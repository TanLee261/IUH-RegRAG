"""Parser selection and normalized document assembly."""

from pathlib import Path
from typing import Any

from .docx_parser import parse_docx
from .html_parser import parse_html
from .models import ParsedDocument
from .pdf_parser import parse_pdf
from .text_parser import parse_text


METADATA_FIELDS = ('so_qd', 'nam', 'effective_date', 'applicability', 'legal_status')


def parse_document(
    record: dict[str, Any],
    data_dir: Path,
    ocr_dir: Path,
    ocr_mode: str = 'auto',
    requires_ocr_review: bool = False,
) -> ParsedDocument:
    relative_path = record['path']
    path = data_dir / relative_path
    suffix = path.suffix.lower()
    document_id = record['sha256']

    if suffix == '.pdf':
        pages, method, needs_review, warnings = parse_pdf(
            path,
            ocr_dir,
            document_id,
            ocr_mode,
            requires_ocr_review,
        )
    elif suffix == '.docx':
        pages, method, needs_review, warnings = parse_docx(path)
    elif suffix in ('.html', '.htm'):
        pages, method, needs_review, warnings = parse_html(path)
    elif suffix == '.txt':
        pages, method, needs_review, warnings = parse_text(path)
    else:
        raise ValueError(f'Unsupported document format: {suffix}')

    metadata = {field: record.get(field) for field in METADATA_FIELDS}
    return ParsedDocument(
        document_id=document_id,
        title=record.get('title') or path.stem,
        group=record.get('group') or '',
        source=record.get('source') or '',
        source_file=relative_path,
        sha256=record['sha256'],
        content_type=record.get('content_type') or suffix.lstrip('.'),
        extraction_method=method,
        pages=pages,
        metadata=metadata,
        needs_review=needs_review,
        warnings=warnings,
    )
