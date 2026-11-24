import pytest

from generation import build_structured_sources


CHUNKS = [
    {
        'id': 'a',
        'document_id': 'doc-a',
        'title': 'Quy chế A',
        'dieu': 19,
        'khoan': 2,
        'source': 'https://example.test/a',
        'text': 'Nội dung   có nhiều\n khoảng trắng và cần được chuẩn hóa.',
        'rrf_score': 0.04,
        'rerank_score': 0.87,
    },
    {
        'id': 'b',
        'title': 'Thông báo B',
        'dieu': None,
        'khoan': None,
        'source': None,
        'text': 'Nội dung B.',
        'score': 0.62,
    },
]


def test_builds_only_cited_sources_in_citation_order():
    sources = build_structured_sources(CHUNKS, ('b', 'a'))

    assert [source.chunk_id for source in sources] == ['b', 'a']
    assert sources[0].document_id is None
    assert sources[0].dieu is None
    assert sources[0].source is None
    assert sources[0].score == pytest.approx(0.62)
    assert sources[1].score == pytest.approx(0.87)
    assert sources[1].snippet == (
        'Nội dung có nhiều khoảng trắng và cần được chuẩn hóa.'
    )


def test_truncates_snippet_without_returning_full_chunk():
    source = build_structured_sources(
        CHUNKS, ('a',), max_snippet_chars=20
    )[0]

    assert len(source.snippet) <= 20
    assert source.snippet.endswith('...')


def test_empty_citations_return_empty_sources():
    assert build_structured_sources(CHUNKS, ()) == ()


def test_rejects_unknown_cited_chunk():
    with pytest.raises(ValueError, match='cited source chunk not found'):
        build_structured_sources(CHUNKS, ('missing',))


@pytest.mark.parametrize('limit', [0, 3])
def test_rejects_too_small_snippet_limit(limit):
    with pytest.raises(ValueError, match='at least 4'):
        build_structured_sources(CHUNKS, ('a',), max_snippet_chars=limit)
