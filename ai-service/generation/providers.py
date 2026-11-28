"""Provider adapters for grounded text generation."""

from dataclasses import dataclass
import json
import os
from typing import Any, Callable, Iterator, Protocol
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from .prompt import GroundedPrompt


class ProviderError(RuntimeError):
    """A provider could not produce a usable answer."""


@dataclass(frozen=True, slots=True)
class ProviderResponse:
    text: str
    provider: str
    model: str


class GenerationProvider(Protocol):
    @property
    def name(self) -> str: ...

    @property
    def model(self) -> str: ...

    def generate(self, prompt: GroundedPrompt) -> ProviderResponse: ...

    def generate_stream(self, prompt: GroundedPrompt) -> Iterator[str]: ...


class GeminiProvider:
    """Gemini Interactions API adapter using the official Google Gen AI SDK."""

    def __init__(
        self,
        model: str,
        *,
        temperature: float,
        max_output_tokens: int,
        timeout_seconds: float,
        api_key: str | None = None,
        client: Any | None = None,
    ) -> None:
        self._model = model
        self._temperature = temperature
        self._max_output_tokens = max_output_tokens
        self._timeout_seconds = timeout_seconds
        self._api_key = api_key
        self._client = client

    @property
    def name(self) -> str:
        return 'gemini'

    @property
    def model(self) -> str:
        return self._model

    def _get_client(self) -> Any:
        if self._client is not None:
            return self._client
        key = (
            self._api_key
            or os.getenv('GEMINI_API_KEY')
            or os.getenv('GOOGLE_API_KEY')
            or os.getenv('LLM_API_KEY')
        )
        if not key:
            raise ProviderError('Gemini API key is not configured')
        try:
            from google import genai
        except ImportError as exc:
            raise ProviderError(
                'google-genai is not installed; install ai-service/requirements.txt'
            ) from exc
        self._client = genai.Client(api_key=key)
        return self._client

    def generate(self, prompt: GroundedPrompt) -> ProviderResponse:
        system, user = prompt.messages
        try:
            interaction = self._get_client().interactions.create(
                model=self._model,
                system_instruction=system.content,
                input=user.content,
                generation_config={
                    'temperature': self._temperature,
                    'max_output_tokens': self._max_output_tokens,
                },
                store=False,
                timeout=self._timeout_seconds,
            )
            text = str(interaction.output_text or '').strip()
        except ProviderError:
            raise
        except Exception as exc:
            raise ProviderError('Gemini request failed') from exc
        if not text:
            raise ProviderError('Gemini returned an empty response')
        return ProviderResponse(text=text, provider=self.name, model=self.model)

    def generate_stream(self, prompt: GroundedPrompt) -> Iterator[str]:
        system, user = prompt.messages
        try:
            stream = self._get_client().interactions.create(
                model=self._model,
                system_instruction=system.content,
                input=user.content,
                generation_config={
                    'temperature': self._temperature,
                    'max_output_tokens': self._max_output_tokens,
                },
                store=False,
                stream=True,
                timeout=self._timeout_seconds,
            )
            yielded = False
            for event in stream:
                event_type = getattr(event, 'event_type', None)
                if event_type == 'error':
                    raise ProviderError('Gemini stream failed')
                if event_type == 'step.delta':
                    delta = getattr(event, 'delta', None)
                    if getattr(delta, 'type', None) == 'text':
                        text = getattr(delta, 'text', None)
                        if text:
                            yielded = True
                            yield str(text)
            if not yielded:
                raise ProviderError('Gemini returned an empty response')
        except ProviderError:
            raise
        except Exception as exc:
            raise ProviderError('Gemini stream request failed') from exc


class OllamaProvider:
    """Local Ollama chat adapter used as a configurable provider or fallback."""

    def __init__(
        self,
        model: str,
        *,
        base_url: str,
        temperature: float,
        max_output_tokens: int,
        timeout_seconds: float,
        opener: Callable[..., Any] = urlopen,
    ) -> None:
        self._model = model
        self._base_url = base_url.rstrip('/')
        self._temperature = temperature
        self._max_output_tokens = max_output_tokens
        self._timeout_seconds = timeout_seconds
        self._opener = opener

    @property
    def name(self) -> str:
        return 'ollama'

    @property
    def model(self) -> str:
        return self._model

    def generate(self, prompt: GroundedPrompt) -> ProviderResponse:
        payload = json.dumps(
            {
                'model': self._model,
                'messages': prompt.as_chat_messages(),
                'stream': False,
                'options': {
                    'temperature': self._temperature,
                    'num_predict': self._max_output_tokens,
                },
            }
        ).encode('utf-8')
        request = Request(
            f'{self._base_url}/api/chat',
            data=payload,
            headers={'Content-Type': 'application/json'},
            method='POST',
        )
        try:
            with self._opener(request, timeout=self._timeout_seconds) as response:
                body = json.loads(response.read().decode('utf-8'))
            text = str(body.get('message', {}).get('content', '')).strip()
        except (HTTPError, URLError, TimeoutError, OSError, ValueError, json.JSONDecodeError) as exc:
            raise ProviderError('Ollama request failed') from exc
        if not text:
            raise ProviderError('Ollama returned an empty response')
        return ProviderResponse(text=text, provider=self.name, model=self.model)

    def generate_stream(self, prompt: GroundedPrompt) -> Iterator[str]:
        payload = json.dumps(
            {
                'model': self._model,
                'messages': prompt.as_chat_messages(),
                'stream': True,
                'options': {
                    'temperature': self._temperature,
                    'num_predict': self._max_output_tokens,
                },
            }
        ).encode('utf-8')
        request = Request(
            f'{self._base_url}/api/chat',
            data=payload,
            headers={'Content-Type': 'application/json'},
            method='POST',
        )
        try:
            yielded = False
            with self._opener(request, timeout=self._timeout_seconds) as response:
                for raw_line in response:
                    line = raw_line.decode('utf-8').strip()
                    if not line:
                        continue
                    body = json.loads(line)
                    chunk = str(body.get('message', {}).get('content', ''))
                    if chunk:
                        yielded = True
                        yield chunk
                    if body.get('done'):
                        break
            if not yielded:
                raise ProviderError('Ollama returned an empty response')
        except ProviderError:
            raise
        except (HTTPError, URLError, TimeoutError, OSError, ValueError, json.JSONDecodeError) as exc:
            raise ProviderError('Ollama stream request failed') from exc
