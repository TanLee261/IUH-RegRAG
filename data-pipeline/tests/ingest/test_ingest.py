import argparse
import hashlib
import json
from pathlib import Path
from types import SimpleNamespace

import numpy as np

from ingest.ingest import run


class FakeEmbedder:
    config = SimpleNamespace(
        model_name='fake-model', dimension=4, batch_size=2, max_tokens=128
    )

    def token_lengths(self, texts):
        return [len(text.split()) for text in texts]

    def embed_documents(self, texts):
        vectors = np.zeros((len(texts), 4), dtype=np.float32)
        vectors[:, 0] = 1.0
        return vectors


class FakeStore:
    def __init__(self):
        self.rows = []

    def begin_run(self, model_name, dimension):
        return 'run-e2e'

    def upsert_batch(self, rows, run_id):
        self.rows.extend(rows)

    def finalize_run(self, run_id, chunk_count):
        return {'previous_chunks': 0, 'stored_chunks': chunk_count, 'deleted_chunks': 0}

    def abort_run(self):
        raise AssertionError('ingest should not abort')


def test_end_to_end_ingest_with_fake_embedding_and_store(tmp_path: Path):
    data_dir = tmp_path / 'data'
    source = data_dir / 'raw' / 'sample.txt'
    source.parent.mkdir(parents=True)
    source.write_text(
        'Điều 1. Phạm vi áp dụng\n1. Sinh viên thực hiện quy định.',
        encoding='utf-8',
    )
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    manifest = data_dir / 'manifest.json'
    manifest.write_text(json.dumps([{
        'status': 'downloaded', 'path': 'raw/sample.txt', 'sha256': digest,
        'title': 'Quy chế thử nghiệm', 'group': 'test',
        'source': 'https://example.test/sample.txt',
    }]), encoding='utf-8')
    validation = data_dir / 'validation.json'
    validation.write_text('{"files": []}', encoding='utf-8')
    store = FakeStore()
    args = argparse.Namespace(
        data_dir=data_dir,
        manifest=manifest,
        validation=validation,
        documents=data_dir / 'processed' / 'documents',
        chunks=data_dir / 'processed' / 'chunks' / 'chunks.jsonl',
        ocr_dir=data_dir / 'ocr',
        parse_report=data_dir / 'reports' / 'parse-report.json',
        chunk_report=data_dir / 'reports' / 'chunk-report.json',
        report=data_dir / 'reports' / 'ingest-report.json',
        database_url=None,
        ocr='never',
        max_chars=1800,
        force_parse=False,
    )

    report = run(args, embedder=FakeEmbedder(), store=store)

    assert report['status'] == 'succeeded'
    assert report['chunk']['chunks'] == 1
    assert report['embedding_storage']['embedded'] == 1
    assert len(store.rows) == 1
    assert store.rows[0].chunk['dieu'] == '1'
    assert store.rows[0].chunk['khoan'] == '1'
