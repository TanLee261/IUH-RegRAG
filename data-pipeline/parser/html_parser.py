"""HTML extraction focused on article content instead of site navigation."""

from pathlib import Path

from bs4 import BeautifulSoup, Tag

from .cleaner import clean_text, normalize_line
from .models import ParsedPage


REMOVE_TAGS = ('script', 'style', 'nav', 'header', 'footer', 'aside', 'form', 'svg', 'noscript')


def _best_content_root(soup: BeautifulSoup) -> Tag:
    for selector in ('article', 'main', '[role="main"]', '.post-content', '.news-detail', '.content-detail'):
        candidates = soup.select(selector)
        if candidates:
            return max(candidates, key=lambda node: len(node.get_text(' ', strip=True)))
    return soup.body or soup


def _structured_text(root: Tag) -> str:
    table_rows = []
    for table in root.find_all('table'):
        rows = []
        for row in table.find_all('tr'):
            cells = [normalize_line(cell.get_text(' ', strip=True)) for cell in row.find_all(['th', 'td'])]
            if any(cells):
                rows.append(' | '.join(cells))
        if rows:
            table_rows.append('\n'.join(rows))
        table.decompose()
    text = root.get_text('\n', strip=True)
    if table_rows:
        text += '\n\n' + '\n\n'.join(table_rows)
    return clean_text(text)


def parse_html(path: Path) -> tuple[list[ParsedPage], str, bool, list[str]]:
    soup = BeautifulSoup(path.read_bytes(), 'lxml')
    for node in soup.find_all(REMOVE_TAGS):
        node.decompose()
    root = _best_content_root(soup)
    text = _structured_text(root)
    warnings = [] if text else ['No article text could be extracted']
    return [ParsedPage(1, text)], 'html', not bool(text), warnings
