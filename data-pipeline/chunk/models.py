"""Validated, storage-agnostic schema for legal-document chunks."""

import hashlib
import json
import re
import unicodedata
from dataclasses import asdict, dataclass
from typing import Any
from urllib.parse import urlparse


SHA256 = re.compile(r'^[0-9a-f]{64}$')
TAG = re.compile(r'^[a-z0-9]+(?:-[a-z0-9]+)*$')
CHUNK_TYPES = frozenset({'article', 'clause', 'preamble', 'appendix', 'free_text'})


def _canonical_text(value: str | None) -> str:
    if value is None:
        return ''
    return ' '.join(unicodedata.normalize('NFC', value).split())


def build_chunk_id(
    document_id: str,
    text: str,
    *,
    chunk_index: int = 0,
    chunk_type: str,
    dieu: str | None = None,
    khoan: str | None = None,
    diem: str | None = None,
    part_index: int = 0,
) -> str:
    """Build a stable content ID without depending on a vector database."""
    identity = {
        'document_id': document_id,
        'chunk_index': chunk_index,
        'chunk_type': chunk_type,
        'dieu': _canonical_text(dieu),
        'khoan': _canonical_text(khoan),
        'diem': _canonical_text(diem),
        'part_index': part_index,
        'text': _canonical_text(text),
    }
    encoded = json.dumps(
        identity,
        ensure_ascii=False,
        sort_keys=True,
        separators=(',', ':'),
    ).encode('utf-8')
    return hashlib.sha256(encoded).hexdigest()


@dataclass(frozen=True)
class Chunk:
    id: str
    document_id: str
    chunk_index: int
    part_index: int
    chunk_type: str
    text: str
    title: str
    source: str
    group: str
    dieu: str | None
    dieu_title: str | None
    khoan: str | None
    diem: str | None
    so_qd: str | None
    year: int | None
    page_start: int | None
    page_end: int | None
    tags: tuple[str, ...]
    legal_status: str

    def __post_init__(self) -> None:
        if not SHA256.fullmatch(self.id):
            raise ValueError('id must be a lowercase SHA-256 hex digest')
        if not SHA256.fullmatch(self.document_id):
            raise ValueError('document_id must be a lowercase SHA-256 hex digest')
        if self.chunk_index < 0 or self.part_index < 0:
            raise ValueError('chunk_index and part_index must be non-negative')
        if self.chunk_type not in CHUNK_TYPES:
            raise ValueError(f'unsupported chunk_type: {self.chunk_type}')
        for name in ('text', 'title', 'source', 'group', 'legal_status'):
            if not getattr(self, name).strip():
                raise ValueError(f'{name} must not be empty')
        source = urlparse(self.source)
        if source.scheme not in {'http', 'https'} or not source.netloc:
            raise ValueError('source must be an HTTP(S) URL')
        for name in ('dieu', 'dieu_title', 'khoan', 'diem', 'so_qd'):
            value = getattr(self, name)
            if value is not None and not value.strip():
                raise ValueError(f'{name} must be null or non-empty')
        if self.year is not None and not 1000 <= self.year <= 9999:
            raise ValueError('year must be a four-digit integer or null')
        if (self.page_start is None) != (self.page_end is None):
            raise ValueError('page_start and page_end must both be null or integers')
        if self.page_start is not None:
            if self.page_start < 1 or self.page_end < self.page_start:
                raise ValueError('page range must be positive and ordered')
        if len(set(self.tags)) != len(self.tags):
            raise ValueError('tags must be unique')
        if any(not TAG.fullmatch(tag) for tag in self.tags):
            raise ValueError('tags must be lowercase ASCII slugs')

    def to_dict(self) -> dict[str, Any]:
        result = asdict(self)
        result['tags'] = list(self.tags)
        return result
