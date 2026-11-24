# Phase 4 - Reranking

Reranking nhan candidate tu hybrid retrieval va dung cross-encoder de cham truc
tiep tung cap `(cau hoi, chunk)`. Cau hinh mac dinh dung model da ngon ngu
`BAAI/bge-reranker-v2-m3`, phu hop truy van tieng Viet.

## Pipeline

```text
question
  -> query understanding
  -> hybrid retrieval (30 candidates, giu 20)
  -> cross-encoder reranking
  -> 5 chunks cho grounded prompt
```

`RerankedRetriever` trong `ai-service/rerank/pipeline.py` dieu phoi hai buoc cuoi.
Model embedding, BM25 index va reranker phai duoc khoi tao mot lan khi AI service
startup, khong nap lai theo request.

```python
from rerank import RerankedRetriever, Reranker

pipeline = RerankedRetriever(retriever, Reranker())
chunks = pipeline.retrieve(
    'SV hỏi Điều 19 Khoản 2 của PĐT',
    top_k=5,
    retrieval_k=20,
    candidate_k=30,
)
```

Moi passage gui vao model gom:

```text
Tai lieu: {title}
Dieu {dieu}, Khoan {khoan}
{text}
```

Ket qua giu nguyen metadata va score cua retrieval, dong thoi them
`rerank_score`. Danh sach duoc sap xep giam dan theo score nay. Candidate rong
tra ve danh sach rong; xu ly thong diep empty retrieval thuoc task rieng trong
Phase 4.

Neu cau hoi ghi ro `Dieu`/`Khoan` va co candidate khop metadata, reranker chi
cham tap khop chinh xac nay. Quy tac nay ngan chunk cua mot dieu khac lot vao
context chi vi noi dung cua no co nhac den so dieu user dang hoi. Neu khong co
candidate metadata khop, reranker tra danh sach rong de pipeline xu ly nhu empty
retrieval.

## Cau hinh

```dotenv
RERANK_MODEL=BAAI/bge-reranker-v2-m3
RERANK_MAX_TOKENS=1024
RERANK_BATCH_SIZE=8
RERANK_DEVICE=auto
RERANK_MIN_SCORE=0.05
```

Model mac dinh co khoang 568 trieu tham so. Lan chay dau se tai trong so tu
Hugging Face va can them dung luong dia cung/RAM. Co the dat `RERANK_DEVICE=cpu`,
`mps` hoac `cuda`; `auto` uu tien CUDA, sau do MPS, cuoi cung CPU.

## Chay local

Tu `ai-service/`, sau khi PostgreSQL da khoi dong va corpus da duoc ingest:

```bash
set -a
source ../.env
set +a
../data-pipeline/.venv/bin/python rerank_cli.py \
  "SV hỏi Điều 19 Khoản 2 của PĐT" \
  --limit 5 --retrieval-k 20 --candidate-k 30
```

Dieu kien tham so:

- `limit > 0`.
- `retrieval_k >= limit`.
- `candidate_k >= retrieval_k`.

## Kiem thu

```bash
cd ai-service
../data-pipeline/.venv/bin/python -m pytest -q
```

Unit test dung fake cross-encoder de chay nhanh va deterministic. Smoke test voi
model that can PostgreSQL de xac minh toan bo retrieval -> reranking truoc khi
dua vao grounded prompt trong `docs/grounded-prompt.md`.
