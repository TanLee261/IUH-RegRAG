"""PDF text extraction with an optional local OCR fallback."""

from pathlib import Path

from pypdf import PdfReader

from .cleaner import clean_pages, text_quality_is_low
from .models import ParsedPage
from .ocr import OcrUnavailableError, run_ocr


def _extract_pages(path: Path) -> list[str]:
    reader = PdfReader(path)
    return [(page.extract_text() or '') for page in reader.pages]


def parse_pdf(
    path: Path,
    ocr_dir: Path,
    document_id: str,
    ocr_mode: str = 'auto',
    requires_ocr_review: bool = False,
) -> tuple[list[ParsedPage], str, bool, list[str]]:
    warnings: list[str] = []
    raw_pages = _extract_pages(path)
    extracted_text = '\n'.join(raw_pages)
    should_ocr = ocr_mode == 'always' or (
        ocr_mode == 'auto'
        and (requires_ocr_review or text_quality_is_low(extracted_text))
    )
    method = 'pdf_text'
    needs_review = text_quality_is_low(extracted_text)

    if should_ocr:
        try:
            ocr_path = run_ocr(path, ocr_dir / f'{document_id}.pdf', force=True)
            raw_pages = _extract_pages(ocr_path)
            method = 'pdf_ocr'
            needs_review = text_quality_is_low('\n'.join(raw_pages))
            if needs_review:
                warnings.append('OCR output still has low text quality')
        except OcrUnavailableError as exc:
            needs_review = True
            warnings.append(str(exc))

    cleaned = clean_pages(raw_pages)
    pages = [ParsedPage(index, text) for index, text in enumerate(cleaned, start=1)]
    if not any(page.text for page in pages):
        needs_review = True
        warnings.append('No text could be extracted')
    return pages, method, needs_review, warnings
