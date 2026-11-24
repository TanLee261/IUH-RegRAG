import json
import unicodedata
from pathlib import Path

import pytest

from chunk import Chunk, build_chunk_id


DOCUMENT_ID = 'a' * 64


def make_chunk(**overrides) -> Chunk:
    values = {
        'document_id': DOCUMENT_ID,
        'chunk_index': 0,
        'part_index': 0,
        'chunk_type': 'clause',
        'text': '2. Sinh viên thực hiện đầy đủ quy định.',
        'title': 'Quy chế công tác sinh viên 2023',
        'source': 'https://iuh.edu.vn/quy-che.pdf',
        'group': '05-cong-tac-sinh-vien',
        'dieu': '19',
        'dieu_title': 'Trách nhiệm của Phòng Đào tạo',
        'khoan': '2',
        'diem': None,
        'so_qd': None,
        'year': 2023,
        'page_start': 9,
        'page_end': 10,
        'tags': ('cong-tac-sinh-vien', 'dao-tao'),
        'legal_status': 'unverified',
    }
    values.update(overrides)
    values.setdefault('id', build_chunk_id(
        values['document_id'],
        values['text'],
        chunk_type=values['chunk_type'],
        dieu=values['dieu'],
        khoan=values['khoan'],
        diem=values['diem'],
        part_index=values['part_index'],
    ))
    return Chunk(**values)


def test_chunk_serializes_citation_metadata():
    chunk = make_chunk()

    payload = chunk.to_dict()

    assert payload['dieu'] == '19'
    assert payload['khoan'] == '2'
    assert payload['page_start'] == 9
    assert payload['tags'] == ['cong-tac-sinh-vien', 'dao-tao']


def test_chunk_id_is_stable_for_unicode_and_whitespace_normalization():
    text = 'Điều 19. Trách nhiệm của Phòng Đào tạo'
    decomposed = unicodedata.normalize('NFD', text).replace(' ', '  ')

    first = build_chunk_id(DOCUMENT_ID, text, chunk_type='article', dieu='19')
    second = build_chunk_id(DOCUMENT_ID, decomposed, chunk_type='article', dieu='19')

    assert first == second
    assert first != build_chunk_id(
        DOCUMENT_ID,
        text + '.',
        chunk_type='article',
        dieu='19',
    )
    assert first != build_chunk_id(
        DOCUMENT_ID,
        text,
        chunk_index=1,
        chunk_type='article',
        dieu='19',
    )


@pytest.mark.parametrize(
    ('override', 'message'),
    [
        ({'source': 'file:///tmp/document.pdf'}, 'source'),
        ({'page_start': 10, 'page_end': 9}, 'page range'),
        ({'tags': ('Dao-Tao',)}, 'tags'),
        ({'year': 23}, 'year'),
        ({'dieu': ''}, 'dieu'),
    ],
)
def test_chunk_rejects_invalid_values(override, message):
    with pytest.raises(ValueError, match=message):
        make_chunk(**override)


def test_json_schema_requires_every_contract_field():
    schema_path = Path(__file__).parents[2] / 'chunk' / 'schema.json'
    schema = json.loads(schema_path.read_text(encoding='utf-8'))
    payload = make_chunk().to_dict()

    assert schema['additionalProperties'] is False
    assert set(schema['required']) == set(payload)
