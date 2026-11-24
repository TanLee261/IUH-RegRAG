"""Normalized parser output models."""

from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass
class ParsedPage:
    page_number: int
    text: str


@dataclass
class ParsedDocument:
    document_id: str
    title: str
    group: str
    source: str
    source_file: str
    sha256: str
    content_type: str
    extraction_method: str
    pages: list[ParsedPage]
    metadata: dict[str, Any]
    needs_review: bool = False
    warnings: list[str] = field(default_factory=list)

    @property
    def text(self) -> str:
        return '\n\n'.join(page.text for page in self.pages if page.text).strip()

    def to_dict(self) -> dict[str, Any]:
        result = asdict(self)
        result['text'] = self.text
        return result
