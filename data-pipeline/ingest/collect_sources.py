"""Download the curated public IUH sources and their directly linked documents.

Run: python3 data-pipeline/ingest/collect_sources.py
Requires: requests, beautifulsoup4. Does not chunk/embed or assert legal validity.
"""
import concurrent.futures
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlparse, parse_qs, unquote

import requests
from bs4 import BeautifulSoup

BASE = Path(__file__).resolve().parents[1] / 'data'
RAW = BASE / 'raw'
SEEDS = Path(__file__).with_name('sources.json')


def official(url):
    parsed = urlparse(url)
    host = parsed.hostname or ''
    return parsed.scheme in ('http', 'https') and (host == 'iuh.edu.vn' or host.endswith('.iuh.edu.vn'))


def fetch(item):
    url, group, title, parent = item
    result = dict(source=url, group=group, title=title, parent_source=parent,
                  retrieved_at=datetime.now(timezone.utc).isoformat(),
                  so_qd=None, nam=None, effective_date=None,
                  applicability=None, legal_status='unverified')
    try:
        if not official(url):
            raise ValueError('Not an official IUH URL')
        response = requests.get(url, timeout=(10, 35), headers={'User-Agent': 'Mozilla/5.0', 'Accept': 'text/html,application/pdf,*/*'}, allow_redirects=True)
        response.raise_for_status()
        if not official(response.url):
            raise ValueError('Redirect outside official IUH domain')
        content = response.content
        pdf = content.startswith(b'%PDF-')
        docx = content.startswith(b'PK') and '.docx' in unquote(response.url).lower()
        if '.pdf' in urlparse(url).path.lower() and not pdf:
            raise ValueError('Expected PDF but server returned another format')
        html = 'html' in response.headers.get('Content-Type', '').lower()
        if not (pdf or docx or html):
            raise ValueError('Unsupported or unexpected content type')
        soup = None
        if html:
            soup = BeautifulSoup(content, 'html.parser')
            if len(soup.get_text(' ', strip=True)) < 150:
                raise ValueError('HTML page has too little content; possible error page')
            if soup.title:
                result['page_title'] = soup.title.get_text(' ', strip=True)
        suffix = '.pdf' if pdf else '.docx' if docx else '.html'
        key = hashlib.sha256(url.encode()).hexdigest()[:12]
        dest = RAW / group / (key + suffix)
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.exists() and dest.read_bytes() != content:
            # Retain earlier source bytes when the public document changes.
            old = hashlib.sha256(dest.read_bytes()).hexdigest()[:12]
            archive = dest.with_name(key + '-previous-' + old + suffix)
            if not archive.exists():
                archive.write_bytes(dest.read_bytes())
        dest.write_bytes(content)
        result.update(status='downloaded', path=str(dest.relative_to(BASE)),
                      final_url=response.url, bytes=len(content),
                      sha256=hashlib.sha256(content).hexdigest(),
                      content_type=response.headers.get('Content-Type'))
        links = []
        if soup:
            for node in soup.select('[href], iframe[src], embed[src], object[data]'):
                value = node.get('href') or node.get('src') or node.get('data')
                target = urljoin(response.url, value)
                if 'viewer' in target or 'gview' in target:
                    query = parse_qs(urlparse(target).query)
                    target = (query.get('url') or query.get('src') or [target])[0]
                if official(target) and re.search(r'\.(pdf|docx)(?:$|[?#])', target, re.I):
                    links.append((target, group, node.get_text(' ', strip=True) or title, url))
        return result, links
    except Exception as exc:
        result.update(status='failed', error=str(exc))
        return result, []


def main():
    seeds = json.loads(SEEDS.read_text())
    pending = [(s['url'], s['group'], s['title'], None) for s in seeds]
    seen, records = set(), []
    for depth in range(2):
        batch = []
        for item in pending:
            if item[0] not in seen:
                seen.add(item[0])
                batch.append(item)
        pending = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            for record, links in pool.map(fetch, batch):
                records.append(record)
                pending.extend(links)
                print(record['status'], record['group'], record.get('path', record['source']), flush=True)
    BASE.mkdir(parents=True, exist_ok=True)
    manifest = BASE / 'manifest.json'
    if manifest.exists():
        old_hash = hashlib.sha256(manifest.read_bytes()).hexdigest()[:12]
        previous = BASE / ('manifest-previous-' + old_hash + '.json')
        if not previous.exists():
            previous.write_bytes(manifest.read_bytes())
    manifest.write_text(json.dumps(records, ensure_ascii=False, indent=2) + '\n')
    print('Downloaded:', sum(r['status'] == 'downloaded' for r in records), '/', len(records))


if __name__ == '__main__':
    main()
