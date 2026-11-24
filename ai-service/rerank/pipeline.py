"""Compose candidate retrieval with cross-encoder reranking."""

from typing import Any, Protocol

from .reranker import Reranker


class CandidateRetriever(Protocol):
    def retrieve(
        self,
        question: str,
        *,
        top_k: int = 5,
        candidate_k: int = 30,
    ) -> list[dict[str, Any]]: ...


class RerankedRetriever:
    def __init__(self, retriever: CandidateRetriever, reranker: Reranker) -> None:
        self.retriever = retriever
        self.reranker = reranker

    def retrieve(
        self,
        question: str,
        *,
        top_k: int = 5,
        retrieval_k: int = 20,
        candidate_k: int = 30,
    ) -> list[dict[str, Any]]:
        if top_k < 1:
            raise ValueError('top_k must be positive')
        if retrieval_k < top_k:
            raise ValueError('retrieval_k must be greater than or equal to top_k')
        if candidate_k < retrieval_k:
            raise ValueError('candidate_k must be greater than or equal to retrieval_k')

        candidates = self.retriever.retrieve(
            question,
            top_k=retrieval_k,
            candidate_k=candidate_k,
        )
        return self.reranker.rerank(question, candidates, top_k=top_k)
