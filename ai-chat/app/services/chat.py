import asyncio
import tempfile
from concurrent.futures import ThreadPoolExecutor
from google import genai
from google.genai import types
from app.core.config import settings
from app.services.cnn import cnn_service
from app.services.rag import rag_service
from app.services.storage import storage_service

client = genai.Client(api_key=settings.gemini_api_key)

# Shared thread pool cho tất cả blocking I/O
_executor = ThreadPoolExecutor(max_workers=10)

SYSTEM_PROMPT = """You are an agricultural expert specializing in durian trees,
with many years of experience in the Mekong Delta and Central Highlands of Vietnam.

Use the reference documents below to answer the user's question.
If the documents don't contain the information, say so directly instead of guessing.
Always respond in Vietnamese, clearly and practically for farmers.

Reference documents:
{context}

Conversation summary so far:
{summary}
"""


class ChatService:

    def _build_messages(
        self,
        question: str,
        context: str,
        summary: str,
        recent: list,
        disease_info: str = "",
    ) -> list:
        # Nhận sẵn summary + recent thay vì tự fetch bên trong
        history_text = ""
        for msg in recent:
            role = "Người dùng" if msg["role"] == "user" else "Chuyên gia"
            history_text += f"{role}: {msg['content']}\n"

        full_question = f"{disease_info}\n{question}" if disease_info else question

        return [
            {
                "role": "user",
                "parts": [SYSTEM_PROMPT.format(context=context, summary=summary)],
            },
            {
                "role": "model",
                "parts": [
                    "Tôi đã đọc tài liệu. Hãy hỏi tôi bất cứ điều gì về cây sầu riêng."
                ],
            },
            {"role": "user", "parts": [f"{history_text}\nNgười dùng: {full_question}"]},
        ]

    def _update_summary(self, user_id: str, conv_id: str, question: str, answer: str):
        messages = storage_service.get_messages(user_id, conv_id)
        if len(messages) % 6 != 0:
            return

        current_summary = storage_service.get_summary(user_id, conv_id)
        prompt = f"""Summarize this conversation about durian disease in 3-4 sentences.
Focus on: diseases detected, treatments discussed, key advice given.
Write in Vietnamese.

Previous summary: {current_summary}

Latest exchange:
User: {question}
Expert: {answer}

New summary:"""
        response = client.models.generate_content(
            model="gemini-2.5-flash", contents=prompt
        )
        storage_service.update_summary(user_id, conv_id, response.text)

    async def chat_async(
        self,
        user_id: str,
        message: str,
        conv_id: str = None,
        image_path: str = None,
    ) -> dict:
        loop = asyncio.get_event_loop()

        # Tạo conversation nếu cần
        if not conv_id:
            conv_id = await loop.run_in_executor(
                _executor, storage_service.create_conversation, user_id
            )

        disease_info = ""
        image_url = None
        disease = None
        confidence = None

        # Xử lý ảnh nếu có
        if image_path:
            image_url, result = await asyncio.gather(
                loop.run_in_executor(
                    _executor, storage_service.upload_image, image_path, user_id
                ),
                loop.run_in_executor(_executor, cnn_service.predict, image_path),
            )
            disease = result["class"]
            confidence = result["confidence"]

            if not result["confident_enough"]:
                low_conf_msg = (
                    f"Ảnh chưa đủ rõ để chẩn đoán "
                    f"(độ tin cậy: {confidence*100:.1f}%). "
                    "Vui lòng chụp lại ảnh rõ hơn."
                )
                await asyncio.gather(
                    loop.run_in_executor(
                        _executor,
                        storage_service.save_message,
                        user_id,
                        conv_id,
                        "user",
                        message,
                        image_url,
                    ),
                    loop.run_in_executor(
                        _executor,
                        storage_service.save_message,
                        user_id,
                        conv_id,
                        "assistant",
                        low_conf_msg,
                    ),
                )
                return {
                    "conv_id": conv_id,
                    "answer": low_conf_msg,
                    "disease": disease,
                    "confidence": confidence,
                }

            disease_info = (
                f"[Image analysis result: {disease} "
                f"with {confidence*100:.1f}% confidence]"
            )

        search_query = f"{disease} durian: {message}" if disease else message

        # ✅ RAG + fetch history chạy SONG SONG
        (context, summary, recent) = await asyncio.gather(
            loop.run_in_executor(_executor, rag_service.get_context, search_query),
            loop.run_in_executor(
                _executor, storage_service.get_summary, user_id, conv_id
            ),
            loop.run_in_executor(
                _executor, storage_service.get_recent_messages, user_id, conv_id, 4
            ),
        )

        messages = self._build_messages(message, context, summary, recent, disease_info)

        # Gemini generate
        response = await loop.run_in_executor(
            _executor,
            lambda: client.models.generate_content(
                model="gemini-2.5-flash",
                contents=[
                    types.Content(
                        role=msg["role"],
                        parts=[types.Part(text=p) for p in msg["parts"]],
                    )
                    for msg in messages
                ],
            ),
        )
        answer = response.text

        # ✅ Save 2 messages SONG SONG
        await asyncio.gather(
            loop.run_in_executor(
                _executor,
                storage_service.save_message,
                user_id,
                conv_id,
                "user",
                message,
                image_url,
            ),
            loop.run_in_executor(
                _executor,
                storage_service.save_message,
                user_id,
                conv_id,
                "assistant",
                answer,
            ),
        )

        # ✅ Update summary FIRE-AND-FORGET — không chặn response
        loop.run_in_executor(
            _executor, self._update_summary, user_id, conv_id, message, answer
        )

        return {
            "conv_id": conv_id,
            "answer": answer,
            "disease": disease,
            "confidence": confidence,
        }


# Singleton
chat_service = ChatService()
