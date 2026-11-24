# Phase 3 - Authentication

Backend dung Spring Security voi bearer JWT HS256. Bat profile `db` de khoi
dong datasource, Flyway va auth endpoints. `JWT_SECRET` duoc doc tu environment
va phai co it nhat 32 byte UTF-8; thay gia tri mau truoc khi chay. CORS chap
nhan origin `FRONTEND_ORIGIN` (mac dinh `http://localhost:5173`).
`LLM_API_KEY` cung duoc bind tu environment vao `LlmCredentials`; Phase 4 se
dung `requireApiKey()` khi goi LLM. Neu chua cau hinh LLM provider, backend
van chay voi gia tri rong va khong goi provider.

```bash
cd backend
set -a
source ../.env
set +a
SPRING_PROFILES_ACTIVE=db ./mvnw spring-boot:run
```

## API

| Method | Path | Request JSON | Status | Response |
|---|---|---|---|---|
| POST | `/api/auth/register` | `{ "email": "sv@iuh.edu.vn", "password": "at-least-8-chars" }` | 201 | Token pair |
| POST | `/api/auth/login` | `{ "email": "sv@iuh.edu.vn", "password": "..." }` | 200 | Token pair |
| POST | `/api/auth/refresh` | `{ "refreshToken": "..." }` | 200 | New token pair |
| POST | `/api/auth/logout` | `{ "refreshToken": "..." }` + `Authorization: Bearer <accessToken>` | 204 | Empty |

Token response:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Vi du:

```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"sv@iuh.edu.vn","password":"example-password-123"}'
```

Dang nhap dung email khong phan biet hoa/thuong; dang ky tao tai khoan role
`STUDENT` va luu mat khau duoi dang BCrypt. Duplicate email tra 409, du lieu
khong hop le tra 400, sai credentials/token hoac request khong co access JWT
tra 401. `/actuator/health` va `/actuator/info` duoc truy cap cong khai; cac
route khac yeu cau access token. Refresh JWT khong dung duoc lam access token.

`/api/documents` va `/api/documents/**` (moi method) cung nhu `/api/admin/**`
chi cho `ADMIN`: STUDENT nhan 403, khong dang nhap nhan 401. Day la security
policy da duoc test bang endpoint thu; Document API that se duoc them o Phase 5.
Dang ky chi tao `STUDENT`, khong cho phep chon ADMIN qua request.

`POST /api/chat` yeu cau access JWT va duoc gioi han theo user ID, mac dinh
20 request/phut (`CHAT_RATE_LIMIT_PER_MINUTE`). Vuot gioi han tra 429 kem
header `Retry-After` (so giay); GET chat va cac endpoint khac khong tinh vao
quota. Bo dem la in-memory theo tung backend process, tu xoa cua so cu khi co
nhieu user; neu deploy nhieu instance can dung mot shared rate-limit store.
Chat API that se duoc them o Phase 5.

Access JWT song 15 phut, refresh JWT song 7 ngay. Moi lan refresh, refresh
token cu bi thu hoi va token moi duoc phat. Logout thu hoi refresh token duoc
gui kem va access token hien tai; cac lan dang nhap khac cua cung user khong
bi anh huong. Neu access da het han, co the refresh truoc khi goi logout.

Flyway migration `backend/src/main/resources/db/migration/V2__auth_tokens.sql`
tao `auth_refresh_tokens` (chi luu SHA-256 cua refresh token) va
`auth_revoked_access_tokens` (JWT ID cua access token bi logout). JWT signature,
han dung va user con ton tai duoc kiem tra cho moi protected request; role duoc
lay tu bang `users`. De tranh du lieu token het han tich luy, ops co the xoa
cac hang cu sau khi da het han:

```sql
DELETE FROM auth_refresh_tokens WHERE expires_at < now();
DELETE FROM auth_revoked_access_tokens WHERE expires_at < now();
```

Kiem tra full auth flow tren PostgreSQL (tests rollback du lieu nghiep vu):

```bash
cd backend
DB_URL=jdbc:postgresql://localhost:5432/iuh_regrag \
DB_USERNAME=iuh_regrag DB_PASSWORD=your_local_password \
JWT_SECRET=your_local_secret_at_least_32_bytes \
./mvnw verify -Dspring.profiles.active=db
```
