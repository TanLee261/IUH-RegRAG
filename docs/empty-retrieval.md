# Phase 4 - Empty retrieval

Pipeline khong goi LLM khi khong co bang chung du tin cay. `GenerationService`
tra truc tiep ket qua:

```json
{
  "answer": "Không tìm thấy trong quy chế đã nạp. Bạn vui lòng liên hệ Phòng Đào tạo để được hướng dẫn và xác nhận.",
  "provider": "none",
  "model": "none",
  "source_ids": [],
  "cited_source_ids": [],
  "sources": [],
  "fallback_used": false,
  "status": "no_context"
}
```

Ket qua co context va da qua generation mang `status: "answered"`.

## Dieu kien empty

Pipeline xem retrieval la empty khi co mot trong cac truong hop:

- Corpus hoac hybrid retrieval khong tra candidate.
- Cau hoi ghi ro Dieu/Khoan nhung khong candidate nao khop toan bo metadata do.
- Tat ca candidate co `rerank_score` thap hon `RERANK_MIN_SCORE`.

Nguong mac dinh la `0.05` cho `BAAI/bge-reranker-v2-m3`. Smoke test ngoai mien
voi cau hoi nau an cho score cao nhat khoang `0.011`, trong khi cau hoi quy che
khop ro rang dat score cao hon nhieu. Nguong nay co the doi qua environment:

```dotenv
RERANK_MIN_SCORE=0.05
```

Can danh gia lai nguong tren bo cau hoi IUH khi corpus/model thay doi. Khong dung
score vector hoac BM25 rieng le lam quyet dinh empty vi cac kenh do luon co the
tra nearest neighbor du cau hoi nam ngoai mien.

## Thu tu xu ly

```text
question
  -> retrieval
  -> reranking + min score / legal metadata checks
  -> chunks rong: tra no_context, khong build prompt, khong goi provider
  -> chunks co du lieu: grounded prompt -> Gemini/Ollama
```

Question van duoc validate ke ca khi chunks rong; query rong khong bi bien thanh
mot cau tra loi `no_context` hop le.

## Kiem thu

```bash
cd ai-service
../data-pipeline/.venv/bin/python -m pytest -q
```

Smoke test ngoai mien:

```bash
../data-pipeline/.venv/bin/python generate_cli.py \
  "Cách nấu phở bò tại nhà như thế nào?"
```

Lenh phai tra `status: "no_context"`, mang source rong va khong can API key LLM
hop le vi provider khong duoc goi.
