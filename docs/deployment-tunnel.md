# Hướng dẫn Triển khai & Demo Công khai (Cloudflare Tunnel) - IUH-RegRAG

Tài liệu này ghi chép kiến trúc và hướng dẫn vận hành hệ thống **IUH-RegRAG** trên môi trường công khai (Public HTTPS) phục vụ buổi báo cáo và demo đồ án tốt nghiệp.

---

## 1. Kiến trúc Triển khai (Architecture Overview)

Hệ thống được đưa ra Internet thông qua công nghệ **Cloudflare Quick Tunnel (`cloudflared`)** kết hợp **Nginx Reverse Proxy** và **Docker Compose**:

```
                  [ Người dùng / Hội đồng chấm thi ]
                                 │
                            HTTPS (Cổng 443)
                                 ▼
                     [ Cloudflare Edge Network ]
                    (Tự động cấp SSL/TLS miễn phí)
                                 │
                                 ▼ (Mã hóa Outbound Tunnel)
                       [ Container: tunnel ]
                    (cloudflare/cloudflared:latest)
                                 │
                    (Mạng nội bộ Docker khép kín)
            ┌────────────────────┼────────────────────┐
            ▼                    ▼                    ▼
   [ frontend:80 ]       [ backend:8080 ]     [ ai-service:8000 ]
     React 19 + Nginx     Spring Boot 3 REST        FastAPI
                                 │                    │
                                 ▼                    ▼
                           [ postgres:5432 (pgvector) ]
```

### Ưu điểm vượt trội của giải pháp:
- **Chi phí 0 VNĐ**: Tận dụng toàn bộ sức mạnh phần cứng GPU Metal của máy Mac M1 Pro để chạy Ollama `qwen2.5:3b`.
- **An toàn tuyệt đối**: Không cần mở port Router ở nhà (NAT / Port Forwarding), ngăn chặn nguy cơ bị quét IP.
- **Có sẵn chứng chỉ HTTPS**: URL `https://*.trycloudflare.com` với chứng chỉ số hợp lệ, trình duyệt không báo lỗi "Not secure".
- **Dữ liệu đầy đủ**: Sẵn sàng 1.543 chunks quy chế đào tạo IUH + 1 tài khoản Admin duy nhất (`admin@iuh.edu.vn`).

---

## 2. Các tệp cấu hình chính

| Tệp tin | Vị trí | Công dụng |
| :--- | :--- | :--- |
| `docker-compose.tunnel.yml` | Thư mục gốc | Khai báo container `tunnel` chạy image `cloudflare/cloudflared:latest`. |
| `scripts/tunnel.sh` | `scripts/` | Script quản lý: Khởi động, kiểm tra trạng thái, xem log và lấy URL public. |
| `frontend/nginx.conf` | `frontend/` | Nginx reverse proxy `/api/` về `backend:8080` và phục vụ SPA React. |
| `.env` | Thư mục gốc | Khai báo `FRONTEND_ORIGIN` hỗ trợ cả `localhost:5173` và `https://*.trycloudflare.com`. |
| `backend/.../SecurityConfig.java` | `backend/` | Cho phép cấu hình CORS linh hoạt với `setAllowedOriginPatterns`. |

---

## 3. Hướng dẫn Vận hành Demo

### 3.1. Khởi động hệ thống và lấy link Demo
Khi chuẩn bị demo hoặc nộp bài, chỉ cần chạy 1 lệnh duy nhất tại thư mục gốc:

```bash
./scripts/tunnel.sh start
```

Màn hình sẽ hiển thị link HTTPS công khai, ví dụ:
```text
=================================================================
🎉 LINK DEMO PUBLIC HTTPS CỦA BẠN:
👉 https://fuzzy-eric-bizarre-promotion.trycloudflare.com
👉 Trang Quản trị: https://fuzzy-eric-bizarre-promotion.trycloudflare.com/admin
=================================================================
```

### 3.2. Kiểm tra lại đường dẫn bất kỳ lúc nào
Nếu quên URL, bạn có thể kiểm tra lại nhanh bằng lệnh:
```bash
./scripts/tunnel.sh url
```

### 3.3. Khởi động lại hệ thống
Khi cần khởi động lại toàn bộ stack và cấp phát lại đường truyền tunnel:
```bash
./scripts/tunnel.sh restart
```

### 3.4. Xem log hoạt động thời gian thực
Để theo dõi request từ hội đồng truy cập:
```bash
./scripts/tunnel.sh logs
```

### 3.5. Dừng tunnel khi kết thúc demo
Khi buổi báo cáo hoàn tất:
```bash
./scripts/tunnel.sh stop
```
*(Các container local vẫn hoạt động bình thường trên `http://localhost:5173`).*

---

## 4. Kịch bản Demo Gợi ý cho Hội đồng

1. **Hỏi đáp sinh viên:**
   - Mở link công khai trên trình duyệt điện thoại hoặc máy tính bảng để hội đồng thấy giao diện Responsive chuẩn di động.
   - Chọn câu hỏi gợi ý: *"Điều kiện để được xét tốt nghiệp là gì?"*.
   - Đặt câu hỏi về công thức: *"Cách tính điểm học phần lý thuyết theo quy chế IUH?"* (Kiểm tra công thức toán học hiển thị ngang rõ ràng, trích dẫn chính xác Điều 18 Khoản 2).
2. **Khu vực Quản trị (Admin):**
   - Đăng nhập tại `/admin` với tài khoản: `admin@iuh.edu.vn` / `AdminPassword123@`.
   - Xem danh sách 1.543 chunks quy chế và thông số hệ thống vector DB.

