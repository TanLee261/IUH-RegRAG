"""Collect additional public curricula and source documents; merge the manifest.

Only curated listing pages below are expanded, and only matching article links
and their direct PDF/DOCX attachments are downloaded. No unrestricted crawl.
"""
import concurrent.futures
import hashlib
import json
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from collect_sources import BASE, fetch, official

LISTINGS = [
    'https://sfa.iuh.edu.vn/news.html@314@Chuong-trinh-khung',
    'https://sfa.iuh.edu.vn/news.html@328@Chuong-trinh-khung',
    'https://sfa.iuh.edu.vn/news.html@368@Chuong-trinh-khung',
    'https://sfa.iuh.edu.vn/news.html@334@Chuong-trinh-khung',
    'https://sfa.iuh.edu.vn/news.html@372@Chuong-trinh-khung',
    'https://sfa.iuh.edu.vn/news.html@363@Chuong-trinh-khung',
    'https://sfa.iuh.edu.vn/news.html@384@Chuong-trinh-khung',
    'https://fct.iuh.edu.vn/news.html@354@Chuong-trinh-khung',
]
EXTRA = [
    ('https://fce.iuh.edu.vn/hoc-vu/quy-dinh-v-vhoc-tieng-anh-va-nop-chung-chi-ngoai-ngu-cho-tan-sinh-vien-tu-k20-tro-ve-sau/', '04-ngoai-ngu-tin-hoc', 'Quy định học tiếng Anh và nộp chứng chỉ từ K20 trở về sau', None),
    ('https://fce.iuh.edu.vn/hoc-vu/quy-dinh-ve-viec-hoc-mon-tieng-anh-tai-iuh-tb-so-64-ngay-20-6-2020/', '04-ngoai-ngu-tin-hoc', 'Thông báo 64 về học tiếng Anh năm 2020 - bản lịch sử', None),
    ('https://tuyensinh.iuh.edu.vn/Files_thongBao/IUH-de-an-tuyensinh-2026-dang-web.pdf', '10-hoc-phi', 'Đề án tuyển sinh 2026 - tham khảo học phí dự kiến, không thay bảng mức thu chính thức', None),
    ('https://fme.iuh.edu.vn/wp-content/uploads/2025/11/DHCDT21_TCTA.pdf', '14-chuong-trinh-dao-tao', 'CTĐT Cơ điện tử 2025-2026 TCTA - thử tải lại', None),
]


def discover(url):
    r = requests.get(url, headers={'User-Agent': 'Mozilla/5.0'}, timeout=(10, 25))
    r.raise_for_status()
    soup = BeautifulSoup(r.content, 'html.parser')
    items = [(url, '14-chuong-trinh-dao-tao', 'Danh mục chương trình khung', None)]
    for a in soup.select('a[href]'):
        label = a.get_text(' ', strip=True)
        target = urljoin(url, a['href'])
        if official(target) and 'detail' in target and ('chương trình đào tạo' in label.lower() or 'chương trình khung' in label.lower()):
            items.append((target, '14-chuong-trinh-dao-tao', label, url))
    return items


def main():
    manifest = BASE / 'manifest.json'
    previous_bytes = manifest.read_bytes()
    records = {r['source']: r for r in json.loads(previous_bytes)}
    pending = list(EXTRA)
    discovery = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures = {pool.submit(discover, url): url for url in LISTINGS}
        for future in concurrent.futures.as_completed(futures):
            url = futures[future]
            try:
                items = future.result()
                pending.extend(items)
                discovery.append({'source': url, 'articles_found': len(items) - 1})
            except Exception as exc:
                discovery.append({'source': url, 'error': str(exc)})
    seen = set()
    attempted = []
    for depth in range(2):
        batch = []
        for item in pending:
            if item[0] not in seen:
                seen.add(item[0])
                batch.append(item)
        pending = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            for record, links in pool.map(fetch, batch):
                old = records.get(record['source'])
                if record['status'] == 'downloaded' or old is None or old['status'] != 'downloaded':
                    records[record['source']] = record
                attempted.append(record)
                pending.extend(links)
                print(record['status'], record['group'], record.get('path', record['source']), flush=True)
    archive = BASE / ('manifest-previous-' + hashlib.sha256(previous_bytes).hexdigest()[:12] + '.json')
    if not archive.exists():
        archive.write_bytes(previous_bytes)
    manifest.write_text(json.dumps(list(records.values()), ensure_ascii=False, indent=2) + '\n')
    (BASE / 'supplement-log.json').write_text(json.dumps({'discovery': discovery, 'attempts': attempted}, ensure_ascii=False, indent=2) + '\n')
    print('Supplement downloads:', sum(r['status'] == 'downloaded' for r in attempted))


if __name__ == '__main__':
    main()
