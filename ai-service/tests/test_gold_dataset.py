import json
from pathlib import Path
import pytest


DATASET_PATH = Path(__file__).parent / 'gold_dataset.json'
ALLOWED_CATEGORIES = {
    'canh_bao_hoc_tap',
    'buoc_thoi_hoc',
    'tot_nghiep',
    'thang_diem',
    'tin_chi_hoc_phan',
    'hoc_bong',
    'out_of_domain',
}


def load_dataset():
    assert DATASET_PATH.exists(), f"Gold dataset not found at {DATASET_PATH}"
    with open(DATASET_PATH, encoding='utf-8') as f:
        data = json.load(f)
    return data


def test_dataset_size_and_uniqueness():
    dataset = load_dataset()
    assert len(dataset) == 30, f"Expected 30 gold questions, got {len(dataset)}"

    ids = [item['id'] for item in dataset]
    assert len(ids) == len(set(ids)), "All question IDs must be unique"


def test_dataset_covers_all_required_categories():
    dataset = load_dataset()
    categories_present = {item['category'] for item in dataset}

    required_topics = {
        'canh_bao_hoc_tap',
        'buoc_thoi_hoc',
        'tot_nghiep',
        'thang_diem',
        'tin_chi_hoc_phan',
    }
    missing = required_topics - categories_present
    assert not missing, f"Dataset missing required topics: {missing}"

    for item in dataset:
        assert item['category'] in ALLOWED_CATEGORIES, f"Invalid category: {item['category']}"


def test_schema_conformance_for_all_questions():
    dataset = load_dataset()
    required_keys = {
        'id',
        'category',
        'question',
        'target_dieu',
        'target_khoan',
        'expected_document',
        'expected_keywords',
        'ground_truth_answer',
        'is_answerable',
    }

    for item in dataset:
        missing_keys = required_keys - set(item.keys())
        assert not missing_keys, f"Item {item.get('id')} missing keys: {missing_keys}"
        assert isinstance(item['question'], str) and len(item['question'].strip()) > 5
        assert isinstance(item['ground_truth_answer'], str) and len(item['ground_truth_answer'].strip()) > 5
        assert isinstance(item['is_answerable'], bool)
        assert isinstance(item['expected_keywords'], list)

        if item['is_answerable']:
            assert item['target_dieu'] is not None, f"Answerable item {item['id']} must specify target_dieu"
            assert len(item['expected_keywords']) > 0, f"Answerable item {item['id']} must have expected_keywords"
        else:
            assert item['target_dieu'] is None, f"Unanswerable item {item['id']} should have target_dieu = null"
            assert item['category'] == 'out_of_domain'
