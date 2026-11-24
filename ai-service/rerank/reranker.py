"""Cross-encoder reranking for retrieved knowledge chunks."""

from typing import Any, Mapping, Protocol, Sequence

import numpy as np

from retrieval import understand_query

from .config import RerankerConfig


class CrossEncoderLike(Protocol):
    def predict(
        self,
        sentences: Sequence[tuple[str, str]],
        *,
        batch_size: int,
        show_progress_bar: bool,
        convert_to_numpy: bool,
    ) -> Any: ...


def resolve_device(requested: str) -> str:
    if requested != 'auto':
        return requested
    import torch

    if torch.cuda.is_available():
        return 'cuda'
    if torch.backends.mps.is_available():
        return 'mps'
    return 'cpu'


def passage_from_chunk(chunk: Mapping[str, Any]) -> str:
    text = str(chunk.get('text') or '').strip()
    if not text:
        raise ValueError('candidate chunk text must not be empty')

    lines: list[str] = []
    title = str(chunk.get('title') or '').strip()
    if title:
        lines.append(f'Tài liệu: {title}')

    locator = []
    if chunk.get('dieu') not in (None, ''):
        locator.append(f"Điều {chunk['dieu']}")
    if chunk.get('khoan') not in (None, ''):
        locator.append(f"Khoản {chunk['khoan']}")
    if locator:
        lines.append(', '.join(locator))
    lines.append(text)
    return '\n'.join(lines)


class Reranker:
    """Score query-passage pairs and retain the most relevant chunks."""

    def __init__(
        self,
        config: RerankerConfig | None = None,
        *,
        model: CrossEncoderLike | None = None,
    ) -> None:
        self.config = config or RerankerConfig.from_env()
        if model is None:
            from sentence_transformers import CrossEncoder

            model = CrossEncoder(
                self.config.model_name,
                max_length=self.config.max_tokens,
                device=resolve_device(self.config.device),
            )
        self.model = model

    def rerank(
        self,
        question: str,
        candidates: Sequence[Mapping[str, Any]],
        *,
        top_k: int = 5,
    ) -> list[dict[str, Any]]:
        if top_k < 1:
            raise ValueError('top_k must be positive')
        understood = understand_query(question)
        if not candidates:
            return []

        metadata_references = [
            reference for reference in understood.legal_references
            if reference.kind in {'dieu', 'khoan'}
        ]
        exact_candidates = [
            candidate for candidate in candidates
            if metadata_references and all(
                str(candidate.get(reference.kind) or '').casefold()
                == reference.value.casefold()
                for reference in metadata_references
            )
        ]
        if metadata_references and not exact_candidates:
            return []
        candidates_to_rank = exact_candidates or list(candidates)
        pairs = [
            (understood.semantic_query, passage_from_chunk(candidate))
            for candidate in candidates_to_rank
        ]
        scores = np.asarray(
            self.model.predict(
                pairs,
                batch_size=self.config.batch_size,
                show_progress_bar=False,
                convert_to_numpy=True,
            ),
            dtype=np.float32,
        ).reshape(-1)
        if scores.size != len(candidates_to_rank):
            raise ValueError('reranker returned an unexpected number of scores')
        if not np.isfinite(scores).all():
            raise ValueError('reranker scores contain NaN or Infinity')

        ranked = [
            {**candidate, 'rerank_score': float(score)}
            for candidate, score in zip(candidates_to_rank, scores, strict=True)
            if float(score) >= self.config.min_score
        ]
        ranked.sort(key=lambda chunk: chunk['rerank_score'], reverse=True)
        return ranked[:top_k]
