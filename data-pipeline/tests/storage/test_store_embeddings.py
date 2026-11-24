import json
from pathlib import Path
from types import SimpleNamespace

import numpy as np
import pytest

from ingest.store_embeddings import embed_and_store


def chunk_payload(index: int) -> dict:
    identifier = f'{index + 1:064x}'
    return {
        'id': identifier,
        'document_id': 'a' * 64,
        'chunk_index': index,
        'part_index': 0,
        'chunk_type': 'clause',
        'text': f'{index + 1}. Nội dung thử nghiệm.',
        'title': 'Quy chế thử nghiệm 2024',
        'source': 'https://example.test/document.pdf',
        'group': 'test',
        'dieu': '1',
        'dieu_title': 'Phạm vi áp dụng',
        'khoan': str(index + 1),
        'diem': None,
        'so_qd': None,
        'year': 2024,
        'page_start': 1,
        'page_end': 1,
        'tags': ['test'],
        'legal_status': 'unverified',
    }


class FakeEmbedder:
    def __init__(self, *, token_length=10):
        self.config = SimpleNamespace(
            model_name='fake-model', dimension=4, batch_size=2, max_tokens=16
        )
        self.token_length = token_length

    def token_lengths(self, texts):
        return [self.token_length] * len(texts)

    def embed_documents(self, texts):
        vectors = np.zeros((len(texts), 4), dtype=np.float32)
        vectors[:, 0] = 1.0
        return vectors


class FakeStore:
    def __init__(self):
        self.batches = []
        self.aborted = False

    def begin_run(self, model_name, dimension):
        assert (model_name, dimension) == ('fake-model', 4)
        return 'run-1'

    def upsert_batch(self, rows, run_id):
        assert run_id == 'run-1'
        self.batches.append(list(rows))

    def finalize_run(self, run_id, chunk_count):
        return {'stored_chunks': chunk_count, 'deleted_chunks': 0}

    def abort_run(self):
        self.aborted = True


def write_chunks(path: Path, count: int):
    path.write_text(
        ''.join(json.dumps(chunk_payload(index), ensure_ascii=False) + '\n' for index in range(count)),
        encoding='utf-8',
    )


def test_streams_bounded_batches_and_finalizes(tmp_path: Path):
    path = tmp_path / 'chunks.jsonl'
    write_chunks(path, 5)
    store = FakeStore()

    report = embed_and_store(path, FakeEmbedder(), store)

    assert [len(batch) for batch in store.batches] == [2, 2, 1]
    assert report['embedded'] == 5
    assert report['batches'] == 3
    assert report['storage']['stored_chunks'] == 5
    assert not store.aborted


def test_aborts_before_storage_when_chunk_exceeds_token_limit(tmp_path: Path):
    path = tmp_path / 'chunks.jsonl'
    write_chunks(path, 1)
    store = FakeStore()

    with pytest.raises(ValueError, match='exceed'):
        embed_and_store(path, FakeEmbedder(token_length=17), store)

    assert store.aborted
    assert not store.batches
