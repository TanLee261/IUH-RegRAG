"""Plain text extraction."""

from pathlib import Path

from .cleaner import clean_text
from .models import ParsedPage


def parse_text(path: Path) -> tuple[list[ParsedPage], str, bool, list[str]]:
    warnings = []
    text = ''
    for encoding in ('utf-8-sig', 'utf-8', 'cp1258'):
        try:
            text = path.read_text(encoding=encoding)
            break
        except UnicodeDecodeError:
            continue
    if not text:
        warnings.append('No text could be decoded')
    text = clean_text(text)
    return [ParsedPage(1, text)], 'txt', not bool(text), warnings
