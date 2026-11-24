"""Query-aware orchestration for hybrid retrieval."""

from typing import Any, Protocol, Sequence

from .query_understanding import LegalReference, understand_query


class HybridSearch(Protocol):
    def search(
        self,
        query: str,
        *,
        lexical_tokens: Sequence[str] | None = None,
        legal_references: Sequence[LegalReference] = (),
        limit: int = 5,
        candidate_k: int = 30,
        rrf_k: int = 60,
    ) -> list[dict[str, Any]]: ...


class Retriever:
    """Prepare a user question and delegate both retrieval channels."""

    def __init__(self, searcher: HybridSearch) -> None:
        self.searcher = searcher

    def retrieve(
        self,
        question: str,
        *,
        top_k: int = 5,
        candidate_k: int = 30,
    ) -> list[dict[str, Any]]:
        if top_k < 1:
            raise ValueError('top_k must be positive')
        if candidate_k < top_k:
            raise ValueError('candidate_k must be greater than or equal to top_k')

        query = understand_query(question)
        return self.searcher.search(
            query.semantic_query,
            lexical_tokens=query.lexical_tokens,
            legal_references=query.legal_references,
            limit=top_k,
            candidate_k=candidate_k,
        )
