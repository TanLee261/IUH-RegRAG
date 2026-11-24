"""Grounded answer generation components."""

from .prompt import ChatMessage, GroundedPrompt, GroundedPromptBuilder, PromptConfig
from .config import GenerationConfig
from .providers import GeminiProvider, OllamaProvider, ProviderError, ProviderResponse
from .service import (
    EMPTY_RETRIEVAL_MESSAGE,
    GenerationResult,
    GenerationService,
    GenerationUnavailableError,
    generation_service_from_env,
)
from .sources import StructuredSource, build_structured_sources

__all__ = [
    'ChatMessage',
    'EMPTY_RETRIEVAL_MESSAGE',
    'GeminiProvider',
    'GenerationConfig',
    'GenerationResult',
    'GenerationService',
    'GenerationUnavailableError',
    'GroundedPrompt',
    'GroundedPromptBuilder',
    'OllamaProvider',
    'PromptConfig',
    'ProviderError',
    'ProviderResponse',
    'StructuredSource',
    'build_structured_sources',
    'generation_service_from_env',
]
