# IUH-RegRAG Architecture

IUH-RegRAG duoc to chuc theo kieu monorepo de frontend, backend, AI service,
data pipeline va tai lieu co chung mot noi quan ly.

```text
IUH-RegRAG/
|-- frontend/                 # ReactJS
|-- backend/                  # Spring Boot
|   |-- pom.xml               # Maven build, Java 17
|   |-- mvnw                  # Maven Wrapper
|   `-- src/
|       |-- main/java/vn/edu/iuh/regrag/
|       |-- main/resources/
|       `-- test/java/vn/edu/iuh/regrag/
|-- ai-service/
|   |-- retrieval/
|   |-- rerank/
|   `-- generation/
|-- data-pipeline/
|   |-- ingest/
|   |-- chunk/                # Chunk theo Dieu
|   `-- embed/
|-- docker-compose.yml        # Se duoc tao o task Docker cua Phase 0
`-- docs/
    `-- architecture.md
```

## Trach nhiem cac khoi

- `frontend/`: giao dien React cho chat, citation va quan tri.
- `backend/`: REST API Spring Boot 3.5.7, Java 17, package base `vn.edu.iuh.regrag`.
- `ai-service/`: retrieval, reranking va generation cua RAG.
- `data-pipeline/`: nap, chia chunk theo Dieu/Khoan va tao embedding cho quy che.
- `docs/`: tai lieu kien truc va huong dan ky thuat.

Backend da co Maven build, application entry point va context smoke test. Cac
module nghiep vu va `docker-compose.yml` se duoc bo sung trong cac phase/task
tuong ung.
