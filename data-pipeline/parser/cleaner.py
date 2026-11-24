"""Conservative text cleanup that preserves legal document structure."""

import re
import unicodedata
from collections import Counter


PAGE_NUMBER = re.compile(r'^\s*(?:trang\s+)?\d+(?:\s*/\s*\d+)?\s*$', re.IGNORECASE)
CONTROL_CHARACTERS = re.compile(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]')
SPACES = re.compile(r'[ \t\f\v]+')
MANY_BLANK_LINES = re.compile(r'\n{3,}')
VIETNAMESE_DIACRITICS = frozenset(
    'ăâđêôơưĂÂĐÊÔƠƯ'
    'áàảãạéèẻẽẹíìỉĩịóòỏõọúùủũụýỳỷỹỵ'
    'ấầẩẫậếềểễệốồổỗộớờởỡợứừửữự'
    'ÁÀẢÃẠÉÈẺẼẸÍÌỈĨỊÓÒỎÕỌÚÙỦŨỤÝỲỶỸỴ'
    'ẤẦẨẪẬẾỀỂỄỆỐỒỔỖỘỚỜỞỠỢỨỪỬỮỰ'
)
UNACCENTED_VIETNAMESE = re.compile(
    r'\b(?:dieu|quyet|truong|sinh\s+vien|quy\s+dinh|cong\s+nghiep|'
    r'thanh\s+pho|chuong|phong|dao\s+tao|hoc\s+tap)\b',
    re.IGNORECASE,
)


def normalize_line(line: str) -> str:
    line = unicodedata.normalize('NFC', line)
    line = line.replace('\u00a0', ' ').replace('\u200b', '').replace('\ufeff', '')
    line = line.replace('\u00ad', '')
    line = CONTROL_CHARACTERS.sub('', line)
    return SPACES.sub(' ', line).strip()


def _header_key(line: str) -> str:
    return normalize_line(line).casefold()


def repeated_edge_lines(pages: list[str], edge_size: int = 3) -> set[str]:
    if len(pages) < 3:
        return set()
    counts: Counter[str] = Counter()
    for page in pages:
        lines = [normalize_line(line) for line in page.splitlines() if normalize_line(line)]
        keys = {_header_key(line) for line in lines[:edge_size] + lines[-edge_size:]}
        counts.update(key for key in keys if len(key) >= 4 and not PAGE_NUMBER.match(key))
    threshold = max(3, int(len(pages) * 0.6 + 0.999))
    return {line for line, count in counts.items() if count >= threshold}


def clean_text(text: str, repeated_lines: set[str] | None = None) -> str:
    text = text.replace('\r\n', '\n').replace('\r', '\n')
    lines = []
    for raw_line in text.splitlines():
        line = normalize_line(raw_line)
        if not line:
            if lines and lines[-1] != '':
                lines.append('')
            continue
        if PAGE_NUMBER.match(line):
            continue
        if repeated_lines and _header_key(line) in repeated_lines:
            continue
        lines.append(line)

    # Join words split by a line-ending hyphen while retaining paragraphs and lists.
    joined: list[str] = []
    for line in lines:
        if (joined and joined[-1].endswith('-') and line and line[0].islower()
                and not re.match(r'^\s*[-+*•]\s+', joined[-1])):
            joined[-1] = joined[-1][:-1] + line
        else:
            joined.append(line)
    return MANY_BLANK_LINES.sub('\n\n', '\n'.join(joined)).strip()


def clean_pages(pages: list[str]) -> list[str]:
    repeated = repeated_edge_lines(pages)
    return [clean_text(page, repeated) for page in pages]


def text_quality_is_low(text: str) -> bool:
    compact = ''.join(text.split())
    if len(compact) < 80:
        return True
    replacement_ratio = text.count('\ufffd') / len(compact)
    alpha_count = sum(char.isalpha() for char in compact)
    alpha_ratio = alpha_count / len(compact)
    unaccented_vietnamese = (
        len(compact) >= 500
        and alpha_count > 0
        and sum(char in VIETNAMESE_DIACRITICS for char in compact) / alpha_count < 0.001
        and len(UNACCENTED_VIETNAMESE.findall(text)) >= 10
    )
    return replacement_ratio > 0.01 or alpha_ratio < 0.35 or unaccented_vietnamese
