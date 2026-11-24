"""Run a hybrid BM25 and pgvector query against the ingested corpus."""

import argparse
import json

from embed import Embedder, EmbeddingConfig

from .hybrid import HybridSearcher
from .postgres import PostgresChunkStore, database_dsn_from_env


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('query')
    parser.add_argument('--limit', type=int, default=5)
    parser.add_argument('--candidate-k', type=int, default=30)
    parser.add_argument('--database-url')
    args = parser.parse_args()

    embedder = Embedder(EmbeddingConfig.from_env())
    with PostgresChunkStore(args.database_url or database_dsn_from_env()) as store:
        results = HybridSearcher(store, embedder).search(
            args.query,
            limit=args.limit,
            candidate_k=args.candidate_k,
        )
    print(json.dumps(results, ensure_ascii=False, indent=2, default=str))


if __name__ == '__main__':
    main()
