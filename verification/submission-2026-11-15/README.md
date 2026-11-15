# Bằng chứng hoàn thiện bản nộp nhóm 8 — 15/11/2026

Đây là kết quả thực thi mới, tách biệt các nhật ký lịch sử trong dự án.

| Nội dung | Kết quả | Bằng chứng |
|---|---|---|
| Java 21, MySQL Community 8.4.0 | 104 tests, 15 suites; lỗi/thất bại/bỏ qua đều bằng 0 | `verify-all.log`, `mvn_clean_verify.log`, `../test-results/`, `../last-run.json` |
| Công cụ bảo vệ DB, kiểm tra XML và đóng gói | 9 kiểm thử Python thành công | `python-tests.log` |
| ERD và chuẩn hóa | 14 bảng, 17 quan hệ FK, đối chiếu thuộc tính/khóa/cardinality và lossless join | `erd-verification.log` |
| Hai form nhập, tìm kiếm, hai báo cáo | Chạy 5 màn hình, nhập đúng/sai; ảnh thực tế; đối chiếu SQL | `gui_verification_test_db.log`, `functional_sql_examples_test_db.log`, `../screenshots/` |
| Bền vững dữ liệu | Đối chiếu kết quả qua hai JVM | `persistence_restart_verify.log` |
| Xuất báo cáo nghiệp vụ | PDF doanh thu 2 trang, bán vé 12 trang; tổng seed 35.165.000 VNĐ; đã xem từng trang | `../../reports/exports/*_Mau.pdf` |
| Khởi động ứng dụng chính | Main chạy thực tế trên demo, đóng cửa sổ sau khi chụp ảnh | `main-demo-startup.log`, `main-demo-startup.png` |
| Bảo toàn demo cuối phiên | SHA-256 khớp baseline sau cập nhật trigger đã được cho phép | `final-demo-fingerprint.log` |
| Launcher đóng kèm | Thiếu cấu hình thoát mã 2, hướng dẫn tiếng Việt, chưa kết nối DB | `launcher-no-config.log` |

## Cập nhật trigger được người vận hành cho phép

Xác nhận: “Đồng ý cập nhật trigger, giữ nguyên dữ liệu”. Đã diễn tập backup, restore và refresh trên clone riêng trước khi áp dụng 12 trigger vào `cinema_chain`. Cả 14 bảng giữ nguyên dữ liệu, số dòng và DDL; view giữ nguyên; 12 thân trigger khớp source; 17 bộ đếm bất biến bằng 0. Doanh thu demo giữ nguyên 35.375.000 VNĐ.

SHA-256 tổng trước: `7af5d0052ade56eedd37184d82ce7a00a178bbcaa7806f828e06ed23aaa66d37`.

SHA-256 tổng sau: `e827a6a114e7bf08c130792b34f8209398e09289131f14496d196d4c5568e5d1`.

Hash tổng đổi do định nghĩa trigger. Chi tiết trong `demo-trigger-upgrade/` và `migration-rehearsal/`. Backup dữ liệu gốc nằm ngoài gói nộp. Kiểm tra fingerprint đầy đủ dùng tài khoản quản trị vì tài khoản ứng dụng không có quyền liệt kê trigger.

## Kiểm tra tài liệu và gói nộp

Báo cáo có đúng 7 chương theo rubric và các phụ lục SQL; trang bìa có giảng viên, lớp, nhóm và 5 sinh viên. PDF chính 70 trang; DOCX kết xuất bằng Word 77 trang. Đã xem riêng từng trang của cả hai bản. Công cụ render DOCX dựa trên LibreOffice không có runtime phù hợp trên máy này; đã thay bằng Word COM ẩn và Poppler để kiểm tra bản DOCX thật.

Sau chỉnh sửa cuối, đối chiếu pixel cho thấy chỉ Word trang 49 và PDF trang 4, 5, 6, 43 đổi; đã xem lại cả 5 trang, các trang khác giữ nguyên. Xem `visual-last-fix-diff.json`. ERD có thêm PDF A1/A3 để đọc rõ thuộc tính; nguồn sửa được giữ kèm.

Gói ZIP dùng danh sách cho phép, chứa JAR ứng dụng và 7 thư viện runtime. Đã kiểm CRC, SHA-256 từng tệp trong manifest, lớp Main, 2 font Unicode, metadata trang bìa và nội dung PDF. Không chứa cấu hình mật khẩu, `.env`, file credentials MySQL, backup dữ liệu hay thư mục QA kết xuất trung gian. Kết quả kiểm gói cuối nằm cạnh ZIP tại `reports/submission/package-validation.json`; SHA-256 toàn ZIP có tệp `.zip.sha256` đi kèm.

## Giới hạn

Đây là ứng dụng Swing cho đồ án. Chưa chứng nhận thanh toán/hoàn tiền merchant thật, tải lớn, phục hồi sau mất điện hay đăng nhập/phân quyền cho hệ thống đặt vé online. Không suy diễn các kiểm thử đã chạy thành bảo đảm cho những phạm vi đó. Trọng số rubric không phải điểm tự chấm.
