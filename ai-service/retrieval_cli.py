"""Run query-aware hybrid retrieval against the ingested corpus."""

import argparse
import json
from pathlib import Path
import sys

from retrieval import Retriever


PIPELINE_DIR = Path(__file__).resolve().parents[1] / 'data-pipeline'
if str(PIPELINE_DIR) not in sys.path:
    sys.path.insert(0, str(PIPELINE_DIR))

from embed import Embedder, EmbeddingConfig  # noqa: E402
from storage.hybrid import HybridSearcher  # noqa: E402
from storage.postgres import PostgresChunkStore, database_dsn_from_env  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('query')
    parser.add_argument('--limit', type=int, default=5)
    parser.add_argument('--candidate-k', type=int, default=30)
    parser.add_argument('--database-url')
    args = parser.parse_args()

    embedder = Embedder(EmbeddingConfig.from_env())
    with PostgresChunkStore(args.database_url or database_dsn_from_env()) as store:
        retriever = Retriever(HybridSearcher(store, embedder))
        results = retriever.retrieve(
            args.query,
            top_k=args.limit,
            candidate_k=args.candidate_k,
        )
    print(json.dumps(results, ensure_ascii=False, indent=2, default=str))


if __name__ == '__main__':
    main()
