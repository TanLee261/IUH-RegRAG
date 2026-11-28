"""HTTP API exposing the complete IUH-RegRAG answer pipeline."""

from contextlib import asynccontextmanager
import json
from pathlib import Path
import sys
from threading import Lock
from typing import Annotated, Any, Callable, Iterator, Protocol

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse
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

    def answer_stream(self, question: str) -> Iterator[dict[str, Any]]: ...

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

    def answer_stream(self, question: str) -> Iterator[dict[str, Any]]:
        with self.lock:
            chunks = self.retriever.retrieve(question, top_k=3, retrieval_k=6)
        yield from self.generator.answer_stream(question, chunks)

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

    @app.post('/answer/stream')
    def answer_stream(payload: AnswerRequest, request: Request) -> StreamingResponse:
        pipeline = request.app.state.pipeline

        def event_stream() -> Iterator[str]:
            try:
                for event in pipeline.answer_stream(payload.question):
                    yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
            except GenerationUnavailableError:
                err = {'type': 'error', 'message': 'Generation service unavailable'}
                yield f"data: {json.dumps(err, ensure_ascii=False)}\n\n"

        return StreamingResponse(
            event_stream(),
            media_type='text/event-stream',
            headers={
                'Cache-Control': 'no-cache',
                'Connection': 'keep-alive',
                'X-Accel-Buffering': 'no',
            },
        )

    return app


app = create_app()
