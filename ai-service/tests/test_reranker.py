import numpy as np
import pytest

from rerank import RerankedRetriever, Reranker, RerankerConfig, passage_from_chunk


CANDIDATES = [
    {
        'id': 'a',
        'title': 'Quy chế đào tạo',
        'dieu': '19',
        'khoan': '2',
        'text': 'Phòng Đào tạo tiếp nhận hồ sơ của sinh viên.',
        'source': 'https://example.test/a',
        'rrf_score': 0.04,
    },
    {
        'id': 'b',
        'title': 'Thông báo học phí',
        'dieu': None,
        'khoan': None,
        'text': 'Sinh viên đóng học phí đúng thời hạn.',
        'source': 'https://example.test/b',
        'rrf_score': 0.06,
    },
]


class FakeModel:
    def __init__(self, scores):
        self.scores = scores
        self.call = None

    def predict(self, sentences, **kwargs):
        self.call = (sentences, kwargs)
        return np.asarray(self.scores, dtype=np.float32)


def make_reranker(model):
    return Reranker(RerankerConfig(batch_size=4), model=model)


def test_config_reads_environment(monkeypatch):
    monkeypatch.setenv('RERANK_MODEL', 'test/reranker')
    monkeypatch.setenv('RERANK_MAX_TOKENS', '512')
    monkeypatch.setenv('RERANK_BATCH_SIZE', '2')
    monkeypatch.setenv('RERANK_DEVICE', 'CPU')
    monkeypatch.setenv('RERANK_MIN_SCORE', '0.15')

    config = RerankerConfig.from_env()

    assert config == RerankerConfig('test/reranker', 512, 2, 'cpu', 0.15)


def test_passage_contains_citation_context():
    assert passage_from_chunk(CANDIDATES[0]) == (
        'Tài liệu: Quy chế đào tạo\n'
        'Điều 19, Khoản 2\n'
        'Phòng Đào tạo tiếp nhận hồ sơ của sinh viên.'
    )


def test_reranker_reorders_candidates_and_preserves_metadata():
    model = FakeModel([0.9, 0.1])

    results = make_reranker(model).rerank('SV hỏi PĐT', CANDIDATES, top_k=1)

    assert results[0]['id'] == 'a'
    assert results[0]['source'] == CANDIDATES[0]['source']
    assert results[0]['rrf_score'] == CANDIDATES[0]['rrf_score']
    assert results[0]['rerank_score'] == pytest.approx(0.9)
    pairs, options = model.call
    assert pairs[0][0] == 'sinh viên hỏi phòng đào tạo'
    assert 'Điều 19, Khoản 2' in pairs[0][1]
    assert options['batch_size'] == 4
    assert options['show_progress_bar'] is False


def test_reranker_handles_empty_candidates_without_model_call():
    model = FakeModel([])

    assert make_reranker(model).rerank('quy chế đào tạo', []) == []
    assert model.call is None


def test_explicit_legal_reference_excludes_mismatched_metadata():
    model = FakeModel([0.8])

    results = make_reranker(model).rerank(
        'Điều 19 Khoản 2 quy định gì?', CANDIDATES, top_k=2
    )

    assert [result['id'] for result in results] == ['a']
    pairs, _ = model.call
    assert len(pairs) == 1


def test_missing_explicit_legal_reference_returns_empty_without_scoring():
    model = FakeModel([])

    results = make_reranker(model).rerank(
        'Điều 999 Khoản 8 quy định gì?', CANDIDATES
    )

    assert results == []
    assert model.call is None


def test_discards_candidates_below_minimum_score():
    model = FakeModel([0.049, 0.01])

    assert make_reranker(model).rerank('cách nấu phở?', CANDIDATES) == []


@pytest.mark.parametrize('scores', [[0.1], [float('nan'), 0.2]])
def test_reranker_rejects_invalid_model_output(scores):
    with pytest.raises(ValueError, match='scores|number'):
        make_reranker(FakeModel(scores)).rerank('quy chế đào tạo', CANDIDATES)


def test_reranker_rejects_chunk_without_text():
    with pytest.raises(ValueError, match='text must not be empty'):
        make_reranker(FakeModel([0.1])).rerank('quy chế', [{'id': 'empty'}])


class FakeRetriever:
    def __init__(self):
        self.call = None

    def retrieve(self, question, **kwargs):
        self.call = (question, kwargs)
        return CANDIDATES


class FakeReranker:
    def __init__(self):
        self.call = None

    def rerank(self, question, candidates, **kwargs):
        self.call = (question, candidates, kwargs)
        return [dict(candidates[0])]


def test_pipeline_retrieves_broad_candidates_then_reranks():
    retriever = FakeRetriever()
    reranker = FakeReranker()

    results = RerankedRetriever(retriever, reranker).retrieve(
        'điều 19', top_k=3, retrieval_k=15, candidate_k=25
    )

    assert results[0]['id'] == 'a'
    assert retriever.call == ('điều 19', {'top_k': 15, 'candidate_k': 25})
    assert reranker.call == ('điều 19', CANDIDATES, {'top_k': 3})


@pytest.mark.parametrize(
    ('top_k', 'retrieval_k', 'candidate_k', 'message'),
    [
        (0, 20, 30, 'top_k must be positive'),
        (5, 4, 30, 'retrieval_k must be greater'),
        (5, 20, 19, 'candidate_k must be greater'),
    ],
)
def test_pipeline_rejects_invalid_limits(top_k, retrieval_k, candidate_k, message):
    with pytest.raises(ValueError, match=message):
        RerankedRetriever(FakeRetriever(), FakeReranker()).retrieve(
            'quy chế',
            top_k=top_k,
            retrieval_k=retrieval_k,
            candidate_k=candidate_k,
        )
