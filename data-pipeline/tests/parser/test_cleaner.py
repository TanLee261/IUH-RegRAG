from parser.cleaner import clean_pages, clean_text, text_quality_is_low


def test_clean_text_preserves_paragraphs_and_vietnamese():
    text = 'Điều 1.  Phạm vi\n\n\n1. Nội dung\u00a0quy định'

    assert clean_text(text) == 'Điều 1. Phạm vi\n\n1. Nội dung quy định'


def test_clean_pages_removes_repeated_headers_and_page_numbers():
    pages = [
        f'TRƯỜNG ĐẠI HỌC CÔNG NGHIỆP\nNội dung trang {number}\n{number}'
        for number in range(1, 6)
    ]

    cleaned = clean_pages(pages)

    assert all('TRƯỜNG ĐẠI HỌC' not in page for page in cleaned)
    assert cleaned[0] == 'Nội dung trang 1'


def test_text_quality_flags_broken_unaccented_vietnamese_font_mapping():
    text = (
        'QUYET DINH cua TRUONG DAI HOC CONG NGHIEP THANH PHO. '
        'Dieu nay quy dinh sinh vien hoc tap theo chuong trinh dao tao. '
    ) * 8

    assert text_quality_is_low(text)
