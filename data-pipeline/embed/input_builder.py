"""Build model input without mutating canonical chunk text."""

from typing import Any, Mapping


def build_embedding_input(chunk: Mapping[str, Any]) -> str:
    title = str(chunk.get('title') or '').strip()
    text = str(chunk.get('text') or '').strip()
    if not title:
        raise ValueError('chunk title must not be empty')
    if not text:
        raise ValueError('chunk text must not be empty')

    sections = [f'Tài liệu: {title}']
    dieu = chunk.get('dieu')
    if dieu is not None:
        article = f'Điều {dieu}'
        dieu_title = str(chunk.get('dieu_title') or '').strip()
        if dieu_title:
            article += f': {dieu_title}'
        sections.append(article)
    khoan = chunk.get('khoan')
    if khoan is not None:
        sections.append(f'Khoản {khoan}')
    diem = chunk.get('diem')
    if diem is not None:
        sections.append(f'Điểm {diem}')
    sections.append(text)
    return '\n'.join(sections)
