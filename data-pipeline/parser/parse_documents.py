"""Parse the downloaded IUH source manifest into normalized JSON documents."""

import argparse
import json
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .registry import parse_document


PIPELINE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATA_DIR = PIPELINE_ROOT / 'data'


def load_ocr_review_paths(validation_path: Path) -> set[str]:
    if not validation_path.exists():
        return set()
    validation = json.loads(validation_path.read_text(encoding='utf-8'))
    return {
        item['path']
        for item in validation.get('files', [])
        if item.get('requires_ocr_review') and item.get('path')
    }


def atomic_json_write(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + '\n',
        encoding='utf-8',
    )
    temporary.replace(path)


def run(args: argparse.Namespace) -> dict[str, Any]:
    manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
    records = [record for record in manifest if record.get('status') == 'downloaded']
    if args.only_group:
        records = [record for record in records if record.get('group') == args.only_group]
    if args.only_file:
        records = [record for record in records if record.get('path') == args.only_file]

    ocr_review_paths = load_ocr_review_paths(args.validation)
    results = []
    for index, record in enumerate(records, start=1):
        output_path = args.output / f"{record['sha256']}.json"
        if output_path.exists() and not args.force:
            results.append({
                'path': record['path'],
                'status': 'skipped',
                'output': str(output_path),
            })
            continue
        try:
            document = parse_document(
                record,
                args.data_dir,
                args.ocr_dir,
                args.ocr,
                record['path'] in ocr_review_paths,
            )
            atomic_json_write(output_path, document.to_dict())
            status = 'needs_review' if document.needs_review else 'parsed'
            results.append({
                'path': record['path'],
                'status': status,
                'method': document.extraction_method,
                'characters': len(document.text),
                'pages': len(document.pages),
                'output': str(output_path),
                'warnings': document.warnings,
            })
            print(f'[{index}/{len(records)}] {status}: {record["path"]}', flush=True)
        except Exception as exc:
            results.append({
                'path': record.get('path'),
                'status': 'failed',
                'error': f'{type(exc).__name__}: {exc}',
            })
            print(f'[{index}/{len(records)}] failed: {record.get("path")}: {exc}', file=sys.stderr, flush=True)
            if args.fail_fast:
                raise

    counts = Counter(item['status'] for item in results)
    report = {
        'generated_at': datetime.now(timezone.utc).isoformat(),
        'manifest': str(args.manifest),
        'total': len(records),
        'counts': dict(counts),
        'documents': results,
    }
    atomic_json_write(args.report, report)
    print(json.dumps({'total': len(records), 'counts': dict(counts)}, ensure_ascii=False))
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-dir', type=Path, default=DEFAULT_DATA_DIR)
    parser.add_argument('--manifest', type=Path, default=DEFAULT_DATA_DIR / 'manifest.json')
    parser.add_argument('--validation', type=Path, default=DEFAULT_DATA_DIR / 'validation.json')
    parser.add_argument('--output', type=Path, default=DEFAULT_DATA_DIR / 'processed' / 'documents')
    parser.add_argument('--ocr-dir', type=Path, default=DEFAULT_DATA_DIR / 'ocr')
    parser.add_argument('--report', type=Path, default=DEFAULT_DATA_DIR / 'reports' / 'parse-report.json')
    parser.add_argument('--ocr', choices=('auto', 'always', 'never'), default='auto')
    parser.add_argument('--only-group')
    parser.add_argument('--only-file')
    parser.add_argument('--force', action='store_true')
    parser.add_argument('--fail-fast', action='store_true')
    return parser


def main() -> None:
    run(build_parser().parse_args())


if __name__ == '__main__':
    main()
