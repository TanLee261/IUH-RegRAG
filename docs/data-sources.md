# IUH-RegRAG: Báo cáo Nguồn Dữ liệu Quy chế & Phạm vi Pháp lý

Tài liệu này cung cấp danh mục chi tiết các nguồn tài liệu quy chế chính thức của Trường Đại học Công nghiệp TP.HCM (IUH) đã được thu thập, trích xuất và lưu trữ trong cơ sở dữ liệu Vector (`pgvector`), đồng thời **minh định rõ ràng phạm vi pháp lý và giới hạn của hệ thống chatbot**.

---

## 1. Tuyên bố Minh bạch Phạm vi Pháp lý (Legal Scope & Transparency)

> [!IMPORTANT]
> **Mục tiêu của Chatbot IUH-RegRAG:**
> - Hệ thống được xây dựng nhằm mục đích hỗ trợ sinh viên nhanh chóng tìm kiếm, tra cứu và hiểu các điều khoản quy định học vụ chung của Nhà trường.
> - **Không thay thế văn bản quy phạm nội bộ chính thức:** Mọi quyết định học vụ (xét tốt nghiệp, buộc thôi học, bảo lưu, miễn giảm môn học) phải căn cứ trên hồ sơ gốc và quyết định chính thức do Phòng Đào tạo (PĐT), Phòng Công tác sinh viên (CTSV) hoặc Ban Giám hiệu ban hành.
> - **Phạm vi bao phủ:** Tập trung chủ yếu vào **Quy chế đào tạo trình độ đại học theo hệ thống tín chỉ hiện hành** và các văn bản hướng dẫn chung cấp trường. Các quy định nội bộ đặc thù riêng của từng Khoa/Viện (ví dụ: quy định đồ án chuyên ngành hẹp) chỉ được bao phủ khi có văn bản công khai.

---

## 2. Thống kê Kho Tri thức trong Cơ sở Dữ liệu

| Chỉ số | Số lượng | Ghi chú |
| :--- | :--- | :--- |
| **Tổng số văn bản đã nạp (`knowledge_documents`)** | **124** | Bao gồm quy chế, hướng dẫn, thông báo và CTĐT |
| **Tổng số đoạn tri thức (`knowledge_chunks`)** | **1.543** | Đã được nhúng vector 1024 chiều (`BAAI/bge-m3`) |
| **Trạng thái pháp lý mặc định** | `active` / `unverified` | Đã qua rà soát định dạng và trích xuất Điều/Khoản |

---

## 3. Bảng kê Chi tiết các Nhóm Văn bản Chính thức đã Thu thập

### 3.1. Nhóm Quy chế Đào tạo & Học vụ Chung (Trọng tâm P0)

1. **Quy chế Đào tạo Trình độ Đại học theo Hệ thống Tín chỉ (Ban hành 2025):**
   - **Căn cứ pháp lý:** Xây dựng theo Thông tư số 08/2021/TT-BGDĐT của Bộ GD&ĐT.
   - **Nguồn:** Cổng thông tin Phòng Đào tạo IUH (`https://pdt.iuh.edu.vn/quy-che-dao-tao`).
   - **Nội dung bao phủ:**
     - Điều kiện nhập học, thời gian đào tạo tối đa theo khóa.
     - Đăng ký học phần, số tín chỉ tối thiểu/tối đa trong học kỳ chính và học kỳ hè (Điều 14).
     - Rút học phần, hủy học phần, đăng ký học lại, học cải thiện (Điều 15, 20).
     - Đánh giá học phần, thang điểm 10, thang điểm chữ (A, B, C, D, F) và thang điểm 4 (Điều 17).
     - **Công thức tính điểm học phần:** Điểm lý thuyết, điểm thực hành và học phần tích hợp (Điều 18 Khoản 2 & Khoản 4).
     - Cảnh báo học tập (mức 1, mức 2) và Buộc thôi học do học vụ (Điều 16).
     - Điều kiện xét và công nhận tốt nghiệp (Điều 21).

2. **Quy định Khóa luận Tốt nghiệp & Thực tập Doanh nghiệp:**
   - **Mã văn bản eOffice:** `202512/Documents/quy-dinh-khoa-luan-tot-nghiep-thuc-tap-doanh-nghiep-iuh`.
   - **Nội dung:** Số tín chỉ tích lũy tối thiểu để nhận khóa luận, quy trình chấm bảo vệ và tính điểm.

3. **Quy định Chuẩn đầu ra Ngoại ngữ & Tin học:**
   - **Văn bản:** Thông báo số 1581/TB-ĐHCN và các văn bản quy đổi chứng chỉ quốc tế (TOEIC, IELTS, VSTEP, MOS, IC3).
   - **Phạm vi áp dụng:** Áp dụng theo từng khóa tuyển sinh (từ K16 đến K20+).

---

### 3.2. Nhóm Công tác Sinh viên & Rèn luyện

1. **Quy chế Đánh giá Kết quả Rèn luyện Sinh viên:**
   - **Văn bản:** Quy định đánh giá điểm rèn luyện (Bản 2023 và cập nhật 2026).
   - **Nội dung:** 5 tiêu chí chấm điểm rèn luyện, khung phân loại (Xuất sắc, Tốt, Khá, Trung bình, Yếu, Kém) và quyền lợi xét học bổng, khen thưởng.

2. **Quy chế Công tác Sinh viên:**
   - **Nội dung:** Quyền hạn, nghĩa vụ của sinh viên, chế độ chính sách, kỷ luật và khen thưởng.

3. **Cẩm nang Sinh viên / Sổ tay Người học:**
   - Toàn văn tài liệu hướng dẫn sinh viên các thủ tục hành chính một cửa (cấp giấy chứng nhận sinh viên, tạm hoãn nghĩa vụ quân sự, cấp lại thẻ sinh viên).

---

### 3.3. Nhóm Chương trình Đào tạo (CTĐT)

- Hệ thống đã nạp khung chương trình đào tạo của các ngành tiêu biểu:
  - Tài chính - Ngân hàng (Chương trình chuẩn & Chất lượng cao).
  - Kế toán - Kiểm toán.
  - Công nghệ Thông tin, Kỹ thuật Phần mềm.
  - Công nghệ Kỹ thuật Điện - Điện tử, Cơ điện tử.

---

## 4. Các Giới hạn Dữ liệu Hiện tại (Known Limitations)

Để đảm bảo tính trung thực và học thuật khi bảo vệ đồ án:
1. **Thông tin Biểu phí / Học phí biến động:** Mức học phí cụ thể theo từng học kỳ có thể thay đổi theo thông báo tài chính riêng hàng năm; chatbot chỉ cung cấp quy định chung về thời hạn đóng và xử lý nợ học phí.
2. **Quy định riêng từng Khoa:** Một số môn học chuyên ngành có điều kiện tiên quyết riêng được quy định tại Đề cương chi tiết học phần; chatbot sẽ hướng dẫn người học xem trực tiếp tại đề cương môn học trên Cổng sinh viên.
3. **Cơ chế cập nhật:** Khi Nhà trường ban hành quyết định sửa đổi mới, Quản trị viên cần tải văn bản lên qua trang Admin `/admin` hoặc chạy lệnh nạp bổ sung qua CLI để cập nhật cơ sở dữ liệu vector.

