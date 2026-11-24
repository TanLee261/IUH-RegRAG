"""Legal-document chunk schema shared by chunking and ingestion."""

from .models import Chunk, build_chunk_id
from .chunker import chunk_document

__all__ = ['Chunk', 'build_chunk_id', 'chunk_document']
