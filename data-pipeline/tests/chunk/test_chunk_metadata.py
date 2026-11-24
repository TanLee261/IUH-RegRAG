import json
from pathlib import Path
import pytest
from chunk import chunk_document


DOCUMENT_ID = 'c' * 64


def make_document(pages, **overrides):
    document = {
        'document_id': DOCUMENT_ID,
        'title': 'Quy chế học vụ và đào tạo 2024',
        'group': '01-dao-tao',
        'source': 'https://iuh.edu.vn/quy-che-hoc-vu-2024.pdf',
        'pages': [
            {'page_number': index, 'text': text}
            for index, text in enumerate(pages, start=1)
        ],
        'metadata': {
            'so_qd': '987/QĐ-ĐHCN',
            'nam': 2024,
            'legal_status': 'active',
        },
        'needs_review': False,
    }
    document.update(overrides)
    return document


def test_chunking_inherits_all_document_level_metadata():
    document = make_document([
        'QUY CHẾ HỌC VỤ\nĐiều 1. Phạm vi điều chỉnh\n1. Quy định về đào tạo đại học chính quy.',
    ])

    chunks = chunk_document(document)

    # 1 preamble chunk ('QUY CHẾ HỌC VỤ') + 1 clause chunk ('1. Quy định...')
    assert len(chunks) == 2
    preamble = chunks[0]
    assert preamble.chunk_type == 'preamble'
    assert preamble.so_qd == '987/QĐ-ĐHCN'
    assert preamble.year == 2024
    assert preamble.legal_status == 'active'

    clause = chunks[1]
    assert clause.chunk_type == 'clause'
    assert clause.document_id == DOCUMENT_ID
    assert clause.title == 'Quy chế học vụ và đào tạo 2024'
    assert clause.source == 'https://iuh.edu.vn/quy-che-hoc-vu-2024.pdf'
    assert clause.group == '01-dao-tao'
    assert clause.tags == ('01-dao-tao',)
    assert clause.so_qd == '987/QĐ-ĐHCN'
    assert clause.year == 2024
    assert clause.legal_status == 'active'
    assert clause.dieu == '1'
    assert clause.dieu_title == 'Phạm vi điều chỉnh'
    assert clause.khoan == '1'


def test_chunking_extracts_article_with_suffix_and_colon_delimiter():
    document = make_document([
        'Điều 14a: Đăng ký học phần bổ sung\n'
        '1. Sinh viên được đăng ký học phần bổ sung trong tuần đầu tiên của học kỳ.\n'
        '2. Số tín chỉ tối đa không vượt quá 25 tín chỉ.'
    ])

    chunks = chunk_document(document)

    assert len(chunks) == 2
    assert chunks[0].dieu == '14a'
    assert chunks[0].dieu_title == 'Đăng ký học phần bổ sung'
    assert chunks[0].khoan == '1'
    assert chunks[1].dieu == '14a'
    assert chunks[1].khoan == '2'


def test_chunking_resolves_multiline_article_title():
    document = make_document([
        'Điều 25.\n'
        'Điều kiện tốt nghiệp và xét công nhận tốt nghiệp\n'
        '1. Sinh viên được công nhận tốt nghiệp khi tích lũy đủ số tín chỉ.'
    ])

    chunks = chunk_document(document)

    # Emits an article header chunk with title, followed by clause 1
    assert len(chunks) == 2
    header_chunk, clause_chunk = chunks[0], chunks[1]

    assert header_chunk.chunk_type == 'article'
    assert header_chunk.dieu == '25'
    assert header_chunk.dieu_title == 'Điều kiện tốt nghiệp và xét công nhận tốt nghiệp'
    assert header_chunk.khoan is None

    assert clause_chunk.chunk_type == 'clause'
    assert clause_chunk.dieu == '25'
    assert clause_chunk.dieu_title == 'Điều kiện tốt nghiệp và xét công nhận tốt nghiệp'
    assert clause_chunk.khoan == '1'


def test_chunking_falls_back_to_title_year_when_metadata_year_missing():
    document = make_document(
        ['Điều 1. Phạm vi\nNội dung điều 1.'],
        title='Quy định đánh giá rèn luyện sinh viên năm 2023',
        metadata={'so_qd': '456/QĐ-ĐHCN'},
    )

    chunks = chunk_document(document)

    assert len(chunks) == 1
    assert chunks[0].year == 2023


def test_chunking_defaults_legal_status_to_unverified():
    document = make_document(
        ['Điều 2. Đối tượng\nNội dung đối tượng.'],
        metadata={},
    )

    chunks = chunk_document(document)

    assert len(chunks) == 1
    assert chunks[0].legal_status == 'unverified'
    assert chunks[0].so_qd is None


def test_chunking_tracks_accurate_page_ranges():
    document = make_document([
        'Điều 10. Đăng ký học phần\n1. Sinh viên đăng ký trên cổng thông tin học vụ',
        'trong thời gian thông báo của Phòng Đào tạo.\n2. Hủy học phần sau hạn không được hoàn học phí.',
        'Điều 11. Rút học phần\nNội dung rút học phần.',
    ])

    chunks = chunk_document(document)

    clause1 = next(c for c in chunks if c.dieu == '10' and c.khoan == '1')
    clause2 = next(c for c in chunks if c.dieu == '10' and c.khoan == '2')
    article11 = next(c for c in chunks if c.dieu == '11')

    assert (clause1.page_start, clause1.page_end) == (1, 2)
    assert (clause2.page_start, clause2.page_end) == (2, 2)
    assert (article11.page_start, article11.page_end) == (3, 3)


def test_chunking_generates_preamble_and_appendix_metadata():
    document = make_document([
        'BỘ CÔNG THƯƠNG\nTRƯỜNG ĐẠI HỌC CÔNG NGHIỆP TP.HCM\nCăn cứ Luật Giáo dục Đại học;\n'
        'Điều 1. Ban hành quy chế\nNội dung ban hành.\n'
        'PHỤ LỤC 1. BẢNG QUY ĐỔI ĐIỂM CHỮ\nThang điểm A, B, C, D, F.'
    ])

    chunks = chunk_document(document)

    preamble = [c for c in chunks if c.chunk_type == 'preamble']
    appendix = [c for c in chunks if c.chunk_type == 'appendix']
    article = [c for c in chunks if c.chunk_type == 'article']

    assert len(preamble) >= 1
    assert all(c.dieu is None and c.khoan is None for c in preamble)

    assert len(article) == 1
    assert article[0].dieu == '1'

    assert len(appendix) >= 1
    assert all(c.dieu is None and c.khoan is None for c in appendix)


def test_chunking_returns_empty_when_document_needs_review():
    document = make_document(
        ['Điều 1. Nội dung\n1. Khoản 1.'],
        needs_review=True,
    )

    chunks = chunk_document(document)

    assert chunks == []


def test_all_chunks_conform_to_json_schema():
    schema_path = Path(__file__).parents[2] / 'chunk' / 'schema.json'
    schema = json.loads(schema_path.read_text(encoding='utf-8'))
    required_keys = set(schema.get('required', []))

    document = make_document([
        'QUY CHẾ ĐÀO TẠO\n'
        'Điều 1. Nguyên tắc chung\n'
        '1. Đào tạo theo học chế tín chỉ.\n'
        '2. Lấy người học làm trung tâm.\n'
        'PHỤ LỤC: Biểu mẫu đăng ký'
    ])

    chunks = chunk_document(document)
    assert len(chunks) > 0

    for chunk in chunks:
        data = chunk.to_dict()
        missing = required_keys - set(data.keys())
        assert not missing, f"Chunk {chunk.id} missing required schema fields: {missing}"
        assert data['id'] == chunk.id
        assert data['document_id'] == DOCUMENT_ID
        assert data['chunk_type'] in ['article', 'clause', 'preamble', 'appendix', 'free_text']
        assert isinstance(data['tags'], list)
