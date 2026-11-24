"""Transactional PostgreSQL/pgvector storage for the knowledge corpus."""

import os
from pathlib import Path
from typing import Any, Sequence
from uuid import uuid4

import psycopg
from pgvector.psycopg import register_vector

from .base import EmbeddedChunk


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MIGRATION = (
    PROJECT_ROOT / 'infrastructure' / 'postgres' / 'migrations' /
    '001_knowledge_base.sql'
)


def database_dsn_from_env() -> str:
    explicit = os.getenv('PIPELINE_DATABASE_URL')
    if explicit:
        return explicit
    password = os.getenv('DB_PASSWORD') or os.getenv('POSTGRES_PASSWORD')
    if not password:
        raise ValueError(
            'PIPELINE_DATABASE_URL or DB_PASSWORD/POSTGRES_PASSWORD is required'
        )
    host = os.getenv('DB_HOST', 'localhost')
    port = os.getenv('POSTGRES_PORT', '5432')
    database = os.getenv('POSTGRES_DB', 'iuh_regrag')
    user = os.getenv('POSTGRES_USER', 'iuh_regrag')
    return f'postgresql://{user}:{password}@{host}:{port}/{database}'


class PostgresChunkStore:
    def __init__(
        self,
        dsn: str,
        *,
        migration_path: Path = DEFAULT_MIGRATION,
    ) -> None:
        self.connection = psycopg.connect(dsn)
        self.connection.execute(migration_path.read_text(encoding='utf-8'))
        self.connection.commit()
        register_vector(self.connection)
        self._previous_chunks = 0
        self._model_name = ''

    def close(self) -> None:
        self.connection.close()

    def __enter__(self) -> 'PostgresChunkStore':
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        if exc_type is not None:
            self.abort_run()
        self.close()

    def begin_run(self, model_name: str, dimension: int) -> str:
        if dimension != 1024:
            raise ValueError('database schema requires 1024-dimensional embeddings')
        run_id = str(uuid4())
        self._model_name = model_name
        self._previous_chunks = self.connection.execute(
            'SELECT count(*) FROM knowledge_chunks'
        ).fetchone()[0]
        self.connection.execute(
            '''
            INSERT INTO ingest_runs (id, status, embedding_model, embedding_dimension)
            VALUES (%s, 'running', %s, %s)
            ''',
            (run_id, model_name, dimension),
        )
        return run_id

    def upsert_batch(self, rows: Sequence[EmbeddedChunk], run_id: str) -> None:
        if not rows:
            return
        documents: dict[str, tuple[Any, ...]] = {}
        chunks: list[tuple[Any, ...]] = []
        for row in rows:
            chunk = row.chunk
            documents[chunk['document_id']] = (
                chunk['document_id'], chunk['title'], chunk['source'], chunk['group'],
                chunk['so_qd'], chunk['year'], chunk['legal_status'],
            )
            chunks.append((
                chunk['id'], chunk['document_id'], chunk['chunk_index'],
                chunk['part_index'], chunk['chunk_type'], chunk['text'],
                chunk['title'], chunk['source'], chunk['group'], chunk['dieu'],
                chunk['dieu_title'], chunk['khoan'], chunk['diem'], chunk['so_qd'],
                chunk['year'], chunk['page_start'], chunk['page_end'], chunk['tags'],
                chunk['legal_status'], row.embedding, self._model_name, run_id,
            ))
        with self.connection.cursor() as cursor:
            cursor.executemany(
                '''
                INSERT INTO knowledge_documents (
                    id, title, source, document_group, so_qd, publication_year,
                    legal_status
                ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (id) DO UPDATE SET
                    title = EXCLUDED.title,
                    source = EXCLUDED.source,
                    document_group = EXCLUDED.document_group,
                    so_qd = EXCLUDED.so_qd,
                    publication_year = EXCLUDED.publication_year,
                    legal_status = EXCLUDED.legal_status,
                    updated_at = now()
                ''',
                list(documents.values()),
            )
            cursor.executemany(
                '''
                INSERT INTO knowledge_chunks (
                    id, document_id, chunk_index, part_index, chunk_type, text,
                    title, source, document_group, dieu, dieu_title, khoan, diem,
                    so_qd, publication_year, page_start, page_end, tags,
                    legal_status, embedding, embedding_model, ingest_run_id
                ) VALUES (
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
                )
                ON CONFLICT (id) DO UPDATE SET
                    document_id = EXCLUDED.document_id,
                    chunk_index = EXCLUDED.chunk_index,
                    part_index = EXCLUDED.part_index,
                    chunk_type = EXCLUDED.chunk_type,
                    text = EXCLUDED.text,
                    title = EXCLUDED.title,
                    source = EXCLUDED.source,
                    document_group = EXCLUDED.document_group,
                    dieu = EXCLUDED.dieu,
                    dieu_title = EXCLUDED.dieu_title,
                    khoan = EXCLUDED.khoan,
                    diem = EXCLUDED.diem,
                    so_qd = EXCLUDED.so_qd,
                    publication_year = EXCLUDED.publication_year,
                    page_start = EXCLUDED.page_start,
                    page_end = EXCLUDED.page_end,
                    tags = EXCLUDED.tags,
                    legal_status = EXCLUDED.legal_status,
                    embedding = EXCLUDED.embedding,
                    embedding_model = EXCLUDED.embedding_model,
                    ingest_run_id = EXCLUDED.ingest_run_id,
                    updated_at = now()
                ''',
                chunks,
            )

    def finalize_run(self, run_id: str, chunk_count: int) -> dict[str, int]:
        deleted_chunks = self.connection.execute(
            'DELETE FROM knowledge_chunks WHERE ingest_run_id <> %s',
            (run_id,),
        ).rowcount
        deleted_documents = self.connection.execute(
            '''
            DELETE FROM knowledge_documents AS document
            WHERE NOT EXISTS (
                SELECT 1 FROM knowledge_chunks AS chunk
                WHERE chunk.document_id = document.id
            )
            ''',
        ).rowcount
        self.connection.execute(
            '''
            UPDATE ingest_runs
            SET status = 'succeeded', completed_at = now(), chunk_count = %s
            WHERE id = %s
            ''',
            (chunk_count, run_id),
        )
        self.connection.commit()
        return {
            'previous_chunks': self._previous_chunks,
            'stored_chunks': chunk_count,
            'deleted_chunks': deleted_chunks,
            'deleted_documents': deleted_documents,
        }

    def abort_run(self) -> None:
        self.connection.rollback()

    def vector_search(self, embedding, limit: int = 20) -> list[dict[str, Any]]:
        rows = self.connection.execute(
            '''
            SELECT id, document_id, text, title, source, dieu, khoan,
                   1 - (embedding <=> %s) AS score
            FROM knowledge_chunks
            ORDER BY embedding <=> %s
            LIMIT %s
            ''',
            (embedding, embedding, limit),
        ).fetchall()
        columns = ('id', 'document_id', 'text', 'title', 'source', 'dieu', 'khoan', 'score')
        return [dict(zip(columns, row)) for row in rows]

    def search_corpus(self) -> list[dict[str, Any]]:
        rows = self.connection.execute(
            'SELECT id, document_id, text, title, source, dieu, khoan FROM knowledge_chunks'
        ).fetchall()
        columns = ('id', 'document_id', 'text', 'title', 'source', 'dieu', 'khoan')
        return [dict(zip(columns, row)) for row in rows]

    def counts(self) -> dict[str, int]:
        documents, chunks = self.connection.execute(
            '''
            SELECT
                (SELECT count(*) FROM knowledge_documents),
                (SELECT count(*) FROM knowledge_chunks)
            '''
        ).fetchone()
        return {'documents': documents, 'chunks': chunks}
