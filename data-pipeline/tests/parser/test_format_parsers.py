from pathlib import Path

from docx import Document

from parser.docx_parser import parse_docx
from parser.html_parser import parse_html
from parser import pdf_parser
from parser.text_parser import parse_text


def test_html_parser_selects_article_and_removes_navigation(tmp_path: Path):
    path = tmp_path / 'document.html'
    path.write_text(
        '<html><body><nav>Đăng nhập Sinh viên</nav>'
        '<article><h1>QUY CHẾ</h1><p>Điều 23. Điều kiện tốt nghiệp</p></article>'
        '<footer>Liên hệ</footer></body></html>',
        encoding='utf-8',
    )

    pages, method, needs_review, warnings = parse_html(path)

    assert method == 'html'
    assert not needs_review
    assert not warnings
    assert 'Điều 23' in pages[0].text
    assert 'Đăng nhập' not in pages[0].text


def test_docx_parser_keeps_paragraphs_and_tables(tmp_path: Path):
    path = tmp_path / 'document.docx'
    document = Document()
    document.add_paragraph('Mẫu đơn phúc khảo')
    table = document.add_table(rows=1, cols=2)
    table.cell(0, 0).text = 'Hình thức thi'
    table.cell(0, 1).text = 'Tự luận'
    document.save(path)

    pages, method, needs_review, warnings = parse_docx(path)

    assert method == 'docx'
    assert not needs_review
    assert not warnings
    assert 'Mẫu đơn phúc khảo' in pages[0].text
    assert 'Hình thức thi | Tự luận' in pages[0].text


def test_text_parser_reads_utf8_bom(tmp_path: Path):
    path = tmp_path / 'document.txt'
    path.write_text('Điều 1. Phạm vi áp dụng', encoding='utf-8-sig')

    pages, method, needs_review, warnings = parse_text(path)

    assert method == 'txt'
    assert not needs_review
    assert not warnings
    assert pages[0].text == 'Điều 1. Phạm vi áp dụng'


def test_pdf_parser_uses_ocr_when_embedded_text_is_empty(
    tmp_path: Path,
    monkeypatch,
):
    source = tmp_path / 'scan.pdf'
    source.write_bytes(b'%PDF scan fixture')
    ocr_dir = tmp_path / 'ocr'
    ocr_text = 'QUY ĐỊNH ' + ('Nội dung tiếng Việt. ' * 10)

    def fake_extract(path: Path) -> list[str]:
        return [''] if path == source else [ocr_text]

    def fake_ocr(source_path: Path, destination: Path, force: bool) -> Path:
        assert source_path == source
        assert force
        destination.parent.mkdir(parents=True)
        destination.write_bytes(b'%PDF OCR fixture')
        return destination

    monkeypatch.setattr(pdf_parser, '_extract_pages', fake_extract)
    monkeypatch.setattr(pdf_parser, 'run_ocr', fake_ocr)

    pages, method, needs_review, warnings = pdf_parser.parse_pdf(
        source,
        ocr_dir,
        document_id='document-id',
    )

    assert method == 'pdf_ocr'
    assert not needs_review
    assert not warnings
    assert pages[0].text == ocr_text.strip()
