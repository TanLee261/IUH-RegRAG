"""Run parse, chunk, embedding and pgvector storage as one pipeline."""

import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from chunk.chunk_documents import run as run_chunking
from embed import Embedder, EmbeddingConfig
from parser.parse_documents import run as run_parser
from storage import PostgresChunkStore, database_dsn_from_env

from .store_embeddings import embed_and_store


PIPELINE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATA_DIR = PIPELINE_ROOT / 'data'


def atomic_json_write(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + '\n',
        encoding='utf-8',
    )
    temporary.replace(path)


def downloaded_document_ids(manifest_path: Path) -> set[str]:
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    return {
        record['sha256']
        for record in manifest
        if record.get('status') == 'downloaded'
    }


def run(args: argparse.Namespace, *, embedder=None, store=None) -> dict[str, Any]:
    started = time.perf_counter()
    started_at = datetime.now(timezone.utc).isoformat()
    document_ids = downloaded_document_ids(args.manifest)
    parse_report = run_parser(argparse.Namespace(
        data_dir=args.data_dir,
        manifest=args.manifest,
        validation=args.validation,
        output=args.documents,
        ocr_dir=args.ocr_dir,
        report=args.parse_report,
        ocr=args.ocr,
        only_group=None,
        only_file=None,
        force=args.force_parse,
        fail_fast=True,
    ))
    chunk_report = run_chunking(argparse.Namespace(
        input=args.documents,
        output=args.chunks,
        report=args.chunk_report,
        max_chars=args.max_chars,
        only_document=None,
        include_needs_review=False,
        fail_fast=True,
        allowed_document_ids=document_ids,
    ))

    owns_store = store is None
    if embedder is None:
        embedder = Embedder(EmbeddingConfig.from_env())
    if store is None:
        store = PostgresChunkStore(args.database_url or database_dsn_from_env())
    try:
        storage_report = embed_and_store(args.chunks, embedder, store)
    finally:
        if owns_store:
            store.close()

    report = {
        'generated_at': datetime.now(timezone.utc).isoformat(),
        'started_at': started_at,
        'status': 'succeeded',
        'manifest': str(args.manifest),
        'parse': {
            'total': parse_report['total'],
            'counts': parse_report['counts'],
        },
        'chunk': {
            'documents': chunk_report['documents'],
            'document_counts': chunk_report['document_counts'],
            'chunks': chunk_report['chunks'],
        },
        'embedding_storage': storage_report,
        'duration_seconds': round(time.perf_counter() - started, 3),
    }
    atomic_json_write(args.report, report)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-dir', type=Path, default=DEFAULT_DATA_DIR)
    parser.add_argument('--manifest', type=Path, default=DEFAULT_DATA_DIR / 'manifest.json')
    parser.add_argument('--validation', type=Path, default=DEFAULT_DATA_DIR / 'validation.json')
    parser.add_argument('--documents', type=Path, default=DEFAULT_DATA_DIR / 'processed' / 'documents')
    parser.add_argument('--chunks', type=Path, default=DEFAULT_DATA_DIR / 'processed' / 'chunks' / 'chunks.jsonl')
    parser.add_argument('--ocr-dir', type=Path, default=DEFAULT_DATA_DIR / 'ocr')
    parser.add_argument('--parse-report', type=Path, default=DEFAULT_DATA_DIR / 'reports' / 'parse-report.json')
    parser.add_argument('--chunk-report', type=Path, default=DEFAULT_DATA_DIR / 'reports' / 'chunk-report.json')
    parser.add_argument('--report', type=Path, default=DEFAULT_DATA_DIR / 'reports' / 'ingest-report.json')
    parser.add_argument('--database-url')
    parser.add_argument('--ocr', choices=('auto', 'always', 'never'), default='auto')
    parser.add_argument('--max-chars', type=int, default=1800)
    parser.add_argument('--force-parse', action='store_true')
    return parser


def main() -> None:
    run(build_parser().parse_args())


if __name__ == '__main__':
    main()
