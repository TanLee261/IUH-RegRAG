# Phase 4 - Query understanding

`ai-service/retrieval/query_understanding.py` chuan hoa cau hoi truoc retrieval
theo cach deterministic, khong goi LLM va khong suy dien thong tin nguoi dung
khong viet. API chinh:

```python
from retrieval import understand_query

query = understand_query('SV hoi dieu 19a khoan 2 cua PDT')
```

Ket qua `UnderstoodQuery` co cac truong:

| Field | Muc dich |
|---|---|
| `original_query` | Cau nguoi dung gui, giu nguyen de log/audit. |
| `semantic_query` | Unicode NFC, lowercase, rut gon whitespace va mo rong viet tat chac nghia; dung cho embedding. |
| `lexical_query` | Ban khong dau, chi gom token cach nhau bang space; dung cho BM25. |
| `lexical_tokens` | Token immutable tu `lexical_query`. |
| `legal_references` | Danh sach `dieu`, `khoan`, `diem` theo thu tu nguoi dung viet. |

Vi du dau ra:

```json
{
  "original_query": "SV hỏi điều 19a khoản 2 của PĐT",
  "semantic_query": "sinh viên hỏi điều 19a khoản 2 của phòng đào tạo",
  "lexical_query": "sinh vien hoi dieu 19a khoan 2 cua phong dao tao",
  "lexical_tokens": ["sinh", "vien", "hoi", "dieu", "19a", "khoan", "2", "cua", "phong", "dao", "tao"],
  "legal_references": [
    {"kind": "dieu", "value": "19a"},
    {"kind": "khoan", "value": "2"}
  ]
}
```

## Quy tac

- Mo rong cac viet tat chac nghia: `SV`, `PDT/PĐT`, `CTDT/CTĐT`, `DKHP/ĐKHP`,
  `DTB/ĐTB`, `DTBHK/ĐTBHK`, `DTBTL/ĐTBTL`.
- Khong mo rong viet tat da nghia nhu `HP`; retrieval van nhan token goc.
- Nhan ca marker co dau va khong dau: `Điều/dieu`, `Khoản/khoan`, `Điểm/diem`.
- Giu suffix phap ly, vi du `Điều 19a`; loai bo tham chieu trung lap nhung giu
  thu tu xuat hien.
- So luong nhu `12 tín chỉ` khong bi suy dien thanh `Điều 12`.
- Query rong bi tu choi bang `ValueError`; input khong phai string bi tu choi
  bang `TypeError`.

`Retriever` dua `semantic_query` vao BGE-M3, dua `lexical_tokens` vao BM25 va
boost candidate bang `legal_references`. Khong dua `lexical_query` khong dau vao
embedding neu da co `semantic_query`. Chi tiet xem `docs/retrieval.md`.

## Chay thu

Tu `ai-service/`:

```bash
python query_cli.py "SV hỏi điều 19a khoản 2 của PĐT"
python -m pytest -q
```

Trong workspace hien tai co the dung virtual environment cua data pipeline:

```bash
../data-pipeline/.venv/bin/python -m pytest -q
```
