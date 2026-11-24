from pathlib import Path
import pytest

from eval_rag import (
    check_cites_correct_dieu,
    compute_keyword_recall,
    evaluate_single_query,
    extract_dieus_from_chunks,
    format_report_table,
    run_evaluation,
    summarize_results,
)


def test_extract_dieus_from_chunks():
    chunks = [
        {'dieu': '18', 'text': 'Nội dung điều 18'},
        {'dieu': None, 'text': 'Theo Điều 22 quy định tốt nghiệp'},
        {'text': 'Văn bản không có điều khoản'},
    ]
    dieus = extract_dieus_from_chunks(chunks)
    assert dieus == ['18', '22']


def test_check_cites_correct_dieu():
    assert check_cites_correct_dieu('Theo Điều 18 Quy chế đào tạo...', '18') is True
    assert check_cites_correct_dieu('Theo điều 18 khoản 2...', '18') is True
    assert check_cites_correct_dieu('Theo Điêu 18a...', '18a') is True
    assert check_cites_correct_dieu('Theo Điều 19...', '18') is False
    assert check_cites_correct_dieu('Câu trả lời không có trích dẫn', None) is True


def test_compute_keyword_recall():
    keywords = ['cảnh báo', 'học tập', '1.00', 'học kỳ đầu']
    text = 'Sinh viên bị cảnh báo học tập nếu điểm học kỳ đầu dưới 1.00'
    recall = compute_keyword_recall(text, keywords)
    assert recall == 1.0

    partial_text = 'Sinh viên bị cảnh báo'
    partial_recall = compute_keyword_recall(partial_text, keywords)
    assert partial_recall == 0.25

    assert compute_keyword_recall('bất kỳ', []) == 1.0


def test_evaluate_single_query_answerable():
    item = {
        'id': 'gold-01',
        'category': 'canh_bao_hoc_tap',
        'question': 'Điều kiện cảnh báo học tập?',
        'target_dieu': '18',
        'target_khoan': '1',
        'expected_keywords': ['cảnh báo', '1.00'],
        'is_answerable': True,
    }
    chunks = [
        {'id': 'c1', 'dieu': '18', 'text': 'Điều 18. Cảnh báo học tập dưới 1.00'},
        {'id': 'c2', 'dieu': '19', 'text': 'Điều 19. Buộc thôi học'},
    ]
    answer = 'Theo Điều 18 Khoản 1 [Nguồn 1: Điều 18], sinh viên bị cảnh báo khi điểm < 1.00'

    res = evaluate_single_query(item, chunks, answer, 'answered')
    assert res.hit_at_1 is True
    assert res.hit_at_3 is True
    assert res.has_citation is True
    assert res.cites_correct_dieu is True
    assert res.empty_correct is True
    assert res.keyword_recall == 1.0


def test_evaluate_single_query_out_of_domain():
    item = {
        'id': 'gold-29',
        'category': 'out_of_domain',
        'question': 'Thời tiết ngày mai thế nào?',
        'target_dieu': None,
        'target_khoan': None,
        'expected_keywords': [],
        'is_answerable': False,
    }
    res = evaluate_single_query(item, [], 'Không tìm thấy trong quy chế', 'no_context')
    assert res.is_answerable is False
    assert res.empty_correct is True
    assert res.cites_correct_dieu is True


def test_run_evaluation_on_gold_dataset():
    dataset_path = Path(__file__).parent / 'gold_dataset.json'
    summary, results = run_evaluation(dataset_path, use_mock=True)

    assert summary.total_queries == 30
    assert summary.answerable_queries == 28
    assert summary.unanswerable_queries == 2
    assert summary.hit_at_1 == 1.0
    assert summary.hit_at_3 == 1.0
    assert summary.hit_at_5 == 1.0
    assert summary.citation_rate == 1.0
    assert summary.correct_dieu_rate == 1.0
    assert summary.empty_accuracy == 1.0
    assert summary.average_keyword_recall >= 0.85

    report_table = format_report_table(summary)
    assert "IUH-RegRAG — Evaluation Benchmark Summary" in report_table
    assert "Hit@1" in report_table
