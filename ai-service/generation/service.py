"""Grounded generation orchestration with provider fallback."""

from dataclasses import dataclass
import logging
import re
from typing import Any, Iterator, Literal, Mapping, Sequence

from retrieval import understand_query

from .config import GenerationConfig
from .prompt import GroundedPrompt, GroundedPromptBuilder
from .providers import (
    GeminiProvider,
    GenerationProvider,
    OllamaProvider,
    ProviderError,
)
from .sources import StructuredSource, build_structured_sources


LOGGER = logging.getLogger(__name__)
INSUFFICIENT_CONTEXT_MESSAGE = (
    'Thông tin trong tài liệu được cung cấp chưa đủ để trả lời câu hỏi này.'
)
EMPTY_RETRIEVAL_MESSAGE = (
    'Không tìm thấy trong quy chế đã nạp. '
    'Bạn vui lòng liên hệ Phòng Đào tạo để được hướng dẫn và xác nhận.'
)
CITATION_PATTERN = re.compile(r'\[Nguồn\s+\d+:[^\]]+\]')


class GenerationUnavailableError(RuntimeError):
    """No configured provider produced a valid grounded answer."""


class InvalidCitationError(ProviderError):
    """The generated answer omitted or invented source labels."""


@dataclass(frozen=True, slots=True)
class GenerationResult:
    answer: str
    provider: str
    model: str
    source_ids: tuple[str, ...]
    cited_source_ids: tuple[str, ...]
    fallback_used: bool
    status: Literal['answered', 'no_context']
    sources: tuple[StructuredSource, ...]


def _validate_citations(answer: str, prompt: GroundedPrompt) -> tuple[str, ...]:
    found = tuple(CITATION_PATTERN.findall(answer))
    if not found:
        if INSUFFICIENT_CONTEXT_MESSAGE in answer:
            return ()
        raise InvalidCitationError('generated answer does not contain a citation')

    allowed = dict(zip(prompt.citation_labels, prompt.source_ids, strict=True))
    cited: list[str] = []
    invented: list[str] = []

    for label in found:
        if label in allowed:
            cited.append(allowed[label])
        else:
            match = re.match(r'\[Nguồn\s+(\d+)', label)
            if match:
                idx = int(match.group(1))
                if 1 <= idx <= len(prompt.source_ids):
                    cited.append(prompt.source_ids[idx - 1])
                else:
                    invented.append(label)
            else:
                invented.append(label)

    if invented:
        raise InvalidCitationError('generated answer contains an unknown citation')
    return tuple(dict.fromkeys(cited))


class GenerationService:
    def __init__(
        self,
        primary: GenerationProvider,
        fallback: GenerationProvider | None = None,
        prompt_builder: GroundedPromptBuilder | None = None,
    ) -> None:
        self.primary = primary
        self.fallback = fallback
        self.prompt_builder = prompt_builder or GroundedPromptBuilder()

    def answer(
        self,
        question: str,
        chunks: Sequence[Mapping[str, Any]],
    ) -> GenerationResult:
        understand_query(question)
        if not chunks:
            LOGGER.info('Generation skipped because retrieval returned no usable context')
            return GenerationResult(
                answer=EMPTY_RETRIEVAL_MESSAGE,
                provider='none',
                model='none',
                source_ids=(),
                cited_source_ids=(),
                fallback_used=False,
                status='no_context',
                sources=(),
            )

        prompt = self.prompt_builder.build(question, chunks)
        providers = (self.primary,) if self.fallback is None else (self.primary, self.fallback)
        failures: list[Exception] = []

        for index, provider in enumerate(providers):
            try:
                response = provider.generate(prompt)
                cited = _validate_citations(response.text, prompt)
                LOGGER.info(
                    'Generation completed; provider=%s model=%s source_ids=%s cited_source_ids=%s fallback_used=%s',
                    response.provider,
                    response.model,
                    prompt.source_ids,
                    cited,
                    index > 0,
                )
                return GenerationResult(
                    answer=response.text,
                    provider=response.provider,
                    model=response.model,
                    source_ids=prompt.source_ids,
                    cited_source_ids=cited,
                    fallback_used=index > 0,
                    status='answered',
                    sources=build_structured_sources(chunks, cited),
                )
            except ProviderError as exc:
                failures.append(exc)
                LOGGER.warning(
                    'Generation provider failed; provider=%s model=%s fallback_available=%s error=%s',
                    provider.name,
                    provider.model,
                    index + 1 < len(providers),
                    exc,
                )

        raise GenerationUnavailableError(
            'No generation provider returned a valid grounded answer'
        ) from failures[-1]

    def answer_stream(
        self,
        question: str,
        chunks: Sequence[Mapping[str, Any]],
    ) -> Iterator[dict[str, Any]]:
        understand_query(question)
        if not chunks:
            LOGGER.info('Generation stream skipped because retrieval returned no usable context')
            yield {'type': 'token', 'content': EMPTY_RETRIEVAL_MESSAGE}
            yield {
                'type': 'done',
                'answer': EMPTY_RETRIEVAL_MESSAGE,
                'status': 'no_context',
                'provider': 'none',
                'model': 'none',
                'source_ids': [],
                'cited_source_ids': [],
                'fallback_used': False,
                'sources': [],
            }
            return

        prompt = self.prompt_builder.build(question, chunks)
        providers = (self.primary,) if self.fallback is None else (self.primary, self.fallback)
        failures: list[Exception] = []

        for index, provider in enumerate(providers):
            emitted_parts: list[str] = []
            try:
                stream_fn = getattr(provider, 'generate_stream', None)
                if stream_fn is not None:
                    for chunk in stream_fn(prompt):
                        if not chunk:
                            continue
                        emitted_parts.append(chunk)
                        yield {'type': 'token', 'content': chunk}
                else:
                    response = provider.generate(prompt)
                    emitted_parts.append(response.text)
                    yield {'type': 'token', 'content': response.text}

                full_text = ''.join(emitted_parts).strip()
                if not full_text:
                    raise ProviderError(f'{provider.name} returned an empty stream')

                try:
                    cited = _validate_citations(full_text, prompt)
                except InvalidCitationError:
                    # Answer text was already streamed to client; keep valid citations if any
                    cited = ()

                sources = [s.as_dict() for s in build_structured_sources(chunks, cited)]
                LOGGER.info(
                    'Generation stream completed; provider=%s model=%s source_ids=%s cited_source_ids=%s fallback_used=%s',
                    provider.name,
                    provider.model,
                    prompt.source_ids,
                    cited,
                    index > 0,
                )
                yield {
                    'type': 'done',
                    'answer': full_text,
                    'status': 'answered',
                    'provider': provider.name,
                    'model': provider.model,
                    'source_ids': list(prompt.source_ids),
                    'cited_source_ids': list(cited),
                    'fallback_used': index > 0,
                    'sources': sources,
                }
                return
            except ProviderError as exc:
                failures.append(exc)
                LOGGER.warning(
                    'Generation stream failed; provider=%s model=%s emitted=%s fallback_available=%s error=%s',
                    provider.name,
                    provider.model,
                    bool(emitted_parts),
                    index + 1 < len(providers),
                    exc,
                )
                if emitted_parts:
                    break

        raise GenerationUnavailableError(
            'No generation provider returned a valid grounded answer'
        ) from failures[-1]


def _provider(name: str, model: str, config: GenerationConfig) -> GenerationProvider:
    normalized = name.strip().lower()
    common = {
        'temperature': config.temperature,
        'max_output_tokens': config.max_output_tokens,
        'timeout_seconds': config.timeout_seconds,
    }
    if normalized == 'gemini':
        return GeminiProvider(model, **common)
    if normalized == 'ollama':
        return OllamaProvider(model, base_url=config.ollama_base_url, **common)
    raise ValueError(f'unsupported LLM provider: {name!r}')


def generation_service_from_env() -> GenerationService:
    config = GenerationConfig.from_env()
    primary = _provider(config.provider, config.model, config)
    fallback = None
    if config.fallback_enabled:
        fallback = _provider(config.fallback_provider, config.fallback_model, config)
    return GenerationService(primary, fallback)
