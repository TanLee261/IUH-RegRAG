"""Environment-backed embedding configuration."""

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class EmbeddingConfig:
    model_name: str = 'BAAI/bge-m3'
    dimension: int = 1024
    max_tokens: int = 8192
    batch_size: int = 4
    device: str = 'auto'
    normalize_embeddings: bool = True

    def __post_init__(self) -> None:
        if not self.model_name.strip():
            raise ValueError('model_name must not be empty')
        if self.dimension < 1 or self.max_tokens < 1 or self.batch_size < 1:
            raise ValueError('dimension, max_tokens and batch_size must be positive')
        if self.device not in {'auto', 'cpu', 'mps', 'cuda'}:
            raise ValueError('device must be auto, cpu, mps or cuda')

    @classmethod
    def from_env(cls) -> 'EmbeddingConfig':
        return cls(
            model_name=os.getenv('EMBEDDING_MODEL', cls.model_name),
            dimension=int(os.getenv('EMBEDDING_DIMENSION', cls.dimension)),
            max_tokens=int(os.getenv('EMBEDDING_MAX_TOKENS', cls.max_tokens)),
            batch_size=int(os.getenv('EMBEDDING_BATCH_SIZE', cls.batch_size)),
            device=os.getenv('EMBEDDING_DEVICE', cls.device).lower(),
        )
