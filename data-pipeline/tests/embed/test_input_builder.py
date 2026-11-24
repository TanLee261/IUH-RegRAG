import pytest

from embed.input_builder import build_embedding_input


def test_builds_citation_aware_embedding_input():
    result = build_embedding_input({
        'title': 'Quy chế công tác sinh viên 2023',
        'dieu': '19',
        'dieu_title': 'Trách nhiệm của Phòng Đào tạo',
        'khoan': '2',
        'diem': None,
        'text': '2. Tiếp nhận và xử lý đơn từ.',
        'source': 'https://iuh.edu.vn/document.pdf',
    })

    assert result == (
        'Tài liệu: Quy chế công tác sinh viên 2023\n'
        'Điều 19: Trách nhiệm của Phòng Đào tạo\n'
        'Khoản 2\n'
        '2. Tiếp nhận và xử lý đơn từ.'
    )
    assert 'https://' not in result


def test_builds_free_text_input_without_empty_locators():
    result = build_embedding_input({
        'title': 'Thông báo học phí',
        'dieu': None,
        'khoan': None,
        'diem': None,
        'text': 'Sinh viên đóng học phí đúng hạn.',
    })

    assert result == 'Tài liệu: Thông báo học phí\nSinh viên đóng học phí đúng hạn.'


@pytest.mark.parametrize('field', ['title', 'text'])
def test_rejects_empty_required_input(field):
    chunk = {'title': 'Tài liệu', 'text': 'Nội dung'}
    chunk[field] = ''

    with pytest.raises(ValueError, match=field):
        build_embedding_input(chunk)
