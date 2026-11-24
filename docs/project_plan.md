# IUH-RegRAG - Project Plan / Roadmap

## Muc dich tai lieu

Tai lieu nay la roadmap tong the cua du an **IUH-RegRAG** (chatbot RAG hoi dap quy che Truong Dai hoc Cong nghiep TP.HCM).

Muc dich:
- Theo doi tien do theo tung phase.
- Chuyen thanh Trello / task board.
- Ban giao cho thanh vien khac.
- Tiep tuc phat trien o cac phien sau ma khong mat ngu canh.

**Nguon xac minh trang thai:**
- Cau truc thu muc workspace hien tai.
- Backend Spring Boot scaffold va ket qua Maven test/package.
- Tai lieu thiet ke kien truc (diagram ITLR-style) va trao doi yeu cau trong chat.
- Moi checkbox `[x]` chi duoc danh khi co bang chung trong source code hoac test chay duoc.

---

## Danh sach Phase

| Phase | Ten | Muc tieu ngan |
|-------|-----|----------------|
| 0 | Khoi tao du an & infrastructure | Repo, Docker, CI skeleton |
| 1 | Data pipeline & knowledge base | Thu thap quy che IUH, chunk, embed, vector DB |
| 2 | Database & entity | PostgreSQL user/chat/feedback schema |
| 3 | Authentication & security | JWT, role STUDENT/ADMIN |
| 4 | AI Service (RAG core) | Retrieval, rerank, generation |
| 5 | Backend API nghiep vu | Chat, document, feedback REST |
| 6 | Frontend integration | React UI chat + citation |
| 7 | Testing | Unit, integration, RAG eval |
| 8 | Deployment & demo | Docker compose, VPS/cloud, demo |
| 9 | Documentation | README, API doc, bao cao |

---

## Current Progress Summary

| Hang muc | Trang thai thuc te |
|----------|-------------------|
| Source code backend (Spring Boot) | **Da khoi tao scaffold** |
| Source code frontend (React) | **Chua co** |
| AI Service / RAG pipeline | **Chua co** |
| Data quy che IUH da ingest | **Chua co** |
| Database migration / entity | **Chua co** |
| Auth JWT | **Chua co** |
| Docker / CI/CD | **Chua co** |
| Test suite | **Co backend context smoke test** |
| README / docs ky thuat | **Co project plan va architecture** |
| Thiet ke kien truc (diagram) | Da thong nhat huong fullstack RAG theo mau ITLR |

**Tong tien do uoc luong:** Da hoan thanh 2 task Phase 0; cac phase nghiep vu chua bat dau.

**Diem chua chac chan:** Ten module AI Service co the la package trong Spring Boot hoac service rieng; chua chot trong code.

---

## Phase 0 - Khoi tao du an & infrastructure

**Muc dich:** Tao khung repo, cong cu build, Docker skeleton de cac phase sau lam viec dong nhat.

**Ket qua mong doi:**
- Repo co cau truc `frontend/`, `backend/`, `ai-service/` (hoac module tuong duong), `data-pipeline/`, `docs/`.
- `docker-compose.yml` chay duoc Postgres (+ vector extension neu dung pgvector).
- README toi thieu mo ta cach clone va chay local.

### Tasks

- [x] Tao cau truc monorepo theo kien truc IUH-RegRAG (frontend, backend, ai-service, data-pipeline, docs)
  - Dung de toan team biet file dat o dau.
  - Anh huong truc tiep toi CI, Docker va ban giao.

- [x] Khoi tao backend Spring Boot (Java 17+, Maven/Gradle) voi package base
  - Nen tang cho API, security, integration RAG.
  - Phase 3-5 phu thuoc vao project nay.
  - Da tao Spring Boot 3.5.7, Maven Wrapper, Java 17, package base `vn.edu.iuh.regrag`.
  - Dependencies scaffold: Web, Validation, Actuator va Test; context smoke test da pass.

- [x] ~~Khoi tao frontend React (Vite + TypeScript) voi routing co ban~~
  - Cho UI chat va trang admin ve sau.
  - Tranh scaffold lai giua chung.

- [x] ~~Viet `docker-compose.yml`: PostgreSQL, (optional) Chroma/Qdrant, MinIO hoac volume local~~
  - Dam bao moi truong local giong staging.
  - Can cho Phase 1 (vector) va Phase 2 (user DB).

- [x] ~~Cau hinh bien moi truong mau (`.env.example`): DB URL, JWT secret, LLM API key~~
  - Tranh hard-code secret trong source.
  - Bat buoc truoc khi goi LLM o Phase 4.

- [x] ~~Them GitHub Actions skeleton: build backend + frontend (chua bat buoc test pass)~~
  - Tao nen CI/CD o Phase 8.
  - Phat hien som loi compile.

- [x] ~~Viet README.md: gioi thieu du an, stack, huong chay local~~
  - Onboarding thanh vien moi.
  - Co so cho Phase 9.

---

## Phase 1 - Data pipeline & knowledge base quy che IUH

**Muc dich:** Dua van ban quy che IUH vao he thong duoi dang chunk + embedding + metadata de retrieval.

**Ket qua mong doi:**
- Co it nhat 2-3 van ban quy che (dao tao tin chi, cam nang / tot nghiep, dang ky hoc phan hoac diem ren luyen).
- Chunk theo Dieu/Khoan, co metadata `dieu`, `khoan`, `so_qd`, `nam`, `source`.
- Vector DB co index; script ingest chay lai duoc.

### Tasks

- [x] ~~Thu thap tai lieu goc: PDF/HTML quy che dao tao IUH, cam nang nguoi hoc, thong bao PDT~~
  - Day la nguon truth cua chatbot; thieu data thi RAG vo nghia.
  - Anh huong do chinh xac toan Phase 4-6.

- [x] ~~Viet parser PDF/DOCX/TXT + clean text (bo header/footer rac)~~
  - Chuan hoa input truoc khi chunk.
  - Giam nhiễu embedding.

- [x] ~~Thiet ke schema chunk: id, text, title, source, dieu, khoan, year, tags~~
  - Dam bao citation luc tra loi co Dieu/Khoan.
  - Bat buoc de Generation khong bia nguon.

- [x] ~~Implement chunking theo Dieu/Khoan (khong cat ngang cau phap ly)~~
  - Quy che can giu nguyen y nghia dieu khoan.
  - Chat luong retrieval phu thuoc buoc nay.

- [x] ~~Chon va cau hinh embedding model (vd. multilingual-e5 hoac API embedding)~~
  - Bien text thanh vector.
  - Can cho vector search o Phase 4.

- [x] ~~Store embeddings vao Vector DB (pgvector / Chroma / FAISS) kem metadata~~
  - Noi luu tri thuc de retrieve.
  - Backend RAG se query kho nay.

- [x] ~~Script CLI `ingest` chay lai toan bo pipeline khi them van ban moi~~
  - Admin/ops khong can sua code khi cap nhat quy che.
  - Lien quan Document API Phase 5.

- [x] ~~(Optional) Hybrid index BM25 + vector cho cu phap so / ma dieu~~
  - Cau hoi kieu "Dieu 19", "12 tin chi" can keyword.
  - Cai thien hit rate truoc khi rerank.

---

## Phase 2 - Database & entity

**Muc dich:** Luu user, session chat, feedback, metadata document (khong tinh vector) tren PostgreSQL.

**Ket qua mong doi:**
- Migration tao bang `users`, `chat_sessions`, `messages`, `feedback`, `documents`.
- Entity/repository Spring Data JPA (hoac tuong duong) map dung schema.

### Tasks

- [x] ~~Thiet ke ERD: User, ChatSession, Message, Feedback, Document~~ ([docs/phase-2-erd.md](phase-2-erd.md))
  - Tach ro du lieu nghiep vu va vector store.
  - Lam goc cho API Phase 5.

- [x] ~~Tao migration Flyway/Liquibase (hoac schema.sql) cho PostgreSQL~~ ([V1__business_entities.sql](../backend/src/main/resources/db/migration/V1__business_entities.sql))
  - Version hoa schema, deploy an toan.
  - Can truoc Auth va Chat API.

- [x] ~~Implement entity + repository: User (id, email, role, password_hash)~~ ([User.java](../backend/src/main/java/vn/edu/iuh/regrag/user/User.java), [UserRepository.java](../backend/src/main/java/vn/edu/iuh/regrag/user/UserRepository.java))
  - Nen cho dang nhap va phan quyen.
  - Phase 3 phu thuoc.

- [x] ~~Implement entity Message / ChatSession (luu history, citation JSON)~~ ([chat/](../backend/src/main/java/vn/edu/iuh/regrag/chat/))
  - Phuc vu lich su chat tren UI va audit.
  - Feedback se reference message_id.

- [x] ~~Implement entity Feedback (message_id, rating, comment)~~ ([feedback/](../backend/src/main/java/vn/edu/iuh/regrag/feedback/))
  - Thu thap tin hieu de cai thien RAG.
  - Dung o Phase 5 Feedback API.

- [x] ~~Implement entity Document (ten file, source, version, status ingest)~~ ([document/](../backend/src/main/java/vn/edu/iuh/regrag/document/))
  - Admin quan ly kho quy che.
  - Dong bo voi file storage + vector ids.

---

## Phase 3 - Authentication & security

**Muc dich:** Bao ve API bang JWT; phan role STUDENT / ADMIN.

**Ket qua mong doi:**
- Dang ky/dang nhap tra access + refresh token.
- Endpoint chat co the public rate-limit hoac bat buoc auth (chot trong config).
- Chi ADMIN upload document / xem log day du.

### Tasks

- [x] ~~Cau hinh Spring Security + JWT filter~~ ([SecurityConfig.java](../backend/src/main/java/vn/edu/iuh/regrag/auth/SecurityConfig.java))
  - Chuan hoa auth cho moi REST API.
  - Tranh expose API noi bo.

- [x] ~~API `POST /api/auth/register` va `POST /api/auth/login`~~ ([AuthController.java](../backend/src/main/java/vn/edu/iuh/regrag/auth/AuthController.java))
  - Tao tai khoan SV/demo va lay token.
  - Frontend login form can endpoint nay.

- [x] ~~API refresh token + logout (invalidate neu co blacklist)~~ ([V2__auth_tokens.sql](../backend/src/main/resources/db/migration/V2__auth_tokens.sql))
  - UX dang nhap lau dai an toan hon.
  - Giam login lai tren UI.

- [x] ~~Role-based access: STUDENT vs ADMIN tren Document API~~ ([SecurityConfig.java](../backend/src/main/java/vn/edu/iuh/regrag/auth/SecurityConfig.java))
  - Chi admin ingest quy che.
  - Bao ve toan ven knowledge base.

- [x] ~~Rate limit endpoint chat (theo IP hoac user id)~~ ([ChatRateLimitFilter.java](../backend/src/main/java/vn/edu/iuh/regrag/auth/ChatRateLimitFilter.java))
  - Giam luong LLM API, chong spam.
  - Quan trong khi demo public.

- [x] ~~Khong hard-code secret; load JWT_SECRET, LLM_API_KEY tu env~~ ([application-db.yaml](../backend/src/main/resources/application-db.yaml))
  - An toan deploy.
  - Bat buoc truoc Phase 8.

---

## Phase 4 - AI Service (RAG core)

**Muc dich:** Hien thuc 3 tang Retrieval → Reranking → Generation nhu kien truc da chot.

**Ket qua mong doi:**
- Ham `answer(question) -> { answer, sources[] }`.
- Prompt chi tra loi theo context; temperature thap.
- Log chunk_ids da dung cho moi cau tra loi.

### Tasks

- [x] ~~Query understanding: normalize tieng Viet, giu tu khoa quy che~~ ([query_understanding.py](../ai-service/retrieval/query_understanding.py))
  - Tang recall khi user go tat / khong dau.
  - Dau vao cho embedding search.

- [x] ~~Retrieval: embed query + top-k vector search (va BM25 neu co)~~ ([retriever.py](../ai-service/retrieval/retriever.py))
  - Lay dung doan Dieu/Khoan lien quan.
  - La dau vao bat buoc cua Generation.

- [x] ~~Reranking: cross-encoder hoac score lai top-k → giu 3-5 chunk tot nhat~~ ([reranker.py](../ai-service/rerank/reranker.py))
  - Giam nhiễu context, giam hallucination.
  - Cai thien citation chinh xac.

- [x] ~~Prompt template grounded: chi dung context, bat buoc trich dan Dieu/Khoan/ten van ban~~ ([prompt.py](../ai-service/generation/prompt.py))
  - Dam bao tra loi dung tinh chat phap ly.
  - Core chat luong san pham.

- [x] ~~Generation: goi LLM (Gemini primary, Ollama Qwen2.5:7B fallback; temperature 0.1-0.3)~~ ([service.py](../ai-service/generation/service.py))
  - ~~Sinh cau tra loi tieng Viet co citation.~~
  - ~~Co the doi model bang config, khong doi pipeline.~~

- [x] ~~Xu ly empty retrieval: tra message "Khong tim thay trong quy che da nap" + goi y PDT~~ ([service.py](../ai-service/generation/service.py))
  - Tranh LLM bia khi khong co context.
  - UX trung thuc voi nguoi dung.

- [x] ~~Tra ve structured sources: { title, dieu, khoan, source, snippet, score }~~ ([sources.py](../ai-service/generation/sources.py))
  - Frontend hien panel nguon.
  - Phuc vu danh gia faithfulness.

---

## Phase 5 - Backend API nghiep vu

**Muc dich:** Expose REST API orchestration: auth, chat RAG, document, feedback, system.

**Ket qua mong doi:**
- `POST /api/chat` nhan question, tra answer + sources, luu message.
- Admin CRUD document + trigger ingest.
- Feedback API luu rating.

### Tasks

- [x] ~~`POST /api/chat` — validate input, goi AI Service, luu session/message, tra JSON~~ ([ChatController.java](../backend/src/main/java/vn/edu/iuh/regrag/chat/ChatController.java))
  - API chinh cua san pham.
  - Frontend chat phu thuoc endpoint nay.

- [x] `GET /api/chat/sessions` va `GET /api/chat/sessions/{id}` — lich su chat ([ChatController.java](../backend/src/main/java/vn/edu/iuh/regrag/chat/ChatController.java))
  - Hien thi history tren UI.
  - Can auth de khong xem chat nguoi khac.

- [x] Document API: upload, list, delete (ADMIN); metadata status ingest ([DocumentController.java](../backend/src/main/java/vn/edu/iuh/regrag/document/DocumentController.java))
  - Cap nhat quy che khi truong ban hanh ban moi.
  - Noi Phase 1 pipeline voi van hanh.

- [x] `POST /api/feedback` — rating huu ich / khong dung + comment ([FeedbackController.java](../backend/src/main/java/vn/edu/iuh/regrag/feedback/FeedbackController.java))
  - Thu thap tin hieu chat luong.
  - Phuc vu cai tien retrieval sau nay.

- [x] Recommendation nhe: `GET /api/suggestions` — cau hoi goi y theo topic (canh bao, tot nghiep, thang diem...) ([SuggestionController.java](../backend/src/main/java/vn/edu/iuh/regrag/suggestion/SuggestionController.java))
  - Tuong duong "Recommendations" trong diagram mau, nhung domain quy che.
  - Tang ty le user biet hoi gi.

- [x] System API: health, version, danh sach van ban dang active trong vector DB ([SystemController.java](../backend/src/main/java/vn/edu/iuh/regrag/system/SystemController.java))
  - Monitoring va demo hoi dong.
  - CI/CD healthcheck.

- [x] Xu ly loi dong nhat: 400 validation, 401/403, 429 rate limit, 502 LLM down ([GlobalExceptionHandler.java](../backend/src/main/java/vn/edu/iuh/regrag/common/GlobalExceptionHandler.java))
  - UI hien thong bao ro.
  - Tranh stack trace lo ra client.

---

## Phase 6 - Frontend integration

**Muc dich:** React UI cho sinh vien chat + xem citation; admin co the quan ly tai lieu (toi thieu).

**Ket qua mong doi:**
- Trang chat hoat dong end-to-end voi backend that.
- Panel nguon hien Dieu/Khoan.
- Form login + (optional) trang admin document.

### Tasks

- [x] Layout app: header IUH-RegRAG, disclaimer tham khao quy che goc ([AppLayout.tsx](../frontend/src/components/AppLayout.tsx))
  - Dung muc dich phap ly / hoc vu.
  - Giam hieu nham chatbot = quyet dinh chinh thuc.

- [x] Trang Chat: input, stream/loading, bubble user/assistant ([ChatPage.tsx](../frontend/src/pages/ChatPage.tsx))
  - UX chinh cua du an.
  - Goi `POST /api/chat`.

- [x] Panel Sources: list citation tu response.sources ([SourceDrawer.tsx](../frontend/src/components/SourceDrawer.tsx))
  - Minh bach RAG, de bao cao/demo.
  - Khac biet chatbot thuong.

- [x] Chip cau hoi goi y (canh bao hoc tap, dieu kien tot nghiep, thang diem 10-4, hoc lai...) ([ChatPage.tsx](../frontend/src/pages/ChatPage.tsx))
  - Onboarding user moi.
  - Noi voi Suggestions API neu co.

- [x] Trang Login / luu JWT ([AuthModal.tsx](../frontend/src/components/AuthModal.tsx), [AuthContext.tsx](../frontend/src/context/AuthContext.tsx))
  - Bat buoc neu chat can auth.
  - Admin route can token.

- [x] Trang Admin: upload PDF quy che, xem trang thai ingest ([AdminPage.tsx](../frontend/src/pages/AdminPage.tsx))
  - Van hanh knowledge base khong can CLI.
  - Can role ADMIN.

- [x] Responsive mobile (~390px): chat dung duoc tren dien thoai ([styles.css](../frontend/src/styles.css))
  - SV hay hoi bang mobile.
  - Yeu cau chat luong demo.

- [x] Xu ly error/empty/loading state ro rang tren UI ([ChatPage.tsx](../frontend/src/pages/ChatPage.tsx))
  - Khong man hinh trang khi API fail.
  - Lien quan rate limit va LLM timeout.

---

## Phase 7 - Testing

**Muc dich:** Dam bao API, security va chat luong RAG do luong duoc.

**Ket qua mong doi:**
- Unit test service chinh pass.
- Integration test chat API voi mock LLM hoac fixture.
- Bo 20-30 cau hoi mau IUH de danh gia retrieval/answer.

### Tasks

- [x] ~~Unit test: chunking metadata, security filter, validation chat request~~
  - Bat regression som.
  - Chay trong CI.

- [x] ~~Integration test: login → chat → feedback (Testcontainers Postgres neu co)~~
  - Xac minh luong nghiep vu.
  - Gan voi hanh vi that.

- [x] ~~Mock AI Service trong test backend (khong goi LLM that moi lan CI)~~
  - CI on dinh, khong ton tien API.
  - Van test orchestration.

- [x] ~~Bo cau hoi gold: canh bao hoc tap, buoc thoi hoc, tot nghiep, thang diem, tin chi min/max~~
  - Do faithfulness va citation dung Dieu.
  - Bao cao do an can so lieu.

- [x] ~~Script eval don gian: hit rate top-k, co/khong citation, empty dung luc~~
  - Theo doi khi doi embedding/prompt.
  - Khong doan chat luong bang cam tinh.

---

## Phase 8 - Deployment & demo

**Muc dich:** Chay duoc tren moi truong demo (Docker Compose tren VPS hoac cloud).

**Ket qua mong doi:**
- `docker compose up` khoi dong full stack.
- URL public (hoac local tunnel) de bao cao.
- Bien moi truong production tach biet secret.

### Tasks

- [x] ~~Dockerfile multi-stage cho backend va frontend~~
  - Image gon, build lap lai duoc.
  - Can cho CI/CD.

- [x] ~~Hoan thien docker-compose: app + db + vector + volume data quy che~~
  - Demo 1 lenh.
  - Giam "chay duoc tren may toi".

- [x] ~~Cau hinh deploy VPS/cloud (env, reverse proxy, HTTPS neu public)~~
  - Demo on dinh qua Cloudflare Quick Tunnel (cloudflared Docker service) + Nginx reverse proxy.
  - Ho tro URL HTTPS public hop le, CORS dynamic pattern va script quan ly `scripts/tunnel.sh`.
  - Phase cuoi roadmap.

- [x] ~~Seed data: 1 admin + sample chunks quy che de demo ngay~~
  - 1.543 chunks vector quy che dao tao da nap san trong PostgreSQL + pgvector.
  - Tai khoan ADMIN duy nhat `admin@iuh.edu.vn` san sang demo, khoa logic dang ky admin duplicate.

- [x] ~~Kiem tra health endpoint va log loi LLM/DB khi deploy~~
  - Da xac thuc /actuator/health (Spring Boot), /health (AI Service) va pipeline chat qua HTTPS.
  - Debug nhanh luc demo qua `./scripts/tunnel.sh logs`.

---

## Phase 9 - Documentation

**Muc dich:** Tai lieu du de bao cao do an va ban giao.

**Ket qua mong doi:**
- README day du: kien truc, chay local, bien moi truong, vi du API.
- Mo ta pipeline RAG va cach cap nhat quy che.
- File plan nay duoc cap nhat theo tien do that.

### Tasks

- [x] Tao `docs/project_plan.md` (file nay) voi phase, task, priority
  - Roadmap theo doi va ban giao.
  - Da tao dua tren kien truc thiet ke; chua co source de danh dau task code.

- [x] ~~Cap nhat README: architecture overview, stack, quick start~~
  - Onboarding va bao cao.
  - Lam sau khi co scaffold Phase 0.

- [x] ~~Viet API contract (OpenAPI/Swagger hoac markdown): chat, auth, feedback, document~~
  - Frontend va tester khong doan request/response.
  - Sau khi Phase 5 on dinh.

- [x] ~~Mo ta data sources IUH da dung + version quy che (so QD, ngay)~~
  - Minh bach pham vi phap ly cua chatbot.
  - Tranh hieu nham "phu toan bo truong".

- [x] ~~Huong dan ingest van ban moi (admin/CLI)~~
  - Van hanh sau demo.
  - Gan Phase 1 va 5.

- [x] ~~Ghi chu model LLM da chon, temperature, han che cost~~
  - Bao cao do an va reproduce ket qua.
  - Phase 4.

---

## Priority goi y

| Muc | Y nghia | Phase/task uu tien |
|-----|---------|-------------------|
| **P0** | Khong co thi khong demo RAG duoc | Phase 0 scaffold; Phase 1 ingest + vector; Phase 4 RAG core; Phase 5 `POST /api/chat`; Phase 6 chat UI + sources |
| **P1** | Can cho do an day du / bao cao | Phase 2 DB message; Phase 3 JWT; Phase 7 bo cau hoi gold; Phase 8 docker demo; Phase 9 README + API doc |
| **P2** | Tot neu co thoi gian | Rerank; hybrid BM25; Feedback API; Admin upload; Suggestions API |
| **P3** | Mo rong | Monitoring dashboard; refresh token blacklist; multi-version quy che theo khoa; CI full eval |

---

## Milestones

| Milestone | Dieu kien dat | Phase lien quan |
|-----------|---------------|-----------------|
| **M0 - Repo san sang** | Co structure, docker-compose DB, README toi thieu | Phase 0 |
| **M1 - RAG CLI** | Hoi duoc 1 cau tren CLI voi citation tu quy che IUH | Phase 1 + 4 |
| **M2 - API Chat** | `POST /api/chat` tra answer + sources, luu DB | Phase 2 + 5 |
| **M3 - UI Demo** | React chat end-to-end, hien nguon | Phase 6 |
| **M4 - Bao cao** | Auth, test, docker demo, tai lieu | Phase 3, 7, 8, 9 |

---

## Trang thai hien tai

- **Giai doan:** Khoi tao du an va infrastructure (Phase 0).
- **Code ung dung:** Backend Spring Boot scaffold da khoi tao; frontend va AI service chua co source.
- **Quyet dinh da chot trong thiet ke (chua implement):**
  - Ten: IUH-RegRAG
  - Frontend: ReactJS
  - Backend: Spring Boot REST
  - Auth: JWT, role STUDENT/ADMIN
  - AI Service: Retrieval → Rerank → Generation
  - Generation LLM de xuat: GPT-4o-mini hoac grok-4.5 (temperature thap)
  - Vector DB: pgvector / Chroma / FAISS
  - Domain data: quy che dao tao, cam nang, thong bao hoc vu IUH
- **Viec tiep theo de xuat (P0):** Khoi tao frontend React (Vite + TypeScript) voi routing co ban.

---

## Verification gan nhat

**Thoi diem:** 2026-11-19

**Da kiem tra:**
```text
cd backend
./mvnw test
./mvnw -q -DskipTests package
```

**Ket qua:**
- Maven test thanh cong: 1 test, 0 failure, 0 error, 0 skipped.
- Maven package thanh cong va tao `backend/target/regrag-backend-0.0.1-SNAPSHOT.jar`.
- Source compile voi `release 17`; Spring context khoi dong bang Spring Boot 3.5.7.
- Frontend, Docker Compose va RAG pipeline van chua co de kiem tra.

**Lenh verification khi da co code (dung sau):**
```bash
# Backend (tu workspace root)
cd backend && ./mvnw test
cd backend && ./mvnw -q -DskipTests package

# Frontend
cd frontend && npm ci && npm run build && npm test

# Stack
docker compose up -d --build
curl -sf http://localhost:<port>/api/system/health

# RAG smoke (vi du)
curl -s -X POST http://localhost:<port>/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"Khi nao bi canh bao hoc tap?"}'
```

**Ghi chu:** Moi lan cap nhat plan, can doi chieu lai source va cap nhat checkbox + muc Verification voi output that cua lenh tren.

---

*File nay la nguon theo doi tien do. Khi implement xong task, doi `[ ]` thanh `[x]` chi khi da xac minh trong source/test — khong danh dau theo y dinh hoac ten file trong thiet ke.*
