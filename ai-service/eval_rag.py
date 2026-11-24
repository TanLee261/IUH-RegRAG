"""Evaluation script for IUH-RegRAG: Hit@k, Citation Accuracy, and Empty Retrieval Precision."""

import argparse
from dataclasses import asdict, dataclass
import json
import logging
from pathlib import Path
import re
import sys
from typing import Any, Mapping, Sequence


PIPELINE_DIR = Path(__file__).resolve().parents[1] / 'data-pipeline'
if str(PIPELINE_DIR) not in sys.path:
    sys.path.insert(0, str(PIPELINE_DIR))

CITATION_REGEX = re.compile(r'\[Nguồn\s+\d+[^\]]*\]|Điều\s+(\d+[A-Za-zĐđ]?)', re.IGNORECASE)


@dataclass
class QueryEvalResult:
    id: str
    category: str
    question: str
    is_answerable: bool
    target_dieu: str | None
    hit_at_1: bool
    hit_at_3: bool
    hit_at_5: bool
    has_citation: bool
    cites_correct_dieu: bool
    empty_correct: bool
    status: str
    retrieved_dieus: list[str]
    keyword_recall: float


@dataclass
class EvalSummary:
    total_queries: int
    answerable_queries: int
    unanswerable_queries: int
    hit_at_1: float
    hit_at_3: float
    hit_at_5: float
    citation_rate: float
    correct_dieu_rate: float
    empty_accuracy: float
    average_keyword_recall: float


def extract_dieus_from_chunks(chunks: Sequence[Mapping[str, Any]]) -> list[str]:
    dieus = []
    for c in chunks:
        dieu = c.get('dieu')
        if dieu:
            dieus.append(str(dieu).strip())
        else:
            match = re.search(r'Điều\s+(\d+[A-Za-zĐđ]?)', str(c.get('text', '')), re.IGNORECASE)
            if match:
                dieus.append(match.group(1))
    return dieus


def check_cites_correct_dieu(answer: str, target_dieu: str | None) -> bool:
    if not target_dieu:
        return True
    pattern = rf'\b(?:Điều|Điêu)\s+{re.escape(target_dieu)}\b'
    return bool(re.search(pattern, answer, re.IGNORECASE))


def compute_keyword_recall(text: str, keywords: Sequence[str]) -> float:
    if not keywords:
        return 1.0
    text_lower = text.lower()
    matches = sum(1 for kw in keywords if kw.lower() in text_lower)
    return matches / len(keywords)


def evaluate_single_query(
    item: Mapping[str, Any],
    retrieved_chunks: Sequence[Mapping[str, Any]],
    answer: str,
    status: str,
) -> QueryEvalResult:
    target_dieu = str(item['target_dieu']).strip() if item.get('target_dieu') else None
    is_answerable = bool(item.get('is_answerable', True))
    expected_keywords = item.get('expected_keywords', [])

    chunk_dieus = extract_dieus_from_chunks(retrieved_chunks)

    hit_1 = bool(target_dieu and len(chunk_dieus) >= 1 and chunk_dieus[0] == target_dieu)
    hit_3 = bool(target_dieu and target_dieu in chunk_dieus[:3])
    hit_5 = bool(target_dieu and target_dieu in chunk_dieus[:5])

    has_citation = bool(CITATION_REGEX.search(answer))
    cites_correct = check_cites_correct_dieu(answer, target_dieu) if target_dieu else True

    if not is_answerable:
        # For OOD: empty retrieval is correct when status is no_context/insufficient_context or no chunks
        empty_correct = status in ('no_context', 'insufficient_context') or len(retrieved_chunks) == 0
    else:
        empty_correct = status == 'answered'

    # Keyword recall measured on answer + retrieved context
    all_context = answer + ' ' + ' '.join(str(c.get('text', '')) for c in retrieved_chunks)
    kw_recall = compute_keyword_recall(all_context, expected_keywords)

    return QueryEvalResult(
        id=item['id'],
        category=item['category'],
        question=item['question'],
        is_answerable=is_answerable,
        target_dieu=target_dieu,
        hit_at_1=hit_1,
        hit_at_3=hit_3,
        hit_at_5=hit_5,
        has_citation=has_citation,
        cites_correct_dieu=cites_correct,
        empty_correct=empty_correct,
        status=status,
        retrieved_dieus=chunk_dieus,
        keyword_recall=kw_recall,
    )


def summarize_results(results: Sequence[QueryEvalResult]) -> EvalSummary:
    total = len(results)
    answerable = [r for r in results if r.is_answerable]
    unanswerable = [r for r in results if not r.is_answerable]

    n_ans = len(answerable)
    n_unans = len(unanswerable)

    hit_1 = sum(1 for r in answerable if r.hit_at_1) / n_ans if n_ans else 1.0
    hit_3 = sum(1 for r in answerable if r.hit_at_3) / n_ans if n_ans else 1.0
    hit_5 = sum(1 for r in answerable if r.hit_at_5) / n_ans if n_ans else 1.0

    cit_rate = sum(1 for r in answerable if r.has_citation) / n_ans if n_ans else 1.0
    correct_cit = sum(1 for r in answerable if r.cites_correct_dieu) / n_ans if n_ans else 1.0

    empty_acc = sum(1 for r in unanswerable if r.empty_correct) / n_unans if n_unans else 1.0
    avg_kw = sum(r.keyword_recall for r in answerable) / n_ans if n_ans else 1.0

    return EvalSummary(
        total_queries=total,
        answerable_queries=n_ans,
        unanswerable_queries=n_unans,
        hit_at_1=round(hit_1, 4),
        hit_at_3=round(hit_3, 4),
        hit_at_5=round(hit_5, 4),
        citation_rate=round(cit_rate, 4),
        correct_dieu_rate=round(correct_cit, 4),
        empty_accuracy=round(empty_acc, 4),
        average_keyword_recall=round(avg_kw, 4),
    )


def mock_pipeline_answer(item: Mapping[str, Any]) -> tuple[list[dict[str, Any]], str, str]:
    """Deterministic simulated pipeline for offline/CI evaluation."""
    if not item.get('is_answerable', True):
        return [], 'Không tìm thấy trong quy chế đã nạp. Câu hỏi nằm ngoài phạm vi học vụ.', 'no_context'

    target_dieu = item.get('target_dieu', '1')
    target_khoan = item.get('target_khoan', '1')
    ground_truth = item.get('ground_truth_answer', '')

    chunks = [
        {
            'id': f'chunk-{target_dieu}-{target_khoan}',
            'dieu': target_dieu,
            'khoan': target_khoan,
            'title': item.get('expected_document') or 'Quy chế đào tạo IUH',
            'text': f"Điều {target_dieu}. Quy định: {ground_truth}",
        },
        {
            'id': f'chunk-{target_dieu}-2',
            'dieu': target_dieu,
            'khoan': '2',
            'title': item.get('expected_document') or 'Quy chế đào tạo IUH',
            'text': f"Điều {target_dieu} Khoản 2. Các nội dung liên quan khác.",
        },
    ]

    answer = f"Theo Điều {target_dieu} Khoản {target_khoan} {chunks[0]['title']}, {ground_truth} [Nguồn 1: {chunks[0]['title']}, Điều {target_dieu}]"
    return chunks, answer, 'answered'


def run_evaluation(
    dataset_path: Path,
    use_mock: bool = True,
    database_url: str | None = None,
) -> tuple[EvalSummary, list[QueryEvalResult]]:
    with open(dataset_path, encoding='utf-8') as f:
        dataset = json.load(f)

    results = []

    if use_mock:
        for item in dataset:
            chunks, answer, status = mock_pipeline_answer(item)
            res = evaluate_single_query(item, chunks, answer, status)
            results.append(res)
    else:
        from generation import generation_service_from_env
        from rerank import RerankedRetriever, Reranker, RerankerConfig
        from retrieval import Retriever
        from embed import Embedder, EmbeddingConfig
        from storage.hybrid import HybridSearcher
        from storage.postgres import PostgresChunkStore, database_dsn_from_env

        dsn = database_url or database_dsn_from_env()
        with PostgresChunkStore(dsn) as store:
            embedder = Embedder(EmbeddingConfig.from_env())
            retriever = Retriever(HybridSearcher(store, embedder))
            reranked_retriever = RerankedRetriever(retriever, Reranker(RerankerConfig.from_env()))
            generator = generation_service_from_env()

            for item in dataset:
                question = item['question']
                chunks = reranked_retriever.retrieve(question, top_k=5)
                gen_result = generator.answer(question, chunks)
                res = evaluate_single_query(item, chunks, gen_result.answer, gen_result.status)
                results.append(res)

    summary = summarize_results(results)
    return summary, results


def format_report_table(summary: EvalSummary) -> str:
    lines = [
        "=" * 64,
        "          IUH-RegRAG — Evaluation Benchmark Summary",
        "=" * 64,
        f" Total Test Questions        : {summary.total_queries}",
        f" Answerable Regulations      : {summary.answerable_queries}",
        f" Out-of-Domain (OOD) Queries : {summary.unanswerable_queries}",
        "-" * 64,
        f" Hit@1 (Top-1 Retrieval)     : {summary.hit_at_1 * 100:.1f}%",
        f" Hit@3 (Top-3 Retrieval)     : {summary.hit_at_3 * 100:.1f}%",
        f" Hit@5 (Top-5 Retrieval)     : {summary.hit_at_5 * 100:.1f}%",
        "-" * 64,
        f" Citation Presence Rate      : {summary.citation_rate * 100:.1f}%",
        f" Correct Article Cited Rate  : {summary.correct_dieu_rate * 100:.1f}%",
        f" Empty Retrieval Precision   : {summary.empty_accuracy * 100:.1f}%",
        f" Average Keyword Recall      : {summary.average_keyword_recall * 100:.1f}%",
        "=" * 64,
    ]
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    default_dataset = Path(__file__).parent / 'tests' / 'gold_dataset.json'
    parser.add_argument('--dataset', type=Path, default=default_dataset)
    parser.add_argument('--output', type=Path, default=None)
    parser.add_argument('--mock', action='store_true', default=True, help="Run with deterministic mock pipeline")
    parser.add_argument('--live', action='store_false', dest='mock', help="Run with live database and LLM pipeline")
    parser.add_argument('--database-url', type=str, default=None)
    args = parser.parse_args()

    summary, results = run_evaluation(
        args.dataset,
        use_mock=args.mock,
        database_url=args.database_url,
    )

    report_text = format_report_table(summary)
    print(report_text)

    if args.output:
        output_payload = {
            'summary': asdict(summary),
            'queries': [asdict(r) for r in results],
        }
        with open(args.output, 'w', encoding='utf-8') as f:
            json.dump(output_payload, f, ensure_ascii=False, indent=2)
        print(f"\n[Artifact] Detailed JSON evaluation saved to: {args.output}")


if __name__ == '__main__':
    main()
