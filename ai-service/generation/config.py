"""Environment-backed generation configuration."""

from dataclasses import dataclass
import os


def _read_bool(value: str | None, default: bool) -> bool:
    if value is None:
        return default
    normalized = value.strip().lower()
    if normalized in {'1', 'true', 'yes', 'on'}:
        return True
    if normalized in {'0', 'false', 'no', 'off'}:
        return False
    raise ValueError(f'invalid boolean value: {value!r}')


@dataclass(frozen=True, slots=True)
class GenerationConfig:
    provider: str = 'gemini'
    model: str = 'gemini-3.8-flash'
    temperature: float = 0.2
    max_output_tokens: int = 1024
    timeout_seconds: float = 120.0
    fallback_enabled: bool = True
    fallback_provider: str = 'ollama'
    fallback_model: str = 'qwen2.5:7b'
    compare_provider: str = 'ollama'
    compare_model: str = 'gemma3:4b'
    ollama_base_url: str = 'http://localhost:11434'

    def __post_init__(self) -> None:
        if not self.provider.strip():
            raise ValueError('provider must not be empty')
        if not self.model.strip():
            raise ValueError('model must not be empty')
        if not 0 <= self.temperature <= 2:
            raise ValueError('temperature must be between 0 and 2')
        if self.max_output_tokens < 1:
            raise ValueError('max_output_tokens must be positive')
        if self.timeout_seconds <= 0:
            raise ValueError('timeout_seconds must be positive')
        if self.fallback_enabled and not self.fallback_provider.strip():
            raise ValueError('fallback_provider must not be empty')
        if self.fallback_enabled and not self.fallback_model.strip():
            raise ValueError('fallback_model must not be empty')
        if not self.compare_provider.strip():
            raise ValueError('compare_provider must not be empty')
        if not self.compare_model.strip():
            raise ValueError('compare_model must not be empty')

    @classmethod
    def from_env(cls) -> 'GenerationConfig':
        return cls(
            provider=os.getenv('LLM_PROVIDER', 'gemini'),
            model=os.getenv('LLM_MODEL', 'gemini-3.8-flash'),
            temperature=float(os.getenv('LLM_TEMPERATURE', '0.2')),
            max_output_tokens=int(os.getenv('LLM_MAX_OUTPUT_TOKENS', '1024')),
            timeout_seconds=float(os.getenv('LLM_TIMEOUT_SECONDS', '120')),
            fallback_enabled=_read_bool(os.getenv('LLM_FALLBACK_ENABLED'), True),
            fallback_provider=os.getenv('LLM_FALLBACK_PROVIDER', 'ollama'),
            fallback_model=os.getenv('LLM_FALLBACK_MODEL', 'qwen2.5:7b'),
            compare_provider=os.getenv('LLM_COMPARE_PROVIDER', 'ollama'),
            compare_model=os.getenv('LLM_COMPARE_MODEL', 'gemma3:4b'),
            ollama_base_url=os.getenv('OLLAMA_BASE_URL', 'http://localhost:11434'),
        )
