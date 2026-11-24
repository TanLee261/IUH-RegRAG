"""Local OCR integration through OCRmyPDF and Tesseract."""

import shutil
import subprocess
from pathlib import Path


class OcrUnavailableError(RuntimeError):
    pass


def ocr_available() -> bool:
    return all(shutil.which(command) for command in ('ocrmypdf', 'tesseract', 'qpdf'))


def ensure_vietnamese_language() -> None:
    result = subprocess.run(
        ['tesseract', '--list-langs'],
        check=True,
        capture_output=True,
        text=True,
    )
    languages = {line.strip() for line in result.stdout.splitlines()}
    if 'vie' not in languages:
        raise OcrUnavailableError("Tesseract language pack 'vie' is not installed")


def run_ocr(source: Path, destination: Path, force: bool = True) -> Path:
    if not ocr_available():
        raise OcrUnavailableError('OCR requires ocrmypdf, tesseract and qpdf')
    ensure_vietnamese_language()
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        return destination

    temporary = destination.with_suffix('.tmp.pdf')
    command = [
        'ocrmypdf',
        '--language', 'vie+eng',
        '--deskew',
        '--rotate-pages',
        '--output-type', 'pdf',
        '--optimize', '1',
        '--jobs', '2',
    ]
    command.append('--force-ocr' if force else '--skip-text')
    command.extend([str(source), str(temporary)])
    try:
        subprocess.run(command, check=True)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return destination
