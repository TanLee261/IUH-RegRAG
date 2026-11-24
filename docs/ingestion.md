# Ingestion va retrieval

Pipeline Phase 1 chuyen tai lieu IUH thanh chunk co metadata, tao embedding bang
`BAAI/bge-m3` va luu corpus vao PostgreSQL/pgvector. Lenh ingest chay tu dau den
cuoi theo thu tu parser, chunker, embedder va storage.

## Chuan bi

Tao `.env` tai thu muc goc tu `.env.example`, dat cung mat khau cho
`POSTGRES_PASSWORD`, `DB_PASSWORD` va `PIPELINE_DATABASE_URL`. Neu port `5432`
sang `15432`.

Khoi dong database:

```bash
docker compose up -d --wait
```

Tao Python environment:

```bash
cd data-pipeline
python -m venv .venv
.venv/bin/pip install -r requirements.txt
```

Nap bien moi truong trong terminal dang dung:

```bash
set -a
source ../.env
set +a
```

## Chay ingest

Tu `data-pipeline/`, chay:

```bash
.venv/bin/python -m ingest.ingest
```

CLI doc manifest, parse tai lieu, chunk theo Dieu/Khoan, tao vector 1024 chieu
va thay the corpus trong mot transaction. Neu embedding hoac database gap loi,
corpus da commit truoc do van duoc giu nguyen. Chay lai cung input khong tao ban
ghi trung lap.

Tuy chon thuong dung:

```text
--database-url URL   Ghi de PIPELINE_DATABASE_URL
--force-parse        Parse lai ca cac tai lieu da xu ly
--ocr auto|always|never
--max-chars 1800
```

Bao cao ket qua nam tai:

```text
data/reports/parse-report.json
data/reports/chunk-report.json
data/reports/ingest-report.json
```

## PostgreSQL

Migration `infrastructure/postgres/migrations/001_knowledge_base.sql` duoc CLI
tu dong ap dung va theo doi trong `pipeline_schema_migrations`. Corpus nam trong
hai bang `knowledge_documents` va `knowledge_chunks`; moi lan chay duoc ghi tai
`ingest_runs`.

`knowledge_chunks.embedding` dung `vector(1024)` va cosine HNSW index. Cot
`search_vector` co GIN index de ho tro lexical lookup. Metadata citation gom
title, source, Dieu, Khoan, so quyet dinh, nam, tags va legal status.

Kiem tra nhanh sau ingest:

```bash
docker compose exec postgres psql -U iuh_regrag -d iuh_regrag \
  -c "SELECT count(*) FROM knowledge_chunks;"
```

## Hybrid retrieval

Hybrid search tao query embedding, lay vector candidates tu pgvector, xep hang
BM25 tren corpus va hop nhat hai kenh bang reciprocal rank fusion (RRF):

```bash
.venv/bin/python -m storage.hybrid_search \
  "Dieu 12 canh bao hoc tap" --limit 5
```

Moi ket qua cho biet `channels`, `vector_score`, `bm25_score`, `rrf_score` va
metadata trich dan. `--candidate-k` dieu khien so candidate cua moi kenh truoc
khi fusion.

## Kiem thu

```bash
.venv/bin/python -m pytest -q
```

Test bao phu migration/storage contract, transactional ingest, CLI orchestration,
BM25 tokenizer va RRF.
