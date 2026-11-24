# IUH-RegRAG: Đặc tả Hợp đồng API (API Contract)

Tài liệu này quy chuẩn toàn bộ các endpoint REST API trong hệ thống **IUH-RegRAG**, định dạng dữ liệu gửi/nhận (request/response), mã trạng thái HTTP và quy tắc bảo mật xác thực qua JWT.

---

## 1. Tiêu chuẩn Chung & Xác thực (General Standards & Auth)

- **Base URL:**
  - Local Dev: `http://localhost:5173/api` (hoặc trực tiếp backend `http://localhost:8085/api`)
  - Public Demo: `https://<tunnel-domain>/api`
- **Content-Type:** `application/json` (trừ endpoint upload dùng `multipart/form-data`).
- **Mã hóa ký tự:** UTF-8.
- **Xác thực (JWT Bearer Token):**
  - Header: `Authorization: Bearer <accessToken>`
  - Thuật toán: HMAC-SHA256 (HS256).
  - Claims trong token: `sub` (User ID), `email`, `role` (`STUDENT` hoặc `ADMIN`), `iss`, `exp`, `iat`.
  - Thời hạn: Access Token (15 phút), Refresh Token (7 ngày).

---

## 2. Danh mục Endpoints

### 2.1. Nhóm Xác thực Người dùng (`/api/auth`)

#### `POST /api/auth/register`
Đăng ký tài khoản sinh viên mới. Lưu ý: Không cho phép đăng ký tài khoản có email chứa tiền tố "admin" (chỉ có duy nhất 1 tài khoản ADMIN).

- **Quyền hạn:** Công khai (Public).
- **Request Body:**
```json
{
  "email": "sinhvien@iuh.edu.vn",
  "password": "Password123@"
}
```
- **Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```
- **Lỗi thường gặp:**
  - `400 Bad Request`: Email sai định dạng, mật khẩu < 8 ký tự.
  - `403 Forbidden`: Cố tình đăng ký email admin.
  - `409 Conflict`: Email đã tồn tại.

---

#### `POST /api/auth/login`
Đăng nhập hệ thống, nhận cặp token.

- **Quyền hạn:** Công khai (Public).
- **Request Body:**
```json
{
  "email": "admin@iuh.edu.vn",
  "password": "AdminPassword123@"
}
```
- **Response (200 OK):** Trả về `accessToken`, `refreshToken`, `tokenType`, `expiresIn`.
- **Lỗi:** `401 Unauthorized`: Sai email hoặc mật khẩu.

---

#### `POST /api/auth/refresh`
Cấp lại Access Token mới khi token cũ hết hạn bằng Refresh Token hợp lệ.

- **Request Body:**
```json
{
  "refreshToken": "eyJhbGciOi..."
}
```
- **Response (200 OK):** Cặp token mới.

---

### 2.2. Nhóm Hỏi đáp RAG (`/api/chat`)

#### `POST /api/chat`
Gửi câu hỏi của người học đến pipeline RAG. Hệ thống thực hiện: Hybrid Search (BM25 + BGE-M3) -> BGE-Reranker-v2-m3 -> Prompt Assembly -> LLM Generation -> Lưu lịch sử phiên chat.

- **Quyền hạn:** Yêu cầu đăng nhập (`STUDENT` hoặc `ADMIN`).
- **Rate Limit:** 100 requests / phút.
- **Request Body:**
```json
{
  "sessionId": "c3dbee98-c7ba-4bf6-afc7-693da1bdde17", // Tùy chọn, để trống sẽ tạo phiên mới
  "question": "Cách tính điểm học phần lý thuyết theo quy chế IUH?"
}
```
- **Response (200 OK):**
```json
{
  "sessionId": "c3dbee98-c7ba-4bf6-afc7-693da1bdde17",
  "userMessageId": "5c1a7b09-b289-407a-888d-e9cacac6757a",
  "assistantMessageId": "046d11d9-493d-4f95-85f6-84163d7a2d46",
  "answer": "Để tính điểm học phần lý thuyết...\n\n- [Nguồn 1: Điều 18, Khoản 2]...",
  "status": "answered", // "answered" hoặc "no_context"
  "sources": [
    {
      "chunk_id": "cfe1c1a08eeb953beada5f645fc36874d350a99fdbb42b0d0851600c67f543fc",
      "document_id": "924a84b07a0e03f2f2f5a1df11e75f96d12e3679ccd9f37105301192506c1386",
      "title": "Quy chế đào tạo tín chỉ 2025; tốt nghiệp và chuẩn đầu ra",
      "dieu": "18",
      "khoan": "2",
      "source": "https://pdt.iuh.edu.vn/quy-che-dao-tao",
      "snippet": "2. Mỗi học phần lý thuyết được đánh giá bao gồm 3 thành phần sau...",
      "score": 0.9046
    }
  ]
}
```

---

#### `GET /api/chat/sessions`
Lấy danh sách các phiên trò chuyện của người dùng hiện tại (sắp xếp theo thời gian mới nhất).

- **Response (200 OK):**
```json
[
  {
    "id": "c3dbee98-c7ba-4bf6-afc7-693da1bdde17",
    "title": "Cách tính điểm học phần lý thuyết...",
    "createdAt": "2026-11-24T03:05:15Z",
    "updatedAt": "2026-11-24T03:05:23Z",
    "messageCount": 2
  }
]
```

---

#### `GET /api/chat/sessions/{id}`
Lấy toàn bộ tin nhắn trong một phiên chat cụ thể để hiển thị lên màn hình.

---

### 2.3. Nhóm Đánh giá Phản hồi (`/api/feedback`)

#### `POST /api/feedback`
Gửi đánh giá Like/Dislike cho câu trả lời của AI kèm góp ý chi tiết.

- **Request Body:**
```json
{
  "messageId": "046d11d9-493d-4f95-85f6-84163d7a2d46",
  "rating": "LIKE", // hoặc "DISLIKE"
  "comment": "Câu trả lời trích dẫn rất chính xác công thức Điều 18."
}
```
- **Response (200 OK):**
```json
{
  "id": "f1000000-0000-0000-0000-000000000001",
  "messageId": "046d11d9-493d-4f95-85f6-84163d7a2d46",
  "rating": "LIKE",
  "comment": "Câu trả lời trích dẫn rất chính xác công thức Điều 18.",
  "createdAt": "2026-11-24T03:10:00Z"
}
```

---

### 2.4. Nhóm Câu hỏi Gợi ý (`/api/suggestions`)

- **`GET /api/suggestions` (Công khai):** Trả về danh sách câu hỏi mẫu đang `active` theo thứ tự hiển thị.
- **`GET /api/suggestions/admin` (Chỉ ADMIN):** Lấy toàn bộ danh sách để quản lý.
- **`POST /api/suggestions` (Chỉ ADMIN):** Thêm câu hỏi gợi ý mới (`topic`, `question`, `displayOrder`).
- **`DELETE /api/suggestions/{id}` (Chỉ ADMIN):** Xóa câu hỏi gợi ý.

---

### 2.5. Nhóm Quản lý Văn bản & Vector DB (`/api/documents`)

- **`GET /api/documents` (Chỉ ADMIN):** Danh sách văn bản đăng ký tải lên (hỗ trợ phân trang: `?page=0&size=10`).
- **`POST /api/documents` (Chỉ ADMIN):** Tải lên văn bản quy chế mới (`multipart/form-data`: `file`, `title`, `version`, `sourceUrl`).
- **`DELETE /api/documents/{id}` (Chỉ ADMIN):** Xóa văn bản quản trị.
- **`GET /api/documents/vector-active` (Chỉ ADMIN):** Trả về danh sách 124 văn bản quy chế thực tế đang lưu trong `knowledge_documents` kèm số lượng chunks và trạng thái pháp lý.

---

### 2.6. Nhóm Kiểm tra Trạng thái (`/api/system` & `/actuator`)

- **`GET /api/system/health` (Chỉ ADMIN):** Thống kê số lượng văn bản, chunks trong vector DB và trạng thái kết nối DB / AI Service.
- **`GET /actuator/health` (Công khai):** Healthcheck tiêu chuẩn của Spring Boot (`{"status": "UP"}`).
- **`GET /health` (AI Service - cổng 8000):** Healthcheck của FastAPI (`{"status": "ok", "retrieval": "active"}`).

