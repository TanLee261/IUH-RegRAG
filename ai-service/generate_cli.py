"""Run retrieval, reranking and grounded LLM generation for one question."""

import argparse
import json
from pathlib import Path
import sys

from generation import generation_service_from_env
from rerank import RerankedRetriever, Reranker, RerankerConfig
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
    parser.add_argument('--retrieval-k', type=int, default=20)
    parser.add_argument('--candidate-k', type=int, default=30)
    parser.add_argument('--database-url')
    args = parser.parse_args()

    embedder = Embedder(EmbeddingConfig.from_env())
    reranker = Reranker(RerankerConfig.from_env())
    with PostgresChunkStore(args.database_url or database_dsn_from_env()) as store:
        chunks = RerankedRetriever(
            Retriever(HybridSearcher(store, embedder)), reranker
        ).retrieve(
            args.query, top_k=args.limit, retrieval_k=args.retrieval_k,
            candidate_k=args.candidate_k,
        )

    result = generation_service_from_env().answer(args.query, chunks)
    print(json.dumps({
        'answer': result.answer,
        'provider': result.provider,
        'model': result.model,
        'source_ids': result.source_ids,
        'cited_source_ids': result.cited_source_ids,
        'fallback_used': result.fallback_used,
        'status': result.status,
        'sources': [source.as_dict() for source in result.sources],
    }, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
