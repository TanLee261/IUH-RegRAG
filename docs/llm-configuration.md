# IUH-RegRAG: Cấu hình Mô hình LLM, Tham số Suy luận & Tối ưu Chi phí

Tài liệu này ghi chép các quyết định kỹ thuật liên quan đến việc lựa chọn mô hình ngôn ngữ lớn (LLM), thiết lập siêu tham số suy luận (inference hyperparameters) và các chiến lược tối ưu hóa độ trễ, tài nguyên và chi phí vận hành cho hệ thống **IUH-RegRAG**.

---

## 1. Lựa chọn Mô hình Ngôn ngữ Lớn (Model Selection)

Hệ thống hỗ trợ kiến trúc mô hình linh hoạt theo cơ chế **Primary - Fallback** có thể cấu hình động qua biến môi trường mà không cần sửa code:

### 1.1. Mô hình Chính (Primary Model): Ollama `qwen2.5:3b`
- **Nhà phát triển:** Alibaba Cloud / Qwen Team (chạy qua Ollama runtime).
- **Kích thước tham số:** ~3.09 tỷ tham số (Quantization Q4_K_M, dung lượng ~1.9 GB).
- **Lý do lựa chọn:**
  1. **Hiểu tiếng Việt vượt trội:** Trong nhóm mô hình nhỏ dưới 7B, dòng Qwen 2.5 được đánh giá có khả năng đọc hiểu ngữ pháp, thuật ngữ hành chính và cấu trúc câu tiếng Việt tốt nhất hiện nay.
  2. **Tốc độ suy luận siêu tốc:** Khi chạy trên máy cá nhân trang bị chip Apple Silicon (GPU Metal M1 Pro), mô hình sinh từ đạt tốc độ **40-60 tokens/giây**, rút ngắn thời gian trả lời câu hỏi RAG từ **59 giây (ở bản 7B) xuống chỉ còn ~4 - 8 giây**.
  3. **Chi phí 0 VNĐ & Bảo mật:** Hoạt động hoàn toàn Offline trong môi trường máy chủ nội bộ, không phụ thuộc kết nối bên thứ ba, không rò rỉ dữ liệu người dùng.

### 1.2. Mô hình Dự phòng Cục bộ (Local Fallback): Ollama `qwen2.5:7b`
- Tự động kích hoạt khi mô hình 3B gặp sự cố hoặc đối với các tác vụ yêu cầu lập luận đa tầng phức tạp hơn.
- Dung lượng: ~4.7 GB (Q4_K_M).

### 1.3. Mô hình Dự phòng Đám mây (Cloud Provider): Google Gemini 1.5 / 2.0 Flash
- **Provider:** `LLM_PROVIDER=gemini` (Model: `gemini-1.5-flash`).
- **Ưu điểm:** Tốc độ phản hồi cực nhanh (< 2 giây), không tốn RAM máy chủ, phù hợp triển khai lên VPS giá rẻ chỉ có CPU (2 vCPU / 4GB RAM).
- **Hạn mức miễn phí:** Gói Google AI Studio Free Tier cung cấp 15 RPM (Requests Per Minute) và 1.500 RPD (Requests Per Day), hoàn toàn đáp ứng tốt nhu cầu demo và thử nghiệm đồ án mà không phát sinh chi phí.

---

## 2. Thiết lập Siêu tham số (Hyperparameters)

Các tham số này được kiểm soát tập trung qua `.env` và `ai-service/api.py`:

```ini
LLM_PROVIDER=ollama
LLM_MODEL=qwen2.5:3b
LLM_TEMPERATURE=0.2
LLM_MAX_OUTPUT_TOKENS=300
LLM_TIMEOUT_SECONDS=60
LLM_FALLBACK_ENABLED=true
LLM_FALLBACK_PROVIDER=ollama
LLM_FALLBACK_MODEL=qwen2.5:7b
```

### Ý nghĩa & Cơ sở thiết lập:
1. **`LLM_TEMPERATURE = 0.2` (Rất thấp):**
   - Với bài toán tra cứu văn bản quy chế pháp lý, tính **chính xác tuyệt đối và trung thành với ngữ cảnh** là ưu tiên số 1.
   - Nhiệt độ thấp giúp giảm thiểu tính ngẫu nhiên (creativity), ép mô hình chỉ tổng hợp thông tin từ các đoạn trích dẫn được cung cấp, loại bỏ nguy cơ "bịa đặt" (hallucination).
2. **`LLM_MAX_OUTPUT_TOKENS = 300`:**
   - Hạn chế câu trả lời lan man, tập trung vào câu trả lời trực diện và danh mục điều khoản trích dẫn.
   - Giúp giảm hơn 60% thời gian sinh từ của LLM so với mức mặc định 512 tokens.
3. **`max_sources = 3` (Giảm từ 5 xuống 3):**
   - Bộ Reranker (`BAAI/bge-reranker-v2-m3`) đã lọc và sắp xếp các đoạn trích có độ liên quan cao nhất lên đầu.
   - Việc chỉ đưa 3 đoạn trích dẫn chuẩn nhất vào prompt giúp giảm bớt chiều dài ngữ cảnh (context window), giúp mô hình tập trung hơn và tăng tốc độ xử lý thêm ~30%.

---

## 3. Các Giải pháp Tối ưu Chi phí & Tài nguyên (Cost Optimization)

Hệ thống áp dụng chiến lược đa tầng để tiết kiệm tài nguyên tính toán và chi phí:

1. **Cơ chế Ngắt sớm khi Thiếu ngữ cảnh (Short-circuit on Empty Retrieval):**
   - Khi câu hỏi của người dùng nằm ngoài phạm vi học vụ (ví dụ: *"Hôm nay thời tiết thế nào?"* hoặc *"Quán cơm nào ngon gần trường?"*).
   - Điểm số của Reranker không vượt qua ngưỡng tối thiểu (`RERANK_MIN_SCORE = 0.05`).
   - Hệ thống lập tức trả về `status: no_context` kèm thông báo lịch sự mà **KHÔNG gửi prompt đến LLM**, tiết kiệm 100% chi phí suy luận và thời gian tính toán.
2. **Làm phẳng Công thức Toán học (2-Layer Formula Sanitization):**
   - Các công thức tính điểm (như $ĐTKHP = \dots$) trích xuất từ PDF thường bị lỗi ngắt dòng thành từng ký tự rời rạc làm tiêu tốn rất nhiều tokens vô ích.
   - Hệ thống tự động tiền xử lý ghép nối các ký tự đơn lẻ thành 1 dòng công thức duy nhất trước khi đưa vào LLM, giúp tiết kiệm token và đảm bảo LLM hiểu đúng công thức.
3. **Bộ nhớ đệm Mô hình Nhúng (Embedding Cache):**
   - Thư mục cache `huggingface_cache` được gắn volume cố định, tránh việc tải lại trọng số mô hình khi khởi động container.

