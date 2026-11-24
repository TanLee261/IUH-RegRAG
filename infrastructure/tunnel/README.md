# Cloudflare Quick Tunnel - IUH-RegRAG

Thư mục này cấu hình tầng Reverse Proxy công khai sử dụng **Cloudflare Tunnel (cloudflared)**.

## 1. Cơ chế hoạt động
- Khởi chạy container `cloudflare/cloudflared` kết nối trực tiếp đến Cloudflare Edge Network qua giao thức outbound HTTPS (không cần mở port router NAT/Port Forwarding).
- Cloudflare cấp phát chứng chỉ SSL/TLS miễn phí với URL dạng: `https://*.trycloudflare.com`.
- Mọi request gửi đến URL trên sẽ được chuyển tiếp qua tunnel mã hóa đến service `frontend:80` (Nginx + React SPA).
- Nginx sẽ phục vụ giao diện tĩnh và proxy các request `/api/` về `backend:8080`.

## 2. Quản lý Tunnel
- **Khởi động tunnel & lấy URL:** `./scripts/tunnel.sh start`
- **Xem URL hiện tại:** `./scripts/tunnel.sh url`
- **Xem log thời gian thực:** `./scripts/tunnel.sh logs`
- **Dừng tunnel:** `./scripts/tunnel.sh stop`

