# Phase 4 - Retrieval

Retrieval nhan cau hoi nguoi dung qua `Retriever` trong
`ai-service/retrieval/retriever.py`, sau do ket hop ba tin hieu:

- Vector similarity: embed `semantic_query` bang BGE-M3 va tim tren pgvector.
- BM25: tim bang `lexical_tokens` da bo dau.
- Legal metadata: boost chunk co `dieu` va `khoan` trung tham chieu user viet ro.

Vector va BM25 duoc hop nhat bang Reciprocal Rank Fusion (RRF). Tin hieu metadata
khong thay the hai kenh retrieval; no chi uu tien chunk co locator phap ly chinh
xac trong tap ket qua. `diem` chua duoc boost metadata vi chunk schema hien tai
khong co cot `diem`.

## API

```python
from retrieval import Retriever

retriever = Retriever(hybrid_searcher)
chunks = retriever.retrieve(
    'SV hỏi Điều 19 Khoản 2 của PĐT',
    top_k=5,
    candidate_k=30,
)
```

`HybridSearcher` duoc khoi tao mot lan de model embedding va BM25 corpus khong bi
nap lai theo tung request. Goi `HybridSearcher.refresh()` sau khi corpus da duoc
ingest lai.

Moi chunk ket qua giu metadata tu PostgreSQL:

- `id`, `document_id`, `text`, `title`, `source`, `dieu`, `khoan`.
- `rrf_score` va cac score kenh co san.
- `channels`: mot hoac nhieu gia tri `vector`, `bm25`, `legal`.
- `legal_match: true` neu metadata khop toan bo tham chieu Dieu/Khoan ho tro.

## Chay local

Khoi dong PostgreSQL va ingest corpus theo `docs/ingestion.md`, sau do tu
`ai-service/` chay:

```bash
set -a
source ../.env
set +a
../data-pipeline/.venv/bin/python retrieval_cli.py \
  "SV hỏi Điều 19 Khoản 2 của PĐT" --limit 5 --candidate-k 30
```

`top_k`/`--limit` phai lon hon 0. `candidate_k` phai lon hon hoac bang `top_k`.
Gia tri mac dinh la 5 ket qua tu 30 candidate moi kenh.

## Kiem thu

```bash
cd ai-service
../data-pipeline/.venv/bin/python -m pytest -q

cd ../data-pipeline
.venv/bin/python -m pytest -q
```

Reranking nhan danh sach candidate tu `Retriever` nhung khong dua cross-encoder
vao lop retrieval nay. Chi tiet xem `docs/reranking.md`.
