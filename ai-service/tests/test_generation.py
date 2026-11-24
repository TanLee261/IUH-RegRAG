import json
from types import SimpleNamespace

import pytest

from generation import (
    EMPTY_RETRIEVAL_MESSAGE,
    GeminiProvider,
    GenerationConfig,
    GenerationService,
    GenerationUnavailableError,
    OllamaProvider,
    ProviderError,
    ProviderResponse,
)
from generation.service import generation_service_from_env


CHUNKS = [{
    'id': 'chunk-19-2', 'title': 'Quy chế đào tạo', 'dieu': '19', 'khoan': '2',
    'text': 'Sinh viên phải nộp hồ sơ tại Phòng Đào tạo.',
    'source': 'https://example.test/quy-che',
    'document_id': 'document-quy-che', 'rerank_score': 0.91,
}]
CITATION = '[Nguồn 1: Quy chế đào tạo, Điều 19, Khoản 2]'


class FakeProvider:
    def __init__(self, name, model, *, text=None, error=None):
        self._name, self._model = name, model
        self.text, self.error, self.calls = text, error, 0

    @property
    def name(self):
        return self._name

    @property
    def model(self):
        return self._model

    def generate(self, prompt):
        self.calls += 1
        if self.error:
            raise self.error
        return ProviderResponse(self.text, self.name, self.model)


def test_returns_grounded_answer_and_cited_chunk_ids():
    primary = FakeProvider('gemini', 'gemini-test', text=f'Nộp tại PĐT. {CITATION}')
    result = GenerationService(primary).answer('Nộp hồ sơ ở đâu?', CHUNKS)
    assert result.answer.endswith(CITATION)
    assert result.provider == 'gemini'
    assert result.source_ids == ('chunk-19-2',)
    assert result.cited_source_ids == ('chunk-19-2',)
    assert result.fallback_used is False
    assert result.status == 'answered'
    assert len(result.sources) == 1
    assert result.sources[0].as_dict() == {
        'chunk_id': 'chunk-19-2',
        'document_id': 'document-quy-che',
        'title': 'Quy chế đào tạo',
        'dieu': '19',
        'khoan': '2',
        'source': 'https://example.test/quy-che',
        'snippet': 'Sinh viên phải nộp hồ sơ tại Phòng Đào tạo.',
        'score': pytest.approx(0.91),
    }


def test_empty_retrieval_skips_provider_and_returns_safe_message():
    primary = FakeProvider('gemini', 'gemini-test', text='Không được gọi')

    result = GenerationService(primary).answer('Cách nấu phở?', [])

    assert result.answer == EMPTY_RETRIEVAL_MESSAGE
    assert result.status == 'no_context'
    assert result.provider == 'none'
    assert result.model == 'none'
    assert result.source_ids == ()
    assert result.cited_source_ids == ()
    assert result.fallback_used is False
    assert result.sources == ()
    assert primary.calls == 0


def test_empty_retrieval_still_validates_question():
    with pytest.raises(ValueError, match='query must not be empty'):
        GenerationService(FakeProvider('gemini', 'test')).answer('   ', [])


@pytest.mark.parametrize('primary', [
    FakeProvider('gemini', 'gemini-test', error=ProviderError('failed')),
    FakeProvider('gemini', 'gemini-test', text='Không có trích dẫn.'),
    FakeProvider('gemini', 'gemini-test', text='[Nguồn 2: Nguồn bịa, Điều 1, Khoản 1]'),
])
def test_falls_back_when_primary_fails_or_returns_invalid_citation(primary):
    fallback = FakeProvider('ollama', 'qwen2.5:7b', text=f'Nộp tại PĐT. {CITATION}')
    result = GenerationService(primary, fallback).answer('Nộp ở đâu?', CHUNKS)
    assert result.provider == 'ollama'
    assert result.model == 'qwen2.5:7b'
    assert result.fallback_used is True
    assert fallback.calls == 1


def test_allows_exact_insufficient_context_message_without_citation():
    provider = FakeProvider(
        'gemini', 'gemini-test',
        text='Thông tin trong tài liệu được cung cấp chưa đủ để trả lời câu hỏi này.',
    )
    result = GenerationService(provider).answer('Câu hỏi ngoài tài liệu?', CHUNKS)
    assert result.cited_source_ids == ()
    assert result.sources == ()


def test_raises_when_all_providers_fail_validation():
    primary = FakeProvider('gemini', 'gemini-test', text='Không có citation')
    fallback = FakeProvider('ollama', 'qwen2.5:7b', error=ProviderError('offline'))
    with pytest.raises(GenerationUnavailableError):
        GenerationService(primary, fallback).answer('Nộp ở đâu?', CHUNKS)


def test_config_switches_provider_without_changing_pipeline(monkeypatch):
    monkeypatch.setenv('LLM_PROVIDER', 'ollama')
    monkeypatch.setenv('LLM_MODEL', 'qwen2.5:14b')
    monkeypatch.setenv('LLM_TEMPERATURE', '0.1')
    monkeypatch.setenv('LLM_FALLBACK_ENABLED', 'false')
    config = GenerationConfig.from_env()
    service = generation_service_from_env()
    assert config.temperature == 0.1
    assert service.primary.name == 'ollama'
    assert service.primary.model == 'qwen2.5:14b'
    assert service.fallback is None


class FakeInteractions:
    def create(self, **kwargs):
        self.kwargs = kwargs
        return SimpleNamespace(output_text=f'Trả lời. {CITATION}')


def test_gemini_provider_uses_interactions_api_and_low_temperature():
    interactions = FakeInteractions()
    provider = GeminiProvider(
        'gemini-test', temperature=0.2, max_output_tokens=800,
        timeout_seconds=12, client=SimpleNamespace(interactions=interactions),
    )
    prompt = GenerationService(FakeProvider('x', 'x')).prompt_builder.build('Hỏi?', CHUNKS)
    response = provider.generate(prompt)
    assert response.provider == 'gemini'
    assert interactions.kwargs['generation_config'] == {
        'temperature': 0.2, 'max_output_tokens': 800,
    }
    assert interactions.kwargs['store'] is False
    assert interactions.kwargs['timeout'] == 12


class FakeHttpResponse:
    def __enter__(self):
        return self

    def __exit__(self, *_):
        return None

    def read(self):
        return json.dumps({'message': {'content': f'Trả lời. {CITATION}'}}).encode()


def test_ollama_provider_sends_chat_payload():
    recorded = {}

    def opener(request, timeout):
        recorded.update(url=request.full_url, payload=json.loads(request.data), timeout=timeout)
        return FakeHttpResponse()

    provider = OllamaProvider(
        'qwen2.5:7b', base_url='http://localhost:11434/', temperature=0.2,
        max_output_tokens=512, timeout_seconds=20, opener=opener,
    )
    prompt = GenerationService(FakeProvider('x', 'x')).prompt_builder.build('Hỏi?', CHUNKS)
    assert provider.generate(prompt).provider == 'ollama'
    assert recorded['url'] == 'http://localhost:11434/api/chat'
    assert recorded['payload']['model'] == 'qwen2.5:7b'
    assert recorded['payload']['stream'] is False
    assert recorded['payload']['options']['temperature'] == 0.2
    assert recorded['timeout'] == 20
