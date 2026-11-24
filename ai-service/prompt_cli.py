"""Build and print a grounded prompt from retrieval and reranking results."""

import argparse
import json
from pathlib import Path
import sys

from generation import GroundedPromptBuilder
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
        retriever = Retriever(HybridSearcher(store, embedder))
        chunks = RerankedRetriever(retriever, reranker).retrieve(
            args.query,
            top_k=args.limit,
            retrieval_k=args.retrieval_k,
            candidate_k=args.candidate_k,
        )
    prompt = GroundedPromptBuilder().build(args.query, chunks)
    output = {
        'messages': prompt.as_chat_messages(),
        'source_ids': prompt.source_ids,
    }
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
