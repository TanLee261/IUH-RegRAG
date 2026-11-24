# IUH-RegRAG: Hướng dẫn Vận hành & Nạp Văn bản Quy chế Mới (Ingestion Guide)

Tài liệu này hướng dẫn chi tiết quy trình nạp, xử lý sơ bộ (preprocessing), chia đoạn (chunking), tạo vector nhúng (embedding) và lưu trữ văn bản quy chế mới vào hệ thống **IUH-RegRAG**.

---

## 1. Sơ đồ Luồng Xử lý Dữ liệu (Ingestion Pipeline)

```
[ Văn bản gốc (PDF / HTML / DOCX) ]
                 │
                 ▼
[ Tầng 1: Parser & Trích xuất Văn bản ]
• PDF: PyMuPDF / pdfplumber (giữ nguyên cấu trúc phân cấp)
• HTML: BeautifulSoup4 (lọc sạch thẻ header, footer, script)
                 │
                 ▼
[ Tầng 2: Làm sạch & Chuẩn hóa Ký tự ]
• Khử ký tự lạ, chuẩn hóa Unicode tiếng Việt (NFC)
• Gom các dòng công thức toán học bị vỡ/ngắt dòng
                 │
                 ▼
[ Tầng 3: Semantic Chunking (Theo Điều / Khoản) ]
• Phân đoạn theo cấu trúc pháp lý: Chương -> Điều -> Khoản -> Điểm
• Kèm metadata đầy đủ: document_id, dieu, khoan, title, source_url
                 │
                 ▼
[ Tầng 4: Tạo Vector Nhúng (Dense Embedding) ]
• Model: BAAI/bge-m3 (Dense Vector: 1024 chiều)
• Batch size: 16/32, chuẩn hóa L2 normalization
                 │
                 ▼
[ Tầng 5: Lưu trữ PostgreSQL + pgvector ]
• Bảng knowledge_documents (lưu metadata & hash SHA-256)
• Bảng knowledge_chunks (lưu nội dung chunk, vector embedding, HNSW index)
```

---

## 2. Phương thức 1: Nạp qua Giao diện Quản trị Viên (Admin Web UI)

Dành cho cán bộ quản trị hoặc chuyên viên Phòng Đào tạo cập nhật văn bản nhanh chóng mà không cần thao tác dòng lệnh.

### Các bước thực hiện:
1. Đăng nhập vào hệ thống với tài khoản Quản trị viên:
   - URL: `http://localhost:5173/admin` (hoặc domain public tunnel).
   - Email: `admin@iuh.edu.vn`
2. Chọn Tab **"Văn bản quy chế"**.
3. Điền thông tin vào form **"Tải lên văn bản quy chế mới"**:
   - **Tiêu đề văn bản:** Ví dụ *"Quyết định sửa đổi quy chế đào tạo 2026"*.
   - **Phiên bản:** Ví dụ *"v2.1"* hoặc *"2026"*.
   - **Link nguồn chính thức:** Đường link văn bản trên website IUH (để sinh viên đối chiếu).
   - **Tệp văn bản:** Chọn file định dạng `.pdf`, `.docx` hoặc `.txt`.
4. Nhấn nút **"Tải lên & Xử lý"**.
5. Hệ thống sẽ lưu trữ tệp vào thư mục an toàn `/app/uploads/documents/`, tính toán mã băm SHA-256 chống trùng lặp và chuyển trạng thái thành `PENDING` -> `INGESTED`.

---

## 3. Phương thức 2: Nạp Hàng loạt qua Python CLI (Data Pipeline)

Dành cho lập trình viên (Developer) hoặc DevOps khi cần nạp dữ liệu ban đầu hoặc nạp lại toàn bộ kho quy chế lớn.

### 3.1. Chuẩn bị môi trường
Từ thư mục gốc dự án:
```bash
cd data-pipeline
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 3.2. Cấu hình biến môi trường
Đảm bảo biến môi trường kết nối database đã được nạp:
```bash
set -a
source ../.env
set +a
```

### 3.3. Các lệnh thực thi Ingestion

#### A. Nạp toàn bộ kho tài liệu trong thư mục raw:
```bash
python -m ingest.ingest
```
Pipeline sẽ tự động quét toàn bộ các file tại `data-pipeline/data/raw/`, kiểm tra trùng lặp qua SHA-256, thực hiện chunking và insert vào PostgreSQL pgvector.

#### B. Nạp theo từng nhóm tài liệu cụ thể:
```bash
# Chỉ nạp lại nhóm quy chế đào tạo tín chỉ:
python -m ingest.ingest --group "01-quy-che-dao-tao"

# Chỉ nạp lại nhóm quy định tốt nghiệp:
python -m ingest.ingest --group "03-tot-nghiep"
```

#### C. Xem báo cáo nạp dữ liệu:
Sau khi chạy xong, xem kết quả chi tiết tại:
```bash
cat data-pipeline/data/reports/ingest-report.json
```
Báo cáo sẽ liệt kê: số văn bản thành công, số văn bản bị bỏ qua do trùng lặp (skipped), số lượng chunks được tạo và thời gian xử lý.

---

## 4. Kiểm tra Kết quả Truy vấn sau khi Nạp (Verification)

Để kiểm tra ngay văn bản mới nạp có được mô hình tìm kiếm thấy hay không, sử dụng công cụ CLI kiểm tra Hybrid Search:

```bash
python -m storage.hybrid_search "điều kiện xét tốt nghiệp học phần thay thế" --limit 3
```

Kết quả in ra sẽ gồm: `chunk_id`, `score`, `title`, `dieu`, `khoan` và đoạn trích dẫn tương ứng.

---

## 5. Nguyên tắc Idempotent & An toàn Dữ liệu

1. **Khử trùng lặp (Deduplication):** Trước khi nạp văn bản, pipeline tính toán `content_sha256`. Nếu văn bản đã tồn tại trong database với cùng mã băm, hệ thống sẽ bỏ qua để tránh rác vector.
2. **Cập nhật phiên bản:** Khi có văn bản thay thế, văn bản cũ được đánh dấu trạng thái `inactive` trong `knowledge_documents` để ưu tiên văn bản hiện hành trong quá trình truy vấn.

