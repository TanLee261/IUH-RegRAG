"""DOCX extraction that preserves paragraphs and tables in document order."""

from pathlib import Path

from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph

from .cleaner import clean_text, normalize_line
from .models import ParsedPage


def _table_text(table: Table) -> str:
    rows = []
    for row in table.rows:
        cells = [normalize_line(cell.text.replace('\n', ' ')) for cell in row.cells]
        if any(cells):
            rows.append(' | '.join(cells))
    return '\n'.join(rows)


def parse_docx(path: Path) -> tuple[list[ParsedPage], str, bool, list[str]]:
    document = Document(path)
    blocks = []
    for block in document.iter_inner_content():
        if isinstance(block, Paragraph):
            text = normalize_line(block.text)
        elif isinstance(block, Table):
            text = _table_text(block)
        else:
            text = ''
        if text:
            blocks.append(text)
    text = clean_text('\n\n'.join(blocks))
    warnings = [] if text else ['No text could be extracted']
    return [ParsedPage(1, text)], 'docx', not bool(text), warnings
