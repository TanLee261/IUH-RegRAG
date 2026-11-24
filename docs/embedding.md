# Embedding

IUH-RegRAG dung `BAAI/bge-m3` local de tao dense embedding cho van ban tieng
Viet. Model tao vector 1024 chieu, ho tro context toi da 8192 token va khong can
API key.

## Cau hinh

| Bien | Mac dinh | Mo ta |
|---|---|---|
| `EMBEDDING_MODEL` | `BAAI/bge-m3` | Hugging Face model ID |
| `EMBEDDING_DIMENSION` | `1024` | So chieu vector, phai khop pgvector |
| `EMBEDDING_MAX_TOKENS` | `8192` | Gioi han token cua model |
| `EMBEDDING_BATCH_SIZE` | `4` | So input moi batch, da kiem chung tren Apple MPS |
| `EMBEDDING_DEVICE` | `auto` | `auto`, `mps`, `cuda` hoac `cpu` |

`auto` uu tien CUDA, sau do Apple MPS va cuoi cung la CPU. Model duoc tai vao
Hugging Face cache cua user, khong luu trong repository.

## Embedding input

`data-pipeline/embed/input_builder.py` tao input tu metadata citation va text:

```text
Tai lieu: Quy che cong tac sinh vien 2023
Dieu 19: Trach nhiem cua Phong Dao tao
Khoan 2
2. Tiep nhan va xu ly cac loai don tu...
```

Canonical `chunk.text` khong bi thay doi. BGE-M3 khong dung prefix `query:` va
`passage:` cua multilingual-e5.

## Chay

Tu thu muc `data-pipeline/`, smoke test 10 chunk:

```bash
.venv/bin/python -m embed.embed_chunks --limit 10
```

Kiem tra toan bo corpus:

```bash
.venv/bin/python -m embed.embed_chunks
```

Bao cao duoc ghi tai `data/reports/embedding-report.json`. CLI nay chi xac minh
model va corpus; task Vector DB ke tiep se luu vector vao cot `vector(1024)`.
