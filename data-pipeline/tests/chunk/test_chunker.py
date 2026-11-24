from chunk import chunk_document


DOCUMENT_ID = 'b' * 64


def make_document(pages, **overrides):
    document = {
        'document_id': DOCUMENT_ID,
        'title': 'Quy chế đào tạo 2024',
        'group': '01-dao-tao',
        'source': 'https://iuh.edu.vn/quy-che-dao-tao.pdf',
        'pages': [
            {'page_number': index, 'text': text}
            for index, text in enumerate(pages, start=1)
        ],
        'metadata': {
            'so_qd': '123/QĐ-ĐHCN',
            'nam': 2024,
            'legal_status': 'active',
        },
        'needs_review': False,
    }
    document.update(overrides)
    return document


def test_chunks_articles_and_clauses_across_page_boundaries():
    document = make_document([
        'QUY CHẾ ĐÀO TẠO\nĐiều 1. Phạm vi áp dụng\n'
        '1. Quy chế này áp dụng cho sinh viên đang học',
        'tại Trường Đại học Công nghiệp Thành phố Hồ Chí Minh.\n'
        '2. Các đơn vị có trách nhiệm thực hiện.\n'
        'Điều 2: Giải thích thuật ngữ\nNội dung giải thích.',
    ])

    chunks = chunk_document(document)

    clauses = [chunk for chunk in chunks if chunk.chunk_type == 'clause']
    assert [(chunk.dieu, chunk.khoan) for chunk in clauses] == [('1', '1'), ('1', '2')]
    assert clauses[0].page_start == 1
    assert clauses[0].page_end == 2
    assert 'đang học\ntại Trường' in clauses[0].text
    assert any(chunk.dieu == '2' and chunk.dieu_title == 'Giải thích thuật ngữ' for chunk in chunks)


def test_does_not_cut_an_oversized_legal_sentence():
    sentence = '1. ' + ('Nội dung pháp lý chưa kết thúc ' * 15) + 'ở đây.'
    document = make_document([f'Điều 3. Nguyên tắc áp dụng\n{sentence}'])

    chunks = chunk_document(document, max_chars=200)

    assert len(chunks) == 1
    assert chunks[0].khoan == '1'
    assert chunks[0].text == sentence
    assert len(chunks[0].text) > 200


def test_fallback_chunks_notice_by_roman_sections():
    document = make_document([
        'THÔNG BÁO\nI. Thời gian đăng ký\nSinh viên đăng ký đúng hạn.\n'
        'II. Hình thức thực hiện\nSinh viên thực hiện trực tuyến.'
    ])

    chunks = chunk_document(document)

    assert [chunk.chunk_type for chunk in chunks] == ['free_text', 'free_text', 'free_text']
    assert all(chunk.dieu is None and chunk.khoan is None for chunk in chunks)
    assert chunks[1].text.startswith('I. Thời gian đăng ký')
    assert chunks[2].text.startswith('II. Hình thức thực hiện')


def test_appendix_rows_are_not_misclassified_as_clauses():
    document = make_document([
        'Điều 1. Điều khoản thi hành\nQuy định có hiệu lực từ ngày ký.\n'
        'PHỤ LỤC 1\n1. Dòng dữ liệu thứ nhất\n2. Dòng dữ liệu thứ hai'
    ])

    chunks = chunk_document(document)

    appendix = [chunk for chunk in chunks if chunk.chunk_type == 'appendix']
    assert appendix
    assert all(chunk.khoan is None for chunk in appendix)


def test_program_table_splits_on_semesters_and_course_rows():
    courses = '\n'.join(
        f'{number} 2520{number:03d} Tên học phần số {number} 3(3,0,6)'
        for number in range(1, 20)
    )
    document = make_document([
        f'CHƯƠNG TRÌNH ĐÀO TẠO\nHọc kỳ 1\nHọc phần bắt buộc\n{courses}'
    ])

    chunks = chunk_document(document, max_chars=300)

    assert len(chunks) > 3
    assert all(chunk.chunk_type == 'free_text' for chunk in chunks)
    assert max(len(chunk.text) for chunk in chunks) <= 300


def test_keeps_decimal_clause_identifier():
    document = make_document([
        'Điều 3. Chỉ tiêu tuyển sinh\n1.5. Số lượng tuyển sinh dự kiến.'
    ])

    chunks = chunk_document(document)

    assert chunks[0].khoan == '1.5'


def test_skips_document_marked_for_review():
    document = make_document(['Điều 1. Nội dung'], needs_review=True)

    assert chunk_document(document) == []
