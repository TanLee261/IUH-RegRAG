# Chunk Schema

Schema nay la contract chung giua data pipeline, vector store va AI service. Ban
runtime nam tai `data-pipeline/chunk/models.py`; ban JSON Schema nam tai
`data-pipeline/chunk/schema.json`.

## Nguyen tac

- `text` giu noi dung phap ly, khong chen metadata hay score retrieval vao text.
- `dieu`, `khoan` va `diem` la string de ho tro cac ky hieu nhu `5a`, `2a`, `a`.
- Field khong tim thay phai co gia tri `null`, khong duoc doan metadata tu OCR kem.
- Moi chunk phai truy vet duoc ve `document_id`, URL `source` va khoang trang.
- `id` phai deterministic de ingest co the upsert va chay lai an toan.

## Fields

| Field | Type | Nullable | Mo ta |
|---|---|---:|---|
| `id` | string | Khong | SHA-256 cua identity chunk da chuan hoa |
| `document_id` | string | Khong | SHA-256 cua tai lieu parser dau vao |
| `chunk_index` | integer | Khong | Thu tu chunk trong tai lieu, bat dau tu 0 |
| `part_index` | integer | Khong | Thu tu phan khi mot don vi phap ly phai tach nho |
| `chunk_type` | string | Khong | `article`, `clause`, `preamble`, `appendix`, `free_text` |
| `text` | string | Khong | Noi dung chunk dung lam context |
| `title` | string | Khong | Ten tai lieu |
| `source` | string | Khong | URL HTTP(S) cua nguon goc |
| `group` | string | Khong | Nhom tai lieu trong manifest |
| `dieu` | string | Co | So Dieu, khong kem chu `Dieu` |
| `dieu_title` | string | Co | Tieu de cua Dieu |
| `khoan` | string | Co | So Khoan, khong kem chu `Khoan` |
| `diem` | string | Co | Ky hieu diem, vi du `a` |
| `so_qd` | string | Co | So quyet dinh/thong bao neu da xac minh |
| `year` | integer | Co | Nam bon chu so |
| `page_start` | integer | Co | Trang dau, danh so tu 1 |
| `page_end` | integer | Co | Trang cuoi, danh so tu 1 |
| `tags` | string[] | Khong | Tag ASCII lowercase dang slug, khong trung |
| `legal_status` | string | Khong | Trang thai phap ly tu metadata tai lieu |

Moi field trong bang deu phai xuat hien trong JSON. Nullable co nghia la field nhan
`null`, khong co nghia la duoc bo field.

## Chunk ID

`build_chunk_id()` bam JSON canonical gom:

```text
document_id + chunk_index + chunk_type + dieu + khoan + diem + part_index + normalized_text
```

Unicode duoc chuan hoa NFC va whitespace lien tiep duoc rut gon. Sua noi dung chunk
se tao ID moi; cung input se luon tao cung ID.

## Vi du

```json
{
  "id": "f755a5e6c9dd233dfd772e2778a03dac820c7571cba0195b6b0d248ed370144b",
  "document_id": "c480ae1226c57e7e4f42539b11fc5c9819aa4e81ac0032be2db5f19a733d216b",
  "chunk_index": 18,
  "part_index": 0,
  "chunk_type": "clause",
  "text": "2. Tiếp nhận và xử lý các loại đơn từ, hồ sơ, công văn liên quan đến công tác đào tạo.",
  "title": "Quy chế công tác sinh viên 2023",
  "source": "https://iuh.edu.vn/Resource/Upload2/files/quy-che-cong-tac-sinh-vien.pdf",
  "group": "05-cong-tac-sinh-vien",
  "dieu": "19",
  "dieu_title": "Trách nhiệm của Phòng Đào tạo",
  "khoan": "2",
  "diem": null,
  "so_qd": null,
  "year": 2023,
  "page_start": 9,
  "page_end": 10,
  "tags": ["cong-tac-sinh-vien", "dao-tao"],
  "legal_status": "unverified"
}
```

## Embedding va citation

Embedding input duoc tao luc ingest, khong luu de trung lap trong schema:

```text
Tai lieu: {title}
Dieu {dieu}: {dieu_title}
Khoan {khoan}
{text}
```

`score` va `snippet` la du lieu phat sinh luc retrieval, khong thuoc chunk luu tru.
AI service se map chunk thanh citation `{title, dieu, khoan, source, snippet, score}`.

## Output cua task chunking

Task chunking ke tiep se ghi:

```text
data-pipeline/data/processed/chunks/chunks.jsonl
data-pipeline/data/reports/chunk-report.json
```

Tai lieu co `needs_review: true` khong duoc chunk mac dinh. JSONL giup ingest theo
stream va khong phai nap toan bo corpus vao bo nho.

Chay chunking tu thu muc `data-pipeline/`:

```bash
.venv/bin/python -m chunk.chunk_documents --fail-fast
```

Gioi han mem mac dinh la 1.800 ky tu. Chunk chi vuot gioi han khi mot cau phap ly
hoac mot khoi bang OCR khong co ranh gioi tach an toan; pipeline khong cat cuong buc
giua cau.
