"""Batch normalized documents into citation-ready JSONL chunks."""

import argparse
import json
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .chunker import chunk_document


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


def atomic_jsonl_write(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    with temporary.open('w', encoding='utf-8') as output:
        for row in rows:
            output.write(json.dumps(row, ensure_ascii=False, separators=(',', ':')) + '\n')
    temporary.replace(path)


def run(args: argparse.Namespace) -> dict[str, Any]:
    paths = sorted(args.input.glob('*.json'))
    allowed_document_ids = getattr(args, 'allowed_document_ids', None)
    if allowed_document_ids is not None:
        paths = [path for path in paths if path.stem in allowed_document_ids]
    if args.only_document:
        paths = [path for path in paths if path.stem == args.only_document]

    rows: list[dict[str, Any]] = []
    results: list[dict[str, Any]] = []
    for index, path in enumerate(paths, start=1):
        try:
            document = json.loads(path.read_text(encoding='utf-8'))
            if document.get('needs_review') and not args.include_needs_review:
                results.append({
                    'document_id': document.get('document_id', path.stem),
                    'status': 'skipped_review',
                    'chunks': 0,
                })
                continue
            if args.include_needs_review:
                document['needs_review'] = False
            chunks = chunk_document(document, max_chars=args.max_chars)
            payloads = [chunk.to_dict() for chunk in chunks]
            rows.extend(payloads)
            status = 'chunked' if chunks else 'empty'
            results.append({
                'document_id': document['document_id'],
                'status': status,
                'chunks': len(chunks),
                'with_dieu': sum(chunk.dieu is not None for chunk in chunks),
                'with_khoan': sum(chunk.khoan is not None for chunk in chunks),
                'oversized': sum(len(chunk.text) > args.max_chars for chunk in chunks),
            })
            print(f'[{index}/{len(paths)}] {status}: {path.name} ({len(chunks)} chunks)', flush=True)
        except Exception as exc:
            results.append({
                'document_id': path.stem,
                'status': 'failed',
                'chunks': 0,
                'error': f'{type(exc).__name__}: {exc}',
            })
            print(f'[{index}/{len(paths)}] failed: {path.name}: {exc}', file=sys.stderr, flush=True)
            if args.fail_fast:
                raise

    atomic_jsonl_write(args.output, rows)
    counts = Counter(result['status'] for result in results)
    report = {
        'generated_at': datetime.now(timezone.utc).isoformat(),
        'input': str(args.input),
        'output': str(args.output),
        'max_chars': args.max_chars,
        'documents': len(paths),
        'document_counts': dict(counts),
        'chunks': len(rows),
        'with_dieu': sum(row['dieu'] is not None for row in rows),
        'with_khoan': sum(row['khoan'] is not None for row in rows),
        'oversized': sum(len(row['text']) > args.max_chars for row in rows),
        'max_chunk_characters': max((len(row['text']) for row in rows), default=0),
        'results': results,
    }
    atomic_json_write(args.report, report)
    print(json.dumps({
        'documents': len(paths),
        'document_counts': dict(counts),
        'chunks': len(rows),
    }, ensure_ascii=False))
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        '--input',
        type=Path,
        default=DEFAULT_DATA_DIR / 'processed' / 'documents',
    )
    parser.add_argument(
        '--output',
        type=Path,
        default=DEFAULT_DATA_DIR / 'processed' / 'chunks' / 'chunks.jsonl',
    )
    parser.add_argument(
        '--report',
        type=Path,
        default=DEFAULT_DATA_DIR / 'reports' / 'chunk-report.json',
    )
    parser.add_argument('--max-chars', type=int, default=1800)
    parser.add_argument('--only-document')
    parser.add_argument('--include-needs-review', action='store_true')
    parser.add_argument('--fail-fast', action='store_true')
    return parser


def main() -> None:
    run(build_parser().parse_args())


if __name__ == '__main__':
    main()
