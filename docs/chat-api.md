# Phase 5 - Chat API

`POST /api/chat` la API chinh noi frontend, Spring backend va Python AI service.
Endpoint yeu cau JWT access token va da duoc bao ve boi rate limit theo user.

## Request

```http
POST /api/chat
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "question": "Điều kiện công nhận tốt nghiệp là gì?",
  "sessionId": null
}
```

- `question`: bat buoc, khong duoc blank, toi da 2.000 ky tu; backend trim hai dau
  truoc khi goi AI va luu database.
- `sessionId`: tuy chon. Bo trong de tao session moi; neu co thi session phai thuoc
  user trong JWT. Session khong ton tai hoac cua user khac deu tra `404` de khong
  lam lo ID.

## Response

```json
{
  "sessionId": "ba50550e-fdc7-49ba-90be-a2af798db9ca",
  "userMessageId": "2be389bd-22ea-43b1-8528-712def34ceda",
  "assistantMessageId": "549df310-45fd-4537-8607-931e415bccfc",
  "answer": "Sinh viên ... [Nguồn 1: Quy chế đào tạo, Điều 19, Khoản 2]",
  "status": "answered",
  "sources": [
    {
      "chunk_id": "chunk-19-2",
      "document_id": "document-quy-che",
      "title": "Quy chế đào tạo",
      "dieu": "19",
      "khoan": "2",
      "source": "https://example.test/quy-che",
      "snippet": "Sinh viên phải nộp hồ sơ tại Phòng Đào tạo.",
      "score": 0.91
    }
  ]
}
```

Backend luu user message khong citation va assistant message kem snapshot toan bo
`sources` vao cot JSONB `messages.citations`. Viec luu hai message va cap nhat
session dien ra trong mot transaction sau khi AI tra loi thanh cong. Goi HTTP den
AI khong giu database transaction mo.

## AI service contract

Backend goi `POST {AI_SERVICE_URL}/answer` voi `{ "question": "..." }`. Cau hinh:

```dotenv
AI_SERVICE_URL=http://localhost:8000
AI_SERVICE_TIMEOUT_SECONDS=120
```

Khoi dong AI service tu `ai-service/`:

```bash
set -a
source ../.env
set +a
../data-pipeline/.venv/bin/uvicorn api:app --host 0.0.0.0 --port 8000
```

AI service nap embedding/reranker mot lan luc startup va expose:

- `GET /health`
- `POST /answer`

## Status codes

| Status | Truong hop |
|---|---|
| `200` | Cau hoi da duoc xu ly va luu. |
| `400` | JSON/request validation sai, question blank hoac qua dai. |
| `401` | Khong co access token hoac token khong hop le. |
| `404` | `sessionId` khong ton tai/khong thuoc user. |
| `429` | Vuot `CHAT_RATE_LIMIT_PER_MINUTE`. |
| `502` | AI service timeout, offline hoac tra response khong hop le. |

## Kiem thu

```bash
cd backend
./mvnw verify
```

Integration tests co PostgreSQL that kiem tra JWT, validation, ownership, session,
message ordering va citation JSONB. AI client duoc mock trong integration test de
ket qua deterministic va khong tieu ton LLM quota.
