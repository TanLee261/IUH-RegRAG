"""Verify downloaded bytes and generate a browsable collection inventory."""
import hashlib
import json
from collections import Counter
from pathlib import Path
from zipfile import ZipFile

from bs4 import BeautifulSoup
from pypdf import PdfReader

BASE = Path(__file__).resolve().parents[1] / 'data'


def main():
    records = json.loads((BASE / 'manifest.json').read_text())
    checks = []
    lines = ['# Danh mục tài liệu gốc IUH', '',
             'Thu thập ngày 19/11/2026. Mỗi dòng là một nguồn/tệp, không phải một văn bản pháp quy độc lập.', '',
             'Đây là kho raw: chưa chunk, OCR, embed hoặc nạp vector DB. Trạng thái tải thành công không chứng minh hiệu lực pháp lý.', '',
             'Metadata nguồn, thời điểm tải, SHA-256 và lỗi tải: [manifest.json](manifest.json). Các trường chưa xác minh để null.', '',
             '| Nhóm | Tài liệu / nguồn | Tệp local | Kiểm tra |',
             '|---|---|---|---|']
    for r in records:
        title = (r.get('title') or r['group']).replace('|', '/')
        check = {'source': r['source'], 'status': r['status']}
        local = '—'
        note = r.get('error', '')
        if r['status'] == 'downloaded':
            path = BASE / r['path']
            content = path.read_bytes()
            assert hashlib.sha256(content).hexdigest() == r['sha256'], path
            assert len(content) == r['bytes'], path
            check.update(path=r['path'], sha256_verified=True)
            local = f"[Mở tệp]({r['path']})"
            if path.suffix == '.pdf':
                reader = PdfReader(path)
                counts = [len((p.extract_text() or '').strip()) for p in reader.pages]
                low = [i + 1 for i, n in enumerate(counts) if n < 80]
                check.update(pages=len(counts), low_text_pages=low,
                             requires_ocr_review=bool(low))
                note = f'{len(counts)} trang; ' + (f'{len(low)} trang ít/không có text, cần kiểm tra OCR' if low else 'có text trích xuất')
            elif path.suffix == '.docx':
                with ZipFile(path) as z:
                    assert 'word/document.xml' in z.namelist()
                note = 'DOCX hợp lệ'
            else:
                soup = BeautifulSoup(content, 'html.parser')
                check['visible_text_characters'] = len(soup.get_text(' ', strip=True))
                note = 'HTML gốc; có thể gồm menu/sidebar'
                if r['group'] == '01-dao-tao':
                    text = soup.get_text(' ', strip=True)
                    assert 'Điều 23' in text and 'CHƯƠNG V' in text
                    check['graduation_section_present'] = True
        checks.append(check)
        lines.append(f"| {r['group']} | [{title}]({r['source']}) | {local} | {note} |")
    counts = Counter(Path(r['path']).suffix for r in records if r['status'] == 'downloaded')
    summary = {'downloaded': sum(counts.values()), 'formats': dict(counts),
               'failed': sum(r['status'] == 'failed' for r in records),
               'pdf_pages': sum(c.get('pages', 0) for c in checks),
               'pdfs_requiring_ocr_review': sum(c.get('requires_ocr_review', False) for c in checks)}
    (BASE / 'validation.json').write_text(json.dumps({'summary': summary, 'files': checks}, ensure_ascii=False, indent=2) + '\n')
    lines[2:2] = [f"Đã tải **{summary['downloaded']} tệp**: " + ', '.join(f'{n} {s}' for s, n in counts.items()) + f". Còn {summary['failed']} URL lỗi.", '']
    (BASE / 'CATALOG.md').write_text('\n'.join(lines) + '\n')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
