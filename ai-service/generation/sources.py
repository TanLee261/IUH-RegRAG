"""Structured citation sources returned with grounded answers."""

from dataclasses import asdict, dataclass
import math
from typing import Any, Mapping, Sequence


DEFAULT_SNIPPET_CHARS = 320


@dataclass(frozen=True, slots=True)
class StructuredSource:
    chunk_id: str
    document_id: str | None
    title: str
    dieu: str | None
    khoan: str | None
    source: str | None
    snippet: str
    score: float | None

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


def _snippet(text: Any, max_chars: int) -> str:
    normalized = ' '.join(str(text or '').split())
    if not normalized:
        raise ValueError('source text must not be empty')
    if len(normalized) <= max_chars:
        return normalized
    return f'{normalized[:max_chars - 3].rstrip()}...'


def _score(chunk: Mapping[str, Any]) -> float | None:
    for field in ('rerank_score', 'rrf_score', 'score', 'bm25_score'):
        value = chunk.get(field)
        if value is None:
            continue
        score = float(value)
        if not math.isfinite(score):
            raise ValueError('source score must be finite')
        return score
    return None


def build_structured_sources(
    chunks: Sequence[Mapping[str, Any]],
    cited_source_ids: Sequence[str],
    *,
    max_snippet_chars: int = DEFAULT_SNIPPET_CHARS,
) -> tuple[StructuredSource, ...]:
    if max_snippet_chars < 4:
        raise ValueError('max_snippet_chars must be at least 4')

    chunks_by_id = {
        str(chunk.get('id') or '').strip(): chunk
        for chunk in chunks
        if str(chunk.get('id') or '').strip()
    }
    sources: list[StructuredSource] = []
    for chunk_id in cited_source_ids:
        chunk = chunks_by_id.get(chunk_id)
        if chunk is None:
            raise ValueError(f'cited source chunk not found: {chunk_id}')
        title = str(chunk.get('title') or '').strip()
        if not title:
            raise ValueError('source title must not be empty')
        document_id = str(chunk.get('document_id') or '').strip() or None
        source = str(chunk.get('source') or '').strip() or None
        sources.append(StructuredSource(
            chunk_id=chunk_id,
            document_id=document_id,
            title=title,
            dieu=str(chunk['dieu']) if chunk.get('dieu') not in (None, '') else None,
            khoan=str(chunk['khoan']) if chunk.get('khoan') not in (None, '') else None,
            source=source,
            snippet=_snippet(chunk.get('text'), max_snippet_chars),
            score=_score(chunk),
        ))
    return tuple(sources)
