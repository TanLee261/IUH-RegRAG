# Phase 1 - Thu thập tài liệu gốc IUH

- [x] ~~Thu thập tài liệu gốc: PDF/HTML quy chế đào tạo IUH, cẩm nang người học, thông báo PDT~~
  - Đã có quy chế đào tạo HTML, quy chế công tác sinh viên PDF, quy định rèn luyện PDF, sổ tay 2024 PDF, thông báo học phần/học phí và các biểu mẫu học vụ.
  - Dấu hoàn thành áp dụng cho task thu thập kho raw tối thiểu; không có nghĩa đã thu thập hết mọi văn bản của IUH hoặc hoàn tất Phase 1.

Không chỉnh sửa `project_plan.md`. Trạng thái được theo dõi riêng tại tài liệu này theo yêu cầu.

## Dữ liệu và bằng chứng

- [Danh mục có liên kết mở từng tệp và nguồn](../data-pipeline/data/CATALOG.md).
- [Manifest nguồn, SHA-256, thời điểm tải và lỗi](../data-pipeline/data/manifest.json).
- [Kết quả xác minh định dạng, số trang và nhu cầu OCR](../data-pipeline/data/validation.json).
- Tài liệu gốc giữ nguyên byte tải về trong `data-pipeline/data/raw/`; không in lại HTML thành PDF, không sửa PDF gốc.
- Hai URL có nội dung giống nhau vẫn được giữ theo nguồn; dùng SHA-256 để loại trùng ở bước xử lý sau. Các tệp `previous` là lịch sử tải, không phải đầu vào hiện tại.

## Đối chiếu 14 nhóm đã yêu cầu

| Mục | Trạng thái thu thập |
|---|---|
| 1. Quy chế đào tạo | Đã tải HTML, có nội dung Điều/Khoản |
| 2. Sửa đổi/bổ sung | Đã lưu trang danh mục quy chế; chưa tìm được văn bản sửa đổi riêng, chưa xác minh có hay không |
| 3. Tốt nghiệp | Có trong quy chế và thông báo xét tốt nghiệp tháng 10/2026 kèm PDF |
| 4. Chuẩn đầu ra ngoại ngữ/tin học | Có Điều 23 trong quy chế; bổ sung trang quy định từ K20 và PDF Thông báo 1581/TB-ĐHCN về học tiếng Anh (2024). Chưa đủ văn bản/bảng quy đổi cho mọi khóa/ngành |
| 5. Công tác sinh viên | Đã tải PDF bản công bố 2023; cần rà soát hiệu lực trước khi dùng trả lời hiện hành |
| 6. Rèn luyện | Đã tải PDF bản 2023 và bản từ trang công bố 06/08/2026; cần ưu tiên rà soát bản mới, tránh trộn hai phiên bản |
| 7. Sổ tay | Có toàn văn PDF 2024; trang K22/2026 chỉ là giới thiệu, chưa lấy được toàn văn 2026 |
| 8. Đăng ký học phần | Đã tải HTML/PDF HK1 và HK2 năm học 2026-2027 |
| 9. Thi/phúc khảo | Đã tải toàn văn quy chế thi, hướng dẫn phúc khảo và mẫu đơn |
| 10. Học phí | Có thông báo thời hạn đóng; chưa có bảng mức thu đầy đủ theo ngành/khóa |
| 11. Học bổng/miễn giảm | Đã tải quy định học bổng HTML, danh mục phiên bản và hướng dẫn miễn giảm có PDF |
| 12. Học vụ | Đã tải trang biểu mẫu và các tệp trực tiếp được liên kết |
| 13. Thực tập/khóa luận | Đã tải trang công bố và PDF đính kèm |
| 14. Chương trình đào tạo | Đã bổ sung nhiều phiên bản Kế toán, Kiểm toán, Tài chính, Ngân hàng, Công nghệ tài chính và tài liệu khoa May–Thời trang liên kết. Một số tệp được liên kết không khớp tên khoa nên cần căn cứ nội dung PDF. Chưa đầy đủ toàn trường; cổng CTĐT trả trang ít nội dung, PDF Cơ điện tử vẫn bị 403 |

## Đợt tải bổ sung ngày 19/11/2026

- [x] ~~Tải bổ sung các chương trình đào tạo công khai tìm được và tài liệu học tiếng Anh~~.
- Thêm **80 URL/tệp mới** so với đợt trước: **35 PDF và 45 HTML**; tổng kho hiện tại **125 tệp** gồm **63 PDF, 60 HTML và 2 DOCX**, PDF có tổng cộng **444 trang**.
- Tệp đính kèm và trang công bố được tính riêng; con số này không phải 125 văn bản độc lập. Có 11 PDF cần rà soát OCR.
- [Nhật ký tải bổ sung và các trang danh mục đã khảo sát](../data-pipeline/data/supplement-log.json).
- Đã lấy PDF đề án tuyển sinh 2026 để kiểm tra thêm. Đây là PDF gần như không có lớp văn bản, chưa xác minh có mục học phí; không coi là đã có bảng mức thu.
- Đã tìm trên nguồn chính thức các từ khóa sổ tay 2026, mức thu học phí, sửa đổi quy chế. Chưa xác định được URL tải toàn văn sổ tay 2026, bảng đơn giá học phí theo ngành/khóa hoặc văn bản sửa đổi riêng.
- Thử tải lại `DHCDT21_TCTA.pdf` với Referer của khoa vẫn trả HTTP 403. Không ghi nhận tệp lỗi thành PDF thành công.

## Các phần tiếp tục bổ sung

- [ ] Văn bản sửa đổi riêng của quy chế đào tạo và căn cứ hiệu lực/đối tượng áp dụng.
- [ ] Toàn văn sổ tay 2026 và bảng học phí theo ngành/khóa.
- [ ] Bộ chương trình đào tạo đầy đủ theo ngành/khóa thay vì chỉ một số tài liệu công khai.
- [ ] Rà soát số quyết định, ngày hiệu lực, quan hệ thay thế và đối tượng áp dụng từng văn bản. Metadata chưa chắc chắn phải giữ null.
- [ ] Parser/OCR, làm sạch văn bản, chunk Điều/Khoản, embedding và vector index (các task sau).

## Chạy lại

Từ thư mục gốc dự án, nên sử dụng virtualenv Python riêng:

```sh
python3 -m venv data-pipeline/.venv
data-pipeline/.venv/bin/python -m pip install -r data-pipeline/ingest/requirements-collection.txt
data-pipeline/.venv/bin/python data-pipeline/ingest/collect_sources.py
data-pipeline/.venv/bin/python data-pipeline/ingest/collect_supplement.py
data-pipeline/.venv/bin/python data-pipeline/ingest/verify_sources.py
```

Danh sách URL ban đầu được kiểm soát trong `data-pipeline/ingest/sources.json`; các danh mục và URL bổ sung nằm trong `collect_supplement.py`. Chạy bước bổ sung sau bước ban đầu để hợp nhất lại manifest. Script chỉ mở các danh mục được liệt kê, các bài CTĐT khớp tiêu đề trên trang danh mục hiện tại và PDF/DOCX liên kết trực tiếp; không duyệt hết phân trang, không crawl toàn bộ trường và không truy cập tài khoản sinh viên. HTML lưu độc lập, không kèm đầy đủ CSS/ảnh nên phù hợp làm bản nguồn văn bản, không đảm bảo giao diện offline giống website.
