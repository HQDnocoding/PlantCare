import asyncio
import hashlib
import logging
import re
from typing import List, Tuple, Dict
from rank_bm25 import BM25Okapi
from langchain_chroma import Chroma
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_core.documents import Document
from app.core.config import settings

logger = logging.getLogger(__name__)

# ── Retrieval constants ───────────────────────────────────────────
BM25_WEIGHT = 0.4
VECTOR_WEIGHT = 0.6
TOP_K_FETCH = 20
TOP_K_RETURN = 5


class RAGService:
    def __init__(self):
        logger.info("Initializing RAGService...")

        # Dense embedding model
        self.embeddings = HuggingFaceEmbeddings(
            model_name="BAAI/bge-m3",
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True},
        )
        logger.info("Embeddings model loaded")

        # Vector store
        self.vectorstore = Chroma(
            persist_directory=settings.chroma_db_path_2,
            embedding_function=self.embeddings,
            collection_name="nong_nghiep_bge_m3",
        )

        # Load all docs để build BM25
        all_docs = self.vectorstore.get()
        self.all_documents: List[Document] = [
            Document(page_content=text, metadata=meta)
            for text, meta in zip(all_docs["documents"], all_docs["metadatas"])
        ]

        # BM25Okapi với tokenizer tiếng Việt
        corpus_tokens = [
            self._tokenize_vi(doc.page_content) for doc in self.all_documents
        ]
        self.bm25_index = BM25Okapi(corpus_tokens)
        logger.info(f"BM25 index built with {len(self.all_documents)} documents")

        # In-memory cache
        self._cache: Dict[str, List[Document]] = {}

        logger.info("RAGService initialized")

    # ─────────────────────────── Tokenizer ───────────────────────

    def _tokenize_vi(self, text: str) -> List[str]:
        """Tokenize đơn giản cho BM25 – lowercase + bỏ ký tự đặc biệt."""
        text = text.lower()
        text = re.sub(r"[^\w\s]", " ", text)
        return text.split()

    # ─────────────────────────── Cache ───────────────────────────

    def _get_cache_key(self, question: str) -> str:
        normalized = question.lower().strip()
        return hashlib.md5(normalized.encode()).hexdigest()

    # ─────────────────────────── Retrieval ───────────────────────

    def _bm25_retrieve(self, query: str, fetch_n: int) -> List[Tuple[int, float]]:
        """BM25Okapi search, trả về (doc_index, score)."""
        query_tokens = self._tokenize_vi(query)
        scores = self.bm25_index.get_scores(query_tokens)
        ranked = sorted(enumerate(scores), key=lambda x: x[1], reverse=True)
        return ranked[:fetch_n]

    def _vector_retrieve(self, query: str, fetch_n: int) -> List[Tuple[int, float]]:
        """Dense vector search, map về index trong self.all_documents."""
        vec_results = self.vectorstore.similarity_search_with_score(query, k=fetch_n)
        content_to_idx = {
            doc.page_content: i for i, doc in enumerate(self.all_documents)
        }
        ranked = []
        for doc, score in vec_results:
            idx = content_to_idx.get(doc.page_content)
            if idx is not None:
                ranked.append((idx, float(score)))
        return ranked

    def _rrf_fusion(
        self,
        bm25_ranked: List[Tuple[int, float]],
        vector_ranked: List[Tuple[int, float]],
        k: int = 60,
    ) -> List[int]:
        """Reciprocal Rank Fusion với weight riêng cho BM25 và vector."""
        scores: Dict[int, float] = {}
        for rank, (idx, _) in enumerate(bm25_ranked, 1):
            scores[idx] = scores.get(idx, 0) + BM25_WEIGHT * (1 / (k + rank))
        for rank, (idx, _) in enumerate(vector_ranked, 1):
            scores[idx] = scores.get(idx, 0) + VECTOR_WEIGHT * (1 / (k + rank))
        return sorted(scores, key=lambda x: scores[x], reverse=True)

    def _hybrid_retrieve(self, query: str, top_k: int = TOP_K_RETURN) -> List[Document]:
        """Hybrid search: BM25Okapi + Vector → RRF fusion → deduplicate."""
        fetch_n = max(TOP_K_FETCH, top_k * 4)

        bm25_ranked = self._bm25_retrieve(query, fetch_n)
        vector_ranked = self._vector_retrieve(query, fetch_n)
        fused_indices = self._rrf_fusion(bm25_ranked, vector_ranked)

        seen, results = set(), []
        for idx in fused_indices:
            content = self.all_documents[idx].page_content
            if content not in seen:
                seen.add(content)
                results.append(self.all_documents[idx])
            if len(results) >= top_k:
                break
        return results

    # ─────────────────────────── Main pipeline ───────────────────

    async def retrieve_async(
        self, question: str, top_k: int = TOP_K_RETURN
    ) -> List[Document]:
        """Async RAG pipeline: cache → hybrid RRF → trả kết quả."""
        # 1. Check cache
        cache_key = self._get_cache_key(question)
        if cache_key in self._cache:
            logger.info(f"RAG cache hit: {question[:60]}")
            return self._cache[cache_key]

        logger.info(f"RAG cache miss, running pipeline for: {question[:60]}")

        # 2. Hybrid search (offload sang thread pool vì CPU-bound)
        loop = asyncio.get_event_loop()
        results = await loop.run_in_executor(
            None, self._hybrid_retrieve, question, top_k
        )
        logger.info(f"Hybrid retrieve done: {len(results)} docs")

        # 3. Cache
        self._cache[cache_key] = results
        return results

    async def get_context_async(self, question: str) -> str:
        """Retrieve và format context thành string cho prompt."""
        docs = await self.retrieve_async(question)
        return "\n\n---\n\n".join([d.page_content for d in docs])


# Singleton
rag_service = RAGService()
