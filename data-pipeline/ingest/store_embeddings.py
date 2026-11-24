"""Stream chunk JSONL through the embedder into a transactional store."""

import json
import time
from pathlib import Path
from typing import Any, Iterator

from chunk.models import Chunk
from embed import Embedder, build_embedding_input
from storage.base import ChunkStore, EmbeddedChunk


def iter_chunks(path: Path) -> Iterator[dict[str, Any]]:
    with path.open(encoding='utf-8') as source:
        for line_number, line in enumerate(source, start=1):
            if not line.strip():
                continue
            try:
                payload = json.loads(line)
                validated = Chunk(**{
                    **payload,
                    'tags': tuple(payload['tags']),
                })
            except Exception as exc:
                raise ValueError(f'invalid chunk at JSONL line {line_number}: {exc}') from exc
            yield validated.to_dict()


def batched(rows: Iterator[dict[str, Any]], size: int) -> Iterator[list[dict[str, Any]]]:
    if size < 1:
        raise ValueError('batch size must be positive')
    batch: list[dict[str, Any]] = []
    for row in rows:
        batch.append(row)
        if len(batch) == size:
            yield batch
            batch = []
    if batch:
        yield batch


def embed_and_store(
    chunks_path: Path,
    embedder: Embedder,
    store: ChunkStore,
) -> dict[str, Any]:
    started = time.perf_counter()
    config = embedder.config
    run_id = store.begin_run(config.model_name, config.dimension)
    embedded = 0
    batches = 0
    max_observed_tokens = 0
    try:
        for batch in batched(iter_chunks(chunks_path), config.batch_size):
            inputs = [build_embedding_input(chunk) for chunk in batch]
            lengths = embedder.token_lengths(inputs)
            max_observed_tokens = max(max_observed_tokens, *lengths)
            oversized = [length for length in lengths if length > config.max_tokens]
            if oversized:
                raise ValueError(
                    f'{len(oversized)} chunks exceed the {config.max_tokens}-token limit'
                )
            vectors = embedder.embed_documents(inputs)
            store.upsert_batch([
                EmbeddedChunk(chunk, vector)
                for chunk, vector in zip(batch, vectors, strict=True)
            ], run_id)
            embedded += len(batch)
            batches += 1
        storage = store.finalize_run(run_id, embedded)
    except Exception:
        store.abort_run()
        raise
    return {
        'run_id': run_id,
        'model': config.model_name,
        'dimension': config.dimension,
        'batch_size': config.batch_size,
        'embedded': embedded,
        'batches': batches,
        'max_observed_tokens': max_observed_tokens,
        'truncated': 0,
        'storage': storage,
        'duration_seconds': round(time.perf_counter() - started, 3),
    }
