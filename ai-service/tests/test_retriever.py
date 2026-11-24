import pytest

from retrieval import Retriever


class RecordingSearcher:
    def __init__(self, results=None):
        self.results = results or []
        self.call = None

    def search(self, query, **kwargs):
        self.call = (query, kwargs)
        return self.results


def test_retriever_routes_normalized_inputs_to_correct_channels():
    searcher = RecordingSearcher([{'id': 'chunk-1'}])

    results = Retriever(searcher).retrieve(
        'SV hỏi Điều 19a Khoản 2 của PĐT',
        top_k=3,
        candidate_k=10,
    )

    semantic_query, options = searcher.call
    assert results == [{'id': 'chunk-1'}]
    assert semantic_query == 'sinh viên hỏi điều 19a khoản 2 của phòng đào tạo'
    assert options['lexical_tokens'] == (
        'sinh', 'vien', 'hoi', 'dieu', '19a', 'khoan', '2', 'cua', 'phong', 'dao', 'tao'
    )
    assert [(ref.kind, ref.value) for ref in options['legal_references']] == [
        ('dieu', '19a'), ('khoan', '2')
    ]
    assert options['limit'] == 3
    assert options['candidate_k'] == 10


def test_credit_count_is_not_sent_as_legal_reference():
    searcher = RecordingSearcher()

    Retriever(searcher).retrieve('Đăng ký 12 tín chỉ')

    _, options = searcher.call
    assert options['legal_references'] == ()


@pytest.mark.parametrize(
    ('top_k', 'candidate_k', 'message'),
    [(0, 30, 'top_k must be positive'), (5, 4, 'candidate_k must be greater')],
)
def test_retriever_rejects_invalid_limits(top_k, candidate_k, message):
    with pytest.raises(ValueError, match=message):
        Retriever(RecordingSearcher()).retrieve(
            'quy chế đào tạo', top_k=top_k, candidate_k=candidate_k
        )
