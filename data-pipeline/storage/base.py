"""Storage-neutral embedded chunk records."""

from dataclasses import dataclass
from typing import Any, Protocol, Sequence

import numpy as np


@dataclass(frozen=True)
class EmbeddedChunk:
    chunk: dict[str, Any]
    embedding: np.ndarray


class ChunkStore(Protocol):
    def begin_run(self, model_name: str, dimension: int) -> str: ...

    def upsert_batch(self, rows: Sequence[EmbeddedChunk], run_id: str) -> None: ...

    def finalize_run(self, run_id: str, chunk_count: int) -> dict[str, int]: ...

    def abort_run(self) -> None: ...
