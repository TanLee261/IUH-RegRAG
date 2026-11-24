"""Validate BGE-M3 by embedding the normalized chunk corpus."""

import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

from .config import EmbeddingConfig
from .embedder import Embedder
from .input_builder import build_embedding_input


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


def load_chunks(path: Path, limit: int | None = None) -> list[dict[str, Any]]:
    chunks: list[dict[str, Any]] = []
    with path.open(encoding='utf-8') as source:
        for line in source:
            if not line.strip():
                continue
            chunks.append(json.loads(line))
            if limit is not None and len(chunks) >= limit:
                break
    return chunks


def semantic_smoke_test(embedder: Embedder) -> dict[str, Any]:
    query = 'Sinh viên được cấp bằng tốt nghiệp khi nào?'
    positive = (
        'Tài liệu: Quy chế công tác sinh viên\n'
        'Điều 5: Quyền của sinh viên\n'
        'Sinh viên đủ điều kiện công nhận tốt nghiệp được cấp bằng tốt nghiệp.'
    )
    negative = (
        'Tài liệu: Thông báo học phí\n'
        'Sinh viên đóng học phí qua ngân hàng theo thời hạn quy định.'
    )
    query_vector = embedder.embed_query(query)
    candidates = embedder.embed_documents([positive, negative])
    scores = candidates @ query_vector
    return {
        'positive_score': float(scores[0]),
        'negative_score': float(scores[1]),
        'passed': bool(scores[0] > scores[1]),
    }


def run(args: argparse.Namespace) -> dict[str, Any]:
    config = EmbeddingConfig.from_env()
    if args.device:
        config = EmbeddingConfig(
            model_name=config.model_name,
            dimension=config.dimension,
            max_tokens=config.max_tokens,
            batch_size=config.batch_size,
            device=args.device,
        )
    if args.batch_size:
        config = EmbeddingConfig(
            model_name=config.model_name,
            dimension=config.dimension,
            max_tokens=config.max_tokens,
            batch_size=args.batch_size,
            device=config.device,
        )

    started = time.perf_counter()
    chunks = load_chunks(args.input, args.limit)
    inputs = [build_embedding_input(chunk) for chunk in chunks]
    embedder = Embedder(config)
    token_lengths = embedder.token_lengths(inputs)
    vectors = embedder.embed_documents(inputs)
    norms = np.linalg.norm(vectors, axis=1) if len(vectors) else np.array([], dtype=np.float32)
    smoke = None if args.skip_semantic_smoke else semantic_smoke_test(embedder)
    report = {
        'generated_at': datetime.now(timezone.utc).isoformat(),
        'input': str(args.input),
        'model': config.model_name,
        'dimension': config.dimension,
        'max_tokens': config.max_tokens,
        'batch_size': config.batch_size,
        'device': embedder.device,
        'chunks': len(chunks),
        'embedded': len(vectors),
        'failed': 0,
        'truncated': sum(length > config.max_tokens for length in token_lengths),
        'max_observed_tokens': max(token_lengths, default=0),
        'normalized': config.normalize_embeddings,
        'min_vector_norm': float(norms.min()) if len(norms) else None,
        'max_vector_norm': float(norms.max()) if len(norms) else None,
        'semantic_smoke': smoke,
        'duration_seconds': round(time.perf_counter() - started, 3),
    }
    atomic_json_write(args.report, report)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if smoke is not None and not smoke['passed']:
        raise RuntimeError('semantic smoke test failed')
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        '--input',
        type=Path,
        default=DEFAULT_DATA_DIR / 'processed' / 'chunks' / 'chunks.jsonl',
    )
    parser.add_argument(
        '--report',
        type=Path,
        default=DEFAULT_DATA_DIR / 'reports' / 'embedding-report.json',
    )
    parser.add_argument('--limit', type=int)
    parser.add_argument('--batch-size', type=int)
    parser.add_argument('--device', choices=('auto', 'cpu', 'mps', 'cuda'))
    parser.add_argument('--skip-semantic-smoke', action='store_true')
    return parser


def main() -> None:
    run(build_parser().parse_args())


if __name__ == '__main__':
    main()
