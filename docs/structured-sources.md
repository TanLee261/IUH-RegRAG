# Phase 4 - Structured sources

Moi `GenerationResult` tra them `sources`, la citation snapshot co cau truc de
backend luu JSON va frontend hien panel nguon. Chi chunk thuc su duoc citation
trong cau tra loi moi xuat hien trong mang nay.

```json
{
  "answer": "Sinh viên nộp hồ sơ tại Phòng Đào tạo. [Nguồn 1: Quy chế đào tạo, Điều 19, Khoản 2]",
  "status": "answered",
  "sources": [
    {
      "chunk_id": "chunk-19-2",
      "document_id": "document-quy-che",
      "title": "Quy chế đào tạo",
      "dieu": "19",
      "khoan": "2",
      "source": "https://example.test/quy-che",
      "snippet": "Sinh viên phải nộp hồ sơ tại Phòng Đào tạo.",
      "score": 0.91
    }
  ]
}
```

## Contract

| Field | Y nghia |
|---|---|
| `chunk_id` | ID chunk de trace retrieval va log. |
| `document_id` | ID tai lieu trong knowledge base; co the `null`. |
| `title` | Ten van ban tai thoi diem tra loi. |
| `dieu` | So Dieu hoac `null` neu chunk khong co metadata. |
| `khoan` | So Khoan hoac `null`. |
| `source` | URL goc de nguoi dung doi chieu; co the `null`. |
| `snippet` | Noi dung rut gon, normalize whitespace, toi da 320 ky tu. |
| `score` | Uu tien `rerank_score`, sau do RRF/vector/BM25; co the `null`. |

Thu tu `sources` theo thu tu citation xuat hien trong answer. Citation lap lai
khong tao source trung. Neu LLM chi citation mot trong nam context chunks, response
chi tra source do.

`sources` rong trong cac truong hop:

- `status` la `no_context`.
- LLM tra dung thong diep context khong du va khong citation.

Score la snapshot phuc vu debug/evaluation, khong duoc coi la xac suat cau tra loi
dung. Backend co the luu truc tiep cac object nay vao `messages.citations` JSONB;
khong tao foreign key den knowledge chunks de lich su van doc duoc sau re-ingest.

## Kiem thu

```bash
cd ai-service
../data-pipeline/.venv/bin/python -m pytest -q
```

CLI `generate_cli.py` in `sources` cung cac metadata generation khac.
