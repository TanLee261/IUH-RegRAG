"""HTTP API exposing the complete IUH-RegRAG answer pipeline."""

from contextlib import asynccontextmanager
from pathlib import Path
import sys
from threading import Lock
from typing import Annotated, Any, Callable, Protocol

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, StringConstraints

from generation import GenerationUnavailableError, generation_service_from_env
from rerank import RerankedRetriever, Reranker, RerankerConfig
from retrieval import Retriever


PIPELINE_DIR = Path(__file__).resolve().parents[1] / 'data-pipeline'
if str(PIPELINE_DIR) not in sys.path:
    sys.path.insert(0, str(PIPELINE_DIR))

from embed import Embedder, EmbeddingConfig  # noqa: E402
from storage.hybrid import HybridSearcher  # noqa: E402
from storage.postgres import PostgresChunkStore, database_dsn_from_env  # noqa: E402


Question = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=2000)]


class AnswerRequest(BaseModel):
    question: Question


class AnswerPipeline(Protocol):
    def answer(self, question: str) -> dict[str, Any]: ...

    def close(self) -> None: ...


class RagAnswerPipeline:
    """Long-lived models and database connection shared by API requests."""

    def __init__(self) -> None:
        self.store = PostgresChunkStore(database_dsn_from_env())
        embedder = Embedder(EmbeddingConfig.from_env())
        retriever = Retriever(HybridSearcher(self.store, embedder))
        self.retriever = RerankedRetriever(
            retriever, Reranker(RerankerConfig.from_env())
        )
        self.generator = generation_service_from_env()
        self.lock = Lock()

    def answer(self, question: str) -> dict[str, Any]:
        with self.lock:
            chunks = self.retriever.retrieve(question, top_k=3, retrieval_k=6)
            result = self.generator.answer(question, chunks)
        return {
            'answer': result.answer,
            'status': result.status,
            'provider': result.provider,
            'model': result.model,
            'source_ids': result.source_ids,
            'cited_source_ids': result.cited_source_ids,
            'fallback_used': result.fallback_used,
            'sources': [source.as_dict() for source in result.sources],
        }

    def close(self) -> None:
        self.store.close()


def create_app(
    pipeline_factory: Callable[[], AnswerPipeline] = RagAnswerPipeline,
) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.pipeline = pipeline_factory()
        try:
            yield
        finally:
            app.state.pipeline.close()

    app = FastAPI(title='IUH-RegRAG AI Service', lifespan=lifespan)

    @app.get('/health')
    def health() -> dict[str, str]:
        return {'status': 'ok'}

    @app.post('/answer')
    def answer(payload: AnswerRequest, request: Request) -> dict[str, Any]:
        try:
            return request.app.state.pipeline.answer(payload.question)
        except GenerationUnavailableError as exc:
            raise HTTPException(status_code=502, detail='Generation service unavailable') from exc

    return app


app = create_app()
