# IUH-RegRAG: Hệ thống Chatbot RAG Quy chế Học vụ IUH

> **IUH-RegRAG** là hệ thống trợ lý ảo thông minh ứng dụng kiến trúc **RAG (Retrieval-Augmented Generation)** chuyên biệt phục vụ sinh viên và cán bộ Trường Đại học Công nghiệp TP.HCM (IUH) tra cứu quy chế đào tạo, chuẩn đầu ra, điểm rèn luyện, đăng ký học phần và các thủ tục học vụ chính thức.
> 
> Hệ thống đảm bảo tính chuẩn xác pháp lý cao: mọi câu trả lời đều có căn cứ trích dẫn chính xác đến từng **Điều, Khoản** và liên kết văn bản gốc, hạn chế tối đa hiện tượng ảo giác (hallucination).

---

## 🏗️ Kiến trúc Hệ thống (System Architecture)

Hệ thống được thiết kế theo mô hình Microservices hiện đại, đóng gói toàn diện bằng Docker Compose gồm 4 dịch vụ chính và tầng Reverse Proxy công khai:

```
                  [ Trình duyệt Web / Thiết bị Di động ]
                                 │
                            HTTPS (Cổng 443)
                                 ▼
                     [ Cloudflare Edge Network ]
                 (SSL/TLS tự động qua Quick Tunnel)
                                 │
                                 ▼
                     [ Tầng Frontend & Nginx ]
                     (React 19 SPA + Nginx Reverse Proxy)
                                 │
              ┌──────────────────┴──────────────────┐
              │ /                                   │ /api/
              ▼                                     ▼
        [ Web UI SPA ]                    [ Backend API Service ]
    (Tailwind/CSS, React 19)             (Spring Boot 3.5, Java 17)
                                                    │
                                                    ▼
    ┌─────────────────────────────────┬─────────────────────────────────┐
    │                                 │                                 │
    ▼                                 ▼                                 ▼
[ AI Service (Python FastAPI) ]  [ Relational DB ]            [ Vector DB Engine ]
• Hybrid Retrieval (BM25 + Vector) • Users & Auth (JWT)       • 1.543 Chunks Quy chế
• Embedding: BAAI/bge-m3          • Chat Sessions & Messages  • PostgreSQL 17
• Reranker: BGE-Reranker-v2-m3    • Feedbacks & Logs          • Extension pgvector (HNSW)
• LLM: Ollama Qwen2.5 / Gemini    • Document Metadata
```

---

## 🚀 Công nghệ Sử dụng (Tech Stack)

| Thành phần | Công nghệ | Chi tiết |
| :--- | :--- | :--- |
| **Frontend** | React 19, TypeScript, Vite | Nginx reverse proxy, Responsive UI, Markdown & Math formatting |
| **Backend API** | Spring Boot 3.5, Java 17 | Spring Security 6, Nimbus JWT, Bucket4j Rate Limiting, REST API |
| **Database** | PostgreSQL 17 + pgvector | HNSW Vector Index, Quan hệ người dùng & phiên chat |
| **AI Engine** | FastAPI, PyTorch, HuggingFace | `BAAI/bge-m3` (1024 dims), `BAAI/bge-reranker-v2-m3` |
| **Mô hình LLM** | Ollama / Google Gemini | Primary: `qwen2.5:3b`, Fallback: `qwen2.5:7b` / `gemini-1.5-flash` |
| **DevOps / Deploy**| Docker Compose, Cloudflare Tunnel | Multi-stage Dockerfile, `./scripts/tunnel.sh` |

---

## ⚡ Bắt đầu Nhanh (Quick Start)

### 1. Yêu cầu Tiền đề
- **Docker** & **Docker Compose v2** (đã bật Docker Desktop hoặc Docker Engine).
- (Tùy chọn cho local LLM): **Ollama** đang chạy trên máy (`ollama run qwen2.5:3b`).

### 2. Thiết lập Biến môi trường
Sao chép tệp mẫu và cập nhật các thông số bảo mật:
```bash
cp .env.example .env
```

### 3. Khởi động Toàn bộ Hệ thống (Chỉ 1 lệnh)
```bash
docker compose up -d
```
Tất cả 4 dịch vụ (`postgres`, `ai-service`, `backend`, `frontend`) sẽ tự động build và chạy:
- **Giao diện Người dùng (Frontend):** `http://localhost:5173`
- **Khu vực Quản trị Viên (Admin):** `http://localhost:5173/admin`
- **Backend Actuator Health:** `http://localhost:8085/actuator/health`
- **AI Service Health:** `http://localhost:8000/health`

### 4. Khởi động Demo Công khai ra Internet (Cloudflare HTTPS Tunnel)
Chỉ cần chạy script quản lý:
```bash
./scripts/tunnel.sh start
```
Hệ thống sẽ in ra URL công khai HTTPS hợp lệ có thể mở ngay trên điện thoại hoặc chia sẻ cho hội đồng chấm thi:
```text
🎉 LINK DEMO PUBLIC HTTPS CỦA BẠN:
👉 https://greensboro-exempt-billing-pierre.trycloudflare.com
👉 Trang Quản trị: https://greensboro-exempt-billing-pierre.trycloudflare.com/admin
```

---

## 🔐 Tài khoản Trải nghiệm Demo

Hệ thống đã được nạp sẵn dữ liệu 1.543 chunks quy chế và chính sách bảo mật **Chỉ duy nhất 1 tài khoản ADMIN**:

| Vai trò | Email đăng nhập | Mật khẩu | Quyền hạn |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `admin@iuh.edu.vn` | `AdminPassword123@` | Truy cập trang Quản trị `/admin`, theo dõi chỉ mục pgvector, quản lý câu hỏi gợi ý, upload văn bản mới. |
| **STUDENT** | Đăng ký trực tiếp tại trang web | Tùy chọn | Hỏi đáp AI, lưu lịch sử phiên chat, đánh giá phản hồi (Like/Dislike). |

---

## ⚙️ Cấu hình Biến Môi trường (`.env`)

| Biến | Giá trị Mặc định | Ý nghĩa |
| :--- | :--- | :--- |
| `POSTGRES_DB` | `iuh_regrag` | Tên cơ sở dữ liệu PostgreSQL. |
| `POSTGRES_USER` | `iuh_regrag` | Tên tài khoản database. |
| `POSTGRES_PASSWORD` | `regrag_local_secret_2025` | Mật khẩu kết nối database. |
| `POSTGRES_PORT` | `15432` | Cổng database map ra máy host. |
| `JWT_SECRET` | *(chuỗi bí mật $\ge$ 32 bytes)* | Khóa ký số JWT HS256 bảo mật. |
| `FRONTEND_ORIGIN` | `http://localhost:5173,...` | Nguồn gốc được phép gọi CORS. |
| `LLM_PROVIDER` | `ollama` | Provider LLM chính (`ollama` hoặc `gemini`). |
| `LLM_MODEL` | `qwen2.5:3b` | Tên model chính (tối ưu tốc độ ~4-8s). |
| `LLM_FALLBACK_PROVIDER` | `ollama` | Provider dự phòng khi model chính lỗi. |
| `LLM_FALLBACK_MODEL` | `qwen2.5:7b` | Model dự phòng. |
| `LLM_TEMPERATURE` | `0.2` | Nhiệt độ sinh từ (thấp để trả lời căn cứ thực tế). |
| `LLM_MAX_OUTPUT_TOKENS` | `300` | Giới hạn độ dài sinh từ để phản hồi siêu tốc. |
| `EMBEDDING_MODEL` | `BAAI/bge-m3` | Mô hình nhúng đa ngôn ngữ 1024 chiều. |
| `RERANK_MODEL` | `BAAI/bge-reranker-v2-m3` | Mô hình Cross-Encoder tái xếp hạng ngữ nghĩa. |

---

## 📂 Danh mục Tài liệu Kỹ thuật Chi tiết

Mọi tài liệu thiết kế và vận hành chuyên sâu nằm trong thư mục [`docs/`](docs/):

1. **Kiến trúc & Kế hoạch:**
   - [`docs/project_plan.md`](docs/project_plan.md): Lộ trình 9 Phase hoàn chỉnh của đồ án.
   - [`docs/architecture.md`](docs/architecture.md): Thiết kế kiến trúc phân tầng RAG.
   - [`docs/phase-2-erd.md`](docs/phase-2-erd.md): Sơ đồ thực thể cơ sở dữ liệu (ERD).
2. **Quy chế & Dữ liệu Tri thức:**
   - [`docs/data-sources.md`](docs/data-sources.md): Danh mục 14 nhóm văn bản quy chế chính thức IUH, số quyết định và phạm vi hiệu lực.
   - [`docs/ingestion-guide.md`](docs/ingestion-guide.md): Hướng dẫn nạp, tách chunk Điều/Khoản và đánh chỉ mục vector.
3. **Mô hình AI & LLM:**
   - [`docs/llm-configuration.md`](docs/llm-configuration.md): Phân tích cấu hình LLM, tối ưu hóa độ trễ, Prompt Grounding và tối ưu chi phí.
   - [`docs/reranking.md`](docs/reranking.md): Cơ chế tái xếp hạng hai giai đoạn (Hybrid Retrieval + Reranking).
4. **Giao tiếp API & Triển khai:**
   - [`docs/api-contract.md`](docs/api-contract.md): Tài liệu đặc tả API chuẩn RESTful đầy đủ.
   - [`docs/deployment-tunnel.md`](docs/deployment-tunnel.md): Hướng dẫn triển khai public HTTPS với Cloudflare Tunnel.

---

## 🧪 Kiểm thử & Xác minh Chất lượng

- **Backend Unit & Integration Tests:**
  ```bash
  cd backend && ./mvnw test
  ```
- **Frontend Typecheck & Build:**
  ```bash
  cd frontend && npm run build
  ```
- **AI Service Test Suite:**
  ```bash
  cd ai-service && pytest
  ```
- **RAG Golden Evaluation (20 câu hỏi vàng):**
  ```bash
  python ai-service/eval_rag.py
  ```
