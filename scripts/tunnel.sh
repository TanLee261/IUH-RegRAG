#!/usr/bin/env bash
# ==============================================================================
# IUH-RegRAG: Cloudflare Quick Tunnel Management Script
# ==============================================================================
set -e

ACTION="${1:-start}"

case "$ACTION" in
  start)
    echo "🚀 Đang khởi động IUH-RegRAG cùng Cloudflare Quick Tunnel..."
    docker compose -f docker-compose.yml -f docker-compose.tunnel.yml up -d tunnel
    echo "⏳ Đang đợi Cloudflare cấp phát URL HTTPS công khai..."
    sleep 5
    URL=""
    for i in {1..15}; do
      URL=$(docker logs iuh-regrag-tunnel 2>&1 | grep -o 'https://[a-zA-Z0-9-]*\.trycloudflare\.com' | tail -n 1 || true)
      if [ -n "$URL" ]; then
        break
      fi
      sleep 2
    done

    if [ -n "$URL" ]; then
      echo "================================================================="
      echo "🎉 LINK DEMO PUBLIC HTTPS CỦA BẠN:"
      echo "👉 $URL"
      echo "👉 Trang Quản trị: $URL/admin"
      echo "================================================================="
      echo "💡 Bạn có thể gửi đường link này cho thầy cô/hội đồng để test trực tiếp!"
    else
      echo "⚠️ Chưa tìm thấy URL trong log, xem log chi tiết bằng: docker logs iuh-regrag-tunnel"
    fi
    ;;

  url)
    URL=$(docker logs iuh-regrag-tunnel 2>&1 | grep -o 'https://[a-zA-Z0-9-]*\.trycloudflare\.com' | tail -n 1 || true)
    if [ -n "$URL" ]; then
      echo "🌐 URL Demo hiện tại: $URL"
    else
      echo "❌ Tunnel chưa chạy hoặc chưa tìm thấy URL. Hãy chạy: ./scripts/tunnel.sh start"
    fi
    ;;

  status)
    docker compose -f docker-compose.yml -f docker-compose.tunnel.yml ps
    ;;

  logs)
    docker logs -f iuh-regrag-tunnel
    ;;

  stop)
    echo "🛑 Đang dừng Cloudflare Tunnel..."
    docker compose -f docker-compose.yml -f docker-compose.tunnel.yml stop tunnel
    docker compose -f docker-compose.yml -f docker-compose.tunnel.yml rm -f tunnel
    echo "✅ Đã dừng tunnel."
    ;;

  restart)
    echo "🔄 Đang khởi động lại toàn bộ hệ thống IUH-RegRAG..."
    docker compose -f docker-compose.yml -f docker-compose.tunnel.yml stop tunnel
    docker compose -f docker-compose.yml -f docker-compose.tunnel.yml rm -f tunnel
    docker compose -f docker-compose.yml -f docker-compose.tunnel.yml up -d --force-recreate frontend backend
    exec "$0" start
    ;;

  *)
    echo "Cách dùng: $0 {start|url|status|logs|stop|restart}"
    exit 1
    ;;
esac

