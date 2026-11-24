"""Deterministic Vietnamese query normalization before retrieval."""

from dataclasses import dataclass
import re
import unicodedata
from typing import Literal


ReferenceKind = Literal['dieu', 'khoan', 'diem']

WHITESPACE = re.compile(r'\s+', re.UNICODE)
WORD = re.compile(r'\w+', re.UNICODE)

# Expand only unambiguous abbreviations. Ambiguous forms such as "hp" are kept.
ABBREVIATIONS = (
    (re.compile(r'(?<!\w)(?:đtbtl|dtbtl)(?!\w)', re.IGNORECASE), 'điểm trung bình tích lũy'),
    (re.compile(r'(?<!\w)(?:đtbhk|dtbhk)(?!\w)', re.IGNORECASE), 'điểm trung bình học kỳ'),
    (re.compile(r'(?<!\w)(?:ctđt|ctdt)(?!\w)', re.IGNORECASE), 'chương trình đào tạo'),
    (re.compile(r'(?<!\w)đkhp(?!\w)|(?<!\w)dkhp(?!\w)', re.IGNORECASE), 'đăng ký học phần'),
    (re.compile(r'(?<!\w)(?:pđt|pdt)(?!\w)', re.IGNORECASE), 'phòng đào tạo'),
    (re.compile(r'(?<!\w)(?:đtb|dtb)(?!\w)', re.IGNORECASE), 'điểm trung bình'),
    (re.compile(r'(?<!\w)sv(?!\w)', re.IGNORECASE), 'sinh viên'),
)

LEGAL_MARKERS = (
    ('dieu', re.compile(r'(?<!\w)(?:điều|dieu)\s*(?:(?:số|so)\s*)?(\d+[a-zđ]?)(?!\w)', re.IGNORECASE)),
    ('khoan', re.compile(r'(?<!\w)(?:khoản|khoan)\s*(?:(?:số|so)\s*)?(\d+[a-zđ]?)(?!\w)', re.IGNORECASE)),
    ('diem', re.compile(r'(?<!\w)(?:điểm|diem)\s*([a-zđ])(?!\w)', re.IGNORECASE)),
)


@dataclass(frozen=True, slots=True)
class LegalReference:
    """An explicit legal locator written by the user."""

    kind: ReferenceKind
    value: str


@dataclass(frozen=True, slots=True)
class UnderstoodQuery:
    """Stable inputs for semantic and lexical retrieval channels."""

    original_query: str
    semantic_query: str
    lexical_query: str
    lexical_tokens: tuple[str, ...]
    legal_references: tuple[LegalReference, ...]


def _fold_vietnamese(text: str) -> str:
    decomposed = unicodedata.normalize('NFD', text.casefold())
    unaccented = ''.join(char for char in decomposed if not unicodedata.combining(char))
    return unaccented.replace('đ', 'd')


def _normalize_text(query: str) -> str:
    normalized = unicodedata.normalize('NFC', query).casefold()
    normalized = WHITESPACE.sub(' ', normalized).strip()
    for pattern, replacement in ABBREVIATIONS:
        normalized = pattern.sub(replacement, normalized)
    return WHITESPACE.sub(' ', normalized).strip()


def _extract_legal_references(query: str) -> tuple[LegalReference, ...]:
    matches: list[tuple[int, LegalReference]] = []
    for kind, pattern in LEGAL_MARKERS:
        for match in pattern.finditer(query):
            matches.append((match.start(), LegalReference(kind, match.group(1).casefold())))

    seen: set[LegalReference] = set()
    references: list[LegalReference] = []
    for _, reference in sorted(matches, key=lambda item: item[0]):
        if reference not in seen:
            references.append(reference)
            seen.add(reference)
    return tuple(references)


def understand_query(query: str) -> UnderstoodQuery:
    """Normalize one user query without inferring facts that were not written."""

    if not isinstance(query, str):
        raise TypeError('query must be a string')
    semantic_query = _normalize_text(query)
    if not semantic_query:
        raise ValueError('query must not be empty')

    lexical_tokens = tuple(WORD.findall(_fold_vietnamese(semantic_query)))
    return UnderstoodQuery(
        original_query=query,
        semantic_query=semantic_query,
        lexical_query=' '.join(lexical_tokens),
        lexical_tokens=lexical_tokens,
        legal_references=_extract_legal_references(semantic_query),
    )
