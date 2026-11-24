"""Environment-backed reranker configuration."""

from dataclasses import dataclass
import os


@dataclass(frozen=True, slots=True)
class RerankerConfig:
    model_name: str = 'BAAI/bge-reranker-v2-m3'
    max_tokens: int = 1024
    batch_size: int = 8
    device: str = 'auto'
    min_score: float = 0.05

    def __post_init__(self) -> None:
        if not self.model_name.strip():
            raise ValueError('model_name must not be empty')
        if self.max_tokens < 1 or self.batch_size < 1:
            raise ValueError('max_tokens and batch_size must be positive')
        if self.device not in {'auto', 'cpu', 'mps', 'cuda'}:
            raise ValueError('device must be auto, cpu, mps or cuda')
        if not 0 <= self.min_score <= 1:
            raise ValueError('min_score must be between 0 and 1')

    @classmethod
    def from_env(cls) -> 'RerankerConfig':
        defaults = cls()
        return cls(
            model_name=os.getenv('RERANK_MODEL', defaults.model_name),
            max_tokens=int(os.getenv('RERANK_MAX_TOKENS', defaults.max_tokens)),
            batch_size=int(os.getenv('RERANK_BATCH_SIZE', defaults.batch_size)),
            device=os.getenv('RERANK_DEVICE', defaults.device).lower(),
            min_score=float(os.getenv('RERANK_MIN_SCORE', defaults.min_score)),
        )
