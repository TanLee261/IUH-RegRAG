import numpy as np

from storage.hybrid import HybridSearcher, tokenize


class Reference:
    def __init__(self, kind, value):
        self.kind = kind
        self.value = value


ROWS = [
    {
        'id': 'a', 'document_id': 'd', 'title': 'Quy chế đào tạo',
        'text': 'Trách nhiệm của Phòng Đào tạo', 'source': 'https://example.test/a',
        'dieu': '19', 'khoan': '2',
    },
    {
        'id': 'b', 'document_id': 'd', 'title': 'Thông báo học phí',
        'text': 'Thời hạn đóng học phí', 'source': 'https://example.test/b',
        'dieu': None, 'khoan': None,
    },
]


class FakeStore:
    def search_corpus(self):
        return ROWS

    def vector_search(self, embedding, limit):
        return [{**ROWS[1], 'score': 0.8}, {**ROWS[0], 'score': 0.7}]


class FakeEmbedder:
    query = None

    def embed_query(self, query):
        self.query = query
        return np.array([1.0, 0.0], dtype=np.float32)


def test_tokenizer_matches_accented_and_unaccented_vietnamese():
    assert tokenize('Điều đào tạo') == tokenize('dieu dao tao')


def test_hybrid_rrf_promotes_exact_legal_locator():
    results = HybridSearcher(FakeStore(), FakeEmbedder()).search(
        'Điều 19 Phòng Đào tạo', limit=2, candidate_k=2
    )

    assert results[0]['id'] == 'a'
    assert set(results[0]['channels']) == {'vector', 'bm25'}
    assert 'bm25_score' in results[0]


def test_separate_channel_inputs_and_legal_metadata_boost():
    embedder = FakeEmbedder()
    results = HybridSearcher(FakeStore(), embedder).search(
        'sinh viên hỏi quy định',
        lexical_tokens=('dieu', '19', 'khoan', '2'),
        legal_references=(Reference('dieu', '19'), Reference('khoan', '2')),
        limit=2,
        candidate_k=2,
    )

    assert embedder.query == 'sinh viên hỏi quy định'
    assert results[0]['id'] == 'a'
    assert results[0]['legal_match'] is True
    assert 'legal' in results[0]['channels']


def test_point_reference_does_not_filter_without_point_metadata():
    results = HybridSearcher(FakeStore(), FakeEmbedder()).search(
        'điểm a',
        legal_references=(Reference('diem', 'a'),),
        limit=2,
        candidate_k=2,
    )

    assert all('legal' not in result['channels'] for result in results)
