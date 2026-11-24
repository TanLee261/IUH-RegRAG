"""Local embedding configuration and adapters."""

from .config import EmbeddingConfig
from .embedder import Embedder
from .input_builder import build_embedding_input

__all__ = ['EmbeddingConfig', 'Embedder', 'build_embedding_input']
