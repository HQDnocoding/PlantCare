from langchain_chroma import Chroma
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_core.documents import Document
from langchain_community.retrievers import BM25Retriever
from sentence_transformers import CrossEncoder
from google import genai
from concurrent.futures import ThreadPoolExecutor
from app.core.config import settings

client = genai.Client(api_key=settings.gemini_api_key)


class RAGService:
    def __init__(self):
        # Load dense embedding model
        self.embeddings = HuggingFaceEmbeddings(
            model_name="BAAI/bge-m3",
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True},
        )

        # Load vector store for dense search
        self.vectorstore = Chroma(
            persist_directory=settings.chroma_db_path_2,
            embedding_function=self.embeddings,
        )

        # Load all docs for BM25 sparse search
        all_docs = self.vectorstore.get()
        self.all_documents = [
            Document(page_content=text, metadata=meta)
            for text, meta in zip(all_docs["documents"], all_docs["metadatas"])
        ]

        self.bm25_retriever = BM25Retriever.from_documents(self.all_documents)
        self.bm25_retriever.k = 5

        # Load reranker model
        self.reranker = CrossEncoder("BAAI/bge-reranker-v2-m3")

    def _generate_queries(self, question: str) -> list[str]:
        """Generate multiple query variants using RAG-Fusion."""
        prompt = f"""You are an agricultural expert specializing in durian trees.
Generate 3 different search queries from the original question below.
Purpose: search for information from multiple angles.

Original question: {question}

Return exactly 3 queries, one per line, no numbering, no explanation."""

        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=prompt,
        )
        queries = [q.strip() for q in response.text.strip().split("\n") if q.strip()]
        return [question] + queries[:3]

    def _reciprocal_rank_fusion(
        self, results_list: list[list[Document]], k: int = 60
    ) -> list[Document]:
        """Merge multiple result lists using RRF scoring."""
        scores: dict[str, float] = {}
        doc_map: dict[str, Document] = {}

        for results in results_list:
            for rank, doc in enumerate(results):
                doc_id = doc.page_content[:100]
                if doc_id not in scores:
                    scores[doc_id] = 0
                doc_map[doc_id] = doc
                scores[doc_id] += 1 / (k + rank + 1)

        sorted_ids = sorted(scores, key=scores.get, reverse=True)
        return [doc_map[doc_id] for doc_id in sorted_ids]

    def _hybrid_retrieve(self, query: str, top_k: int = 5) -> list[Document]:
        """
        Hybrid search: combine dense (BGE-M3) + sparse (BM25).
        Dense catches semantic meaning, BM25 catches exact keywords
        like chemical names and drug names.
        """
        # Dense search
        dense_docs = self.vectorstore.similarity_search(query, k=top_k)

        # Sparse search (BM25)
        sparse_docs = self.bm25_retriever.invoke(query)

        # Merge both using RRF
        fused = self._reciprocal_rank_fusion([dense_docs, sparse_docs])
        return fused[:top_k]

    def _rerank(
        self, query: str, docs: list[Document], top_k: int = 5
    ) -> list[Document]:
        """
        Rerank retrieved docs using cross-encoder.
        More accurate relevance scoring than bi-encoder.
        """
        if not docs:
            return []

        # Score each doc against the query
        pairs = [[query, doc.page_content] for doc in docs]
        scores = self.reranker.predict(pairs)

        # Sort by score descending
        ranked = sorted(zip(scores, docs), key=lambda x: x[0], reverse=True)
        return [doc for _, doc in ranked[:top_k]]

    def retrieve(self, question: str, top_k: int = 5) -> list[Document]:
        """
        Full RAG pipeline:
        RAG-Fusion → Hybrid Search (Dense + BM25) → RRF → Reranker
        """
        queries = self._generate_queries(question)

        # Hybrid search for each query variant - run in parallel
        with ThreadPoolExecutor(max_workers=len(queries)) as executor:
            all_results = list(
                executor.map(lambda q: self._hybrid_retrieve(q, top_k=top_k), queries)
            )

        # Merge all results using RRF
        fused = self._reciprocal_rank_fusion(all_results)

        # Rerank final candidates
        reranked = self._rerank(question, fused, top_k=top_k)
        return reranked

    def get_context(self, question: str) -> str:
        """Retrieve and format context as string for prompt."""
        docs = self.retrieve(question)
        return "\n\n---\n\n".join([d.page_content for d in docs])


# Singleton
rag_service = RAGService()
