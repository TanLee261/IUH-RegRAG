import argparse
import hashlib
import json
from pathlib import Path

from parser.parse_documents import run


def test_pipeline_writes_document_and_report(tmp_path: Path):
    data_dir = tmp_path / 'data'
    source = data_dir / 'raw' / 'sample.txt'
    source.parent.mkdir(parents=True)
    source.write_text('Điều 1. Nội dung thử nghiệm', encoding='utf-8')
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    manifest = data_dir / 'manifest.json'
    manifest.write_text(json.dumps([{
        'status': 'downloaded',
        'path': 'raw/sample.txt',
        'sha256': digest,
        'title': 'Tài liệu thử nghiệm',
        'group': 'test',
        'source': 'https://example.test/document.txt',
    }]), encoding='utf-8')
    validation = data_dir / 'validation.json'
    validation.write_text('{"files": []}', encoding='utf-8')
    output = data_dir / 'processed' / 'documents'
    report_path = data_dir / 'reports' / 'parse-report.json'
    args = argparse.Namespace(
        data_dir=data_dir,
        manifest=manifest,
        validation=validation,
        output=output,
        ocr_dir=data_dir / 'ocr',
        report=report_path,
        ocr='never',
        only_group=None,
        only_file=None,
        force=False,
        fail_fast=True,
    )

    report = run(args)

    assert report['counts'] == {'parsed': 1}
    parsed = json.loads((output / f'{digest}.json').read_text(encoding='utf-8'))
    assert parsed['document_id'] == digest
    assert parsed['text'] == 'Điều 1. Nội dung thử nghiệm'
    assert report_path.exists()
