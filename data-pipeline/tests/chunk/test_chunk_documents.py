import argparse
import json
from pathlib import Path

from chunk.chunk_documents import run


def write_document(path: Path, document_id: str, *, needs_review: bool = False):
    path.write_text(json.dumps({
        'document_id': document_id,
        'title': 'Quy định thử nghiệm 2024',
        'group': 'test',
        'source': 'https://example.test/document.pdf',
        'pages': [{
            'page_number': 1,
            'text': 'Điều 1. Phạm vi áp dụng\n1. Nội dung thử nghiệm.',
        }],
        'metadata': {
            'so_qd': None,
            'nam': 2024,
            'legal_status': 'unverified',
        },
        'needs_review': needs_review,
    }, ensure_ascii=False), encoding='utf-8')


def test_batch_writes_jsonl_and_skips_review_documents(tmp_path: Path):
    input_dir = tmp_path / 'documents'
    input_dir.mkdir()
    write_document(input_dir / 'parsed.json', 'a' * 64)
    write_document(input_dir / 'review.json', 'b' * 64, needs_review=True)
    output = tmp_path / 'chunks' / 'chunks.jsonl'
    report_path = tmp_path / 'reports' / 'chunk-report.json'
    args = argparse.Namespace(
        input=input_dir,
        output=output,
        report=report_path,
        max_chars=1800,
        only_document=None,
        include_needs_review=False,
        fail_fast=True,
    )

    report = run(args)

    rows = [json.loads(line) for line in output.read_text(encoding='utf-8').splitlines()]
    assert report['document_counts'] == {'chunked': 1, 'skipped_review': 1}
    assert report['chunks'] == len(rows) == 1
    assert rows[0]['dieu'] == '1'
    assert rows[0]['khoan'] == '1'
    assert report_path.exists()
