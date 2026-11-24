# =========================================================================
# IUH-RegRAG AI Service (FastAPI + Hybrid Search + Reranker + LLM)
# =========================================================================
FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY ai-service/requirements.txt ./ai-requirements.txt
COPY data-pipeline/requirements.txt ./pipeline-requirements.txt

# Install dependencies (CPU torch to keep container slim)
RUN pip install --no-cache-dir --extra-index-url https://download.pytorch.org/whl/cpu torch && \
    pip install --no-cache-dir -r ai-requirements.txt -r pipeline-requirements.txt

# Copy application code
COPY infrastructure/ ./infrastructure/
COPY data-pipeline/ ./data-pipeline/
COPY ai-service/ ./ai-service/

WORKDIR /app/ai-service

EXPOSE 8000

ENV PYTHONPATH="/app/data-pipeline:/app/ai-service:${PYTHONPATH}" \
    HF_HOME="/app/.cache/huggingface" \
    PYTHONUNBUFFERED=1

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8000/health || exit 1

CMD ["uvicorn", "api:app", "--host", "0.0.0.0", "--port", "8000"]
