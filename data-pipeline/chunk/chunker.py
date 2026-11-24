"""Structure-aware chunking for Vietnamese legal and administrative text."""

import re
from dataclasses import dataclass
from typing import Any, Iterable

from .models import Chunk, build_chunk_id


ARTICLE = re.compile(
    r'^[\s‹«>*#~_\-]{0,4}(?:Điều|Điêu)\s+(\d+[A-Za-zĐđ]?)\s*[.:]\s*(.*)$',
    re.IGNORECASE,
)
CLAUSE = re.compile(r'^\s*(\d{1,3}(?:\.\d{1,3})?)\s*(?:[.)]|[.,]{1,2})\s*(\S.*)$')
APPENDIX = re.compile(
    r'^\s*PH[ỤU]\s*L[ỤU]C(?:\s+(?:[IVXLCDM]+|\d+))?(?:\s+.*)?$',
    re.IGNORECASE,
)
ROMAN_SECTION = re.compile(r'^\s*(?:[IVXLCDM]{1,8}|IL|ILL)[.)]\s+\S', re.IGNORECASE)
PROGRAM_SECTION = re.compile(
    r'^\s*(?:Học\s+kỳ\s+\d+|Học\s+phần\s+(?:bắt\s+buộc|tự\s+chọn))\b',
    re.IGNORECASE,
)
TABLE_ROW = re.compile(r'^\s*\d{1,3}\s+\d{5,}\b')
SENTENCE_END = re.compile(r'[.!?][\]})"”’]*$')
YEAR = re.compile(r'(?<!\d)(19\d{2}|20\d{2}|21\d{2})(?!\d)')


@dataclass(frozen=True)
class SourceLine:
    text: str
    page_number: int


@dataclass(frozen=True)
class Unit:
    lines: list[SourceLine]
    chunk_type: str
    dieu: str | None = None
    dieu_title: str | None = None
    khoan: str | None = None
    diem: str | None = None


def _source_lines(document: dict[str, Any]) -> list[SourceLine]:
    lines: list[SourceLine] = []
    for page in document.get('pages', []):
        page_number = int(page['page_number'])
        for text in page.get('text', '').splitlines():
            lines.append(SourceLine(text.rstrip(), page_number))
    while lines and not lines[-1].text:
        lines.pop()
    return lines


def _trim(lines: Iterable[SourceLine]) -> list[SourceLine]:
    result = list(lines)
    while result and not result[0].text.strip():
        result.pop(0)
    while result and not result[-1].text.strip():
        result.pop()
    return result


def _render(lines: Iterable[SourceLine]) -> str:
    rendered: list[str] = []
    for line in _trim(lines):
        text = line.text.strip()
        if not text:
            if rendered and rendered[-1] != '':
                rendered.append('')
            continue
        rendered.append(text)
    return '\n'.join(rendered).strip()


def _sentence_groups(lines: list[SourceLine]) -> list[list[SourceLine]]:
    groups: list[list[SourceLine]] = []
    current: list[SourceLine] = []
    for line in _trim(lines):
        if not line.text.strip():
            if current:
                groups.append(current)
                current = []
            continue
        if current and TABLE_ROW.match(line.text):
            groups.append(current)
            current = []
        current.append(line)
        if SENTENCE_END.search(line.text.strip()):
            groups.append(current)
            current = []
    if current:
        groups.append(current)
    return groups


def _split_without_breaking_sentence(
    lines: list[SourceLine],
    max_chars: int,
) -> list[list[SourceLine]]:
    groups = _sentence_groups(lines)
    if not groups:
        return []
    parts: list[list[SourceLine]] = []
    current: list[SourceLine] = []
    for group in groups:
        candidate = _render([*current, *group])
        if current and len(candidate) > max_chars:
            parts.append(_trim(current))
            current = list(group)
        else:
            current.extend(group)
    if current:
        parts.append(_trim(current))
    return parts


def _article_title(lines: list[SourceLine], match: re.Match[str]) -> str | None:
    inline = match.group(2).strip()
    if inline:
        return inline
    for line in lines[1:]:
        candidate = line.text.strip()
        if candidate:
            return candidate
    return None


def _article_units(lines: list[SourceLine], match: re.Match[str]) -> list[Unit]:
    dieu = match.group(1)
    title = _article_title(lines, match)
    clause_starts: list[tuple[int, str]] = []
    for index, line in enumerate(lines[1:], start=1):
        clause = CLAUSE.match(line.text)
        if clause and 0 < int(clause.group(1).split('.')[0]) <= 100:
            clause_starts.append((index, clause.group(1)))

    if not clause_starts:
        return [Unit(lines, 'article', dieu=dieu, dieu_title=title)]

    units: list[Unit] = []
    intro = _trim(lines[:clause_starts[0][0]])
    if len(intro) > 1 or (intro and intro[0].text.strip() != lines[0].text.strip()):
        units.append(Unit(intro, 'article', dieu=dieu, dieu_title=title))
    for position, (start, khoan) in enumerate(clause_starts):
        end = clause_starts[position + 1][0] if position + 1 < len(clause_starts) else len(lines)
        units.append(Unit(
            _trim(lines[start:end]),
            'clause',
            dieu=dieu,
            dieu_title=title,
            khoan=khoan,
        ))
    return units


def _fallback_units(lines: list[SourceLine], chunk_type: str) -> list[Unit]:
    starts = [
        index
        for index, line in enumerate(lines)
        if ROMAN_SECTION.match(line.text) or PROGRAM_SECTION.match(line.text)
    ]
    if not starts:
        return [Unit(lines, chunk_type)] if _render(lines) else []
    units: list[Unit] = []
    if _render(lines[:starts[0]]):
        units.append(Unit(_trim(lines[:starts[0]]), chunk_type))
    for position, start in enumerate(starts):
        end = starts[position + 1] if position + 1 < len(starts) else len(lines)
        units.append(Unit(_trim(lines[start:end]), chunk_type))
    return units


def _structural_units(lines: list[SourceLine]) -> list[Unit]:
    boundaries: list[tuple[int, str, re.Match[str] | None]] = []
    for index, line in enumerate(lines):
        article = ARTICLE.match(line.text)
        if article:
            boundaries.append((index, 'article', article))
        elif APPENDIX.match(line.text):
            boundaries.append((index, 'appendix', None))

    if not boundaries:
        return _fallback_units(lines, 'free_text')

    units: list[Unit] = []
    if boundaries[0][0] > 0:
        units.extend(_fallback_units(_trim(lines[:boundaries[0][0]]), 'preamble'))
    for position, (start, kind, match) in enumerate(boundaries):
        end = boundaries[position + 1][0] if position + 1 < len(boundaries) else len(lines)
        region = _trim(lines[start:end])
        if kind == 'article' and match is not None:
            units.extend(_article_units(region, match))
        else:
            units.extend(_fallback_units(region, 'appendix'))
    return units


def _document_year(document: dict[str, Any]) -> int | None:
    value = document.get('metadata', {}).get('nam')
    if isinstance(value, int) and 1000 <= value <= 9999:
        return value
    if isinstance(value, str) and value.isdigit() and len(value) == 4:
        return int(value)
    title_match = YEAR.search(document.get('title', ''))
    return int(title_match.group(1)) if title_match else None


def chunk_document(
    document: dict[str, Any],
    *,
    max_chars: int = 1800,
) -> list[Chunk]:
    """Create citation-ready chunks while preserving semantic boundaries."""
    if max_chars < 200:
        raise ValueError('max_chars must be at least 200')
    if document.get('needs_review'):
        return []

    lines = _source_lines(document)
    units = _structural_units(lines)
    metadata = document.get('metadata', {})
    tags = (document['group'],)
    chunks: list[Chunk] = []
    for unit in units:
        for part_index, part in enumerate(_split_without_breaking_sentence(unit.lines, max_chars)):
            text = _render(part)
            if not text:
                continue
            chunk_index = len(chunks)
            chunk_id = build_chunk_id(
                document['document_id'],
                text,
                chunk_index=chunk_index,
                chunk_type=unit.chunk_type,
                dieu=unit.dieu,
                khoan=unit.khoan,
                diem=unit.diem,
                part_index=part_index,
            )
            pages = [line.page_number for line in part if line.text.strip()]
            chunks.append(Chunk(
                id=chunk_id,
                document_id=document['document_id'],
                chunk_index=chunk_index,
                part_index=part_index,
                chunk_type=unit.chunk_type,
                text=text,
                title=document['title'],
                source=document['source'],
                group=document['group'],
                dieu=unit.dieu,
                dieu_title=unit.dieu_title,
                khoan=unit.khoan,
                diem=unit.diem,
                so_qd=metadata.get('so_qd'),
                year=_document_year(document),
                page_start=min(pages),
                page_end=max(pages),
                tags=tags,
                legal_status=metadata.get('legal_status') or 'unverified',
            ))
    return chunks
