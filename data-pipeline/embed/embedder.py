"""Sentence Transformers adapter with strict vector validation."""

from typing import Any, Sequence

import numpy as np

from .config import EmbeddingConfig


def resolve_device(requested: str) -> str:
    if requested != 'auto':
        return requested
    import torch

    if torch.cuda.is_available():
        return 'cuda'
    if torch.backends.mps.is_available():
        return 'mps'
    return 'cpu'


class Embedder:
    def __init__(
        self,
        config: EmbeddingConfig | None = None,
        *,
        model: Any | None = None,
    ) -> None:
        self.config = config or EmbeddingConfig.from_env()
        self.device = resolve_device(self.config.device) if model is None else self.config.device
        if model is None:
            from sentence_transformers import SentenceTransformer

            model = SentenceTransformer(self.config.model_name, device=self.device)
        self.model = model
        self.model.max_seq_length = self.config.max_tokens

    def token_lengths(self, texts: Sequence[str]) -> list[int]:
        encoded = self.model.tokenizer(
            list(texts),
            add_special_tokens=True,
            padding=False,
            truncation=False,
            return_length=True,
        )
        lengths = encoded.get('length')
        if lengths is not None:
            return [int(length) for length in lengths]
        return [len(tokens) for tokens in encoded['input_ids']]

    def embed_documents(self, texts: Sequence[str]) -> np.ndarray:
        if not texts:
            return np.empty((0, self.config.dimension), dtype=np.float32)
        if any(not text.strip() for text in texts):
            raise ValueError('embedding input must not be empty')
        vectors = self.model.encode(
            list(texts),
            batch_size=self.config.batch_size,
            convert_to_numpy=True,
            normalize_embeddings=self.config.normalize_embeddings,
            show_progress_bar=False,
        )
        return self._validate(vectors, len(texts))

    def embed_query(self, query: str) -> np.ndarray:
        if not query.strip():
            raise ValueError('query must not be empty')
        return self.embed_documents([query])[0]

    def _validate(self, vectors: Any, expected_rows: int) -> np.ndarray:
        array = np.asarray(vectors, dtype=np.float32)
        expected_shape = (expected_rows, self.config.dimension)
        if array.shape != expected_shape:
            raise ValueError(f'expected embedding shape {expected_shape}, got {array.shape}')
        if not np.isfinite(array).all():
            raise ValueError('embeddings contain NaN or Infinity')
        if self.config.normalize_embeddings:
            norms = np.linalg.norm(array, axis=1)
            if not np.allclose(norms, 1.0, atol=1e-4):
                raise ValueError('embeddings are not L2-normalized')
        return array
