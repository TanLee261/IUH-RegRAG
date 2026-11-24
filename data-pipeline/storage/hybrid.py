"""BM25 plus pgvector retrieval using Reciprocal Rank Fusion."""

import re
import unicodedata
from typing import Any, Protocol, Sequence

import numpy as np
from rank_bm25 import BM25Okapi

from embed import Embedder


WORD = re.compile(r'\w+', re.UNICODE)


class LegalReferenceLike(Protocol):
    kind: str
    value: str


def tokenize(text: str) -> list[str]:
    normalized = unicodedata.normalize('NFD', text.casefold())
    unaccented = ''.join(char for char in normalized if not unicodedata.combining(char))
    unaccented = unaccented.replace('đ', 'd')
    return WORD.findall(unaccented)


class HybridSearcher:
    def __init__(self, store, embedder: Embedder) -> None:
        self.store = store
        self.embedder = embedder
        self.rows: list[dict[str, Any]] = []
        self.tokens: list[list[str]] = []
        self.bm25: BM25Okapi | None = None
        self.refresh()

    def refresh(self) -> None:
        self.rows = self.store.search_corpus()
        self.tokens = [
            tokenize(
                f"{row['title']} Điều {row.get('dieu') or ''} "
                f"Khoản {row.get('khoan') or ''} {row['text']}"
            )
            for row in self.rows
        ]
        self.bm25 = BM25Okapi(self.tokens) if self.tokens else None

    def search(
        self,
        query: str,
        *,
        lexical_tokens: Sequence[str] | None = None,
        legal_references: Sequence[LegalReferenceLike] = (),
        limit: int = 5,
        candidate_k: int = 30,
        rrf_k: int = 60,
    ) -> list[dict[str, Any]]:
        if not query.strip():
            raise ValueError('query must not be empty')
        if limit < 1 or candidate_k < limit:
            raise ValueError('candidate_k must be greater than or equal to limit')

        query_vector = self.embedder.embed_query(query)
        vector_rows = self.store.vector_search(query_vector, candidate_k)
        lexical_rows: list[tuple[dict[str, Any], float]] = []
        if self.bm25 is not None:
            query_tokens = list(lexical_tokens) if lexical_tokens is not None else tokenize(query)
            query_terms = set(query_tokens)
            scores = self.bm25.get_scores(query_tokens)
            order = np.argsort(scores)[::-1]
            lexical_rows = [
                (self.rows[index], float(scores[index]))
                for index in order[:candidate_k]
                if query_terms.intersection(self.tokens[index])
            ]

        metadata_references = [
            reference for reference in legal_references
            if reference.kind in {'dieu', 'khoan'}
        ]
        fused: dict[str, dict[str, Any]] = {}
        for rank, row in enumerate(vector_rows, start=1):
            result = fused.setdefault(row['id'], {**row, 'rrf_score': 0.0, 'channels': []})
            result['rrf_score'] += 1.0 / (rrf_k + rank)
            result['vector_score'] = float(row['score'])
            result['channels'].append('vector')
        for rank, (row, score) in enumerate(lexical_rows, start=1):
            result = fused.setdefault(row['id'], {**row, 'rrf_score': 0.0, 'channels': []})
            result['rrf_score'] += 1.2 / (rrf_k + rank)
            result['bm25_score'] = score
            result['channels'].append('bm25')
        for result in fused.values():
            if metadata_references and all(
                str(result.get(reference.kind) or '').casefold() == reference.value.casefold()
                for reference in metadata_references
            ):
                result['rrf_score'] += 2.0 / (rrf_k + 1)
                result['legal_match'] = True
                result['channels'].append('legal')
        return sorted(
            fused.values(),
            key=lambda row: row['rrf_score'],
            reverse=True,
        )[:limit]
