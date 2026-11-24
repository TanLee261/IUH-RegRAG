import pytest

from retrieval import LegalReference, understand_query


def test_normalizes_unicode_whitespace_and_safe_abbreviations():
    query = understand_query('  SV\t hỏi  PĐT về CTĐT và ĐKHP  ')

    assert query.semantic_query == (
        'sinh viên hỏi phòng đào tạo về chương trình đào tạo và đăng ký học phần'
    )
    assert query.lexical_query == (
        'sinh vien hoi phong dao tao ve chuong trinh dao tao va dang ky hoc phan'
    )


def test_accented_and_unaccented_queries_have_same_lexical_representation():
    accented = understand_query('Điều 19 về trách nhiệm Phòng Đào tạo')
    unaccented = understand_query('dieu 19 ve trach nhiem phong dao tao')

    assert accented.lexical_tokens == unaccented.lexical_tokens


def test_extracts_legal_references_in_written_order_and_preserves_suffixes():
    query = understand_query('Khoản 2 Điều 19a, điểm đ; nhắc lại Điều 19a')

    assert query.legal_references == (
        LegalReference('khoan', '2'),
        LegalReference('dieu', '19a'),
        LegalReference('diem', 'đ'),
    )


def test_extracts_legal_references_from_unaccented_input():
    query = understand_query('dieu so 12 khoan 2 diem a')

    assert query.legal_references == (
        LegalReference('dieu', '12'),
        LegalReference('khoan', '2'),
        LegalReference('diem', 'a'),
    )


def test_does_not_infer_credit_count_as_an_article_or_expand_ambiguous_hp():
    query = understand_query('SV đăng ký 12 tín chỉ HP có được không?')

    assert query.semantic_query == 'sinh viên đăng ký 12 tín chỉ hp có được không?'
    assert query.legal_references == ()
    assert '12' in query.lexical_tokens
    assert 'hp' in query.lexical_tokens


@pytest.mark.parametrize('query', ['', '   ', '\n\t'])
def test_rejects_empty_queries(query):
    with pytest.raises(ValueError, match='query must not be empty'):
        understand_query(query)


def test_rejects_non_string_queries():
    with pytest.raises(TypeError, match='query must be a string'):
        understand_query(None)
