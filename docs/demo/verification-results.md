# BÁO CÁO KẾT QUẢ KIỂM CHỨNG VÀ ĐỐI SOÁT SỐ LIỆU (VERIFICATION RESULTS)
**(Tài liệu Bằng chứng Thực thi trên MySQL Community Server 8.4.0 LTS — Gate A, B, C, D, E, F)**

---

## 1. Tổng hợp Kết quả Các Cổng Chất lượng Tự động (Quality Gates Summary)

| Cổng Chất lượng | Nội dung Kiểm chứng | Lệnh / Cơ chế Thực thi | Kết quả | Bằng chứng Lưu trữ |
|---|---|---|---|---|
| **Gate A — Build** | Biên dịch mã nguồn Java 21 LTS (`21.0.13`) và đóng gói JAR qua Maven | `.\mvnw.cmd clean verify` | **PASSED** (`BUILD SUCCESS`, Exit Code `0`) | `verification/logs/mvn_clean_verify.log` |
| **Gate B — Architecture** | Kiểm tra tự động bằng ArchUnit: không có SQL trong UI, phân tầng nghiêm ngặt, dùng tài khoản phân quyền tối thiểu `cinema_app`, không mã hóa cứng mật khẩu trong mã nguồn | `ArchitectureComplianceTest` (3/3 tests) | **PASSED** | `verification/test-results/TEST-com.cinema.architecture.ArchitectureComplianceTest.xml` |
| **Gate C — MySQL 8.4 Integrity & Concurrency** | Xác nhận `SELECT VERSION()` là **MySQL 8.4.0** (`MySQL Community Server - GPL`, không phải MariaDB); kiểm chứng PK, FK, UNIQUE, CHECK, Generated Columns, 6 Triggers, chống trùng lịch đồng thời (12 luồng), chống bán trùng ghế đồng thời (20 luồng), hủy vé & bán lại ghế, bảo toàn giá lịch sử, chuẩn UTF-8 | `DatabaseIntegrityIT` (8/8), `ScreeningConcurrencyIT` (1/1), `BookingConcurrencyIT` (1/1) | **PASSED** | `verification/logs/db_setup_and_verify.log`, `verification/test-results/TEST-com.cinema.integration.*IT.xml` |
| **Gate D — Application Behavior** | Khởi chạy ứng dụng Swing thực tế kết nối trực tiếp **MySQL 8.4.0 LTS**, thực hiện đầy đủ các luồng thao tác trên 5 màn hình và chụp ảnh màn hình xác thực | `GuiVerificationRunner` (Exit Code `0`) | **PASSED** | `verification/screenshots/01_*.png` .. `09_*.png`, `verification/logs/gui_verification.log` |
| **Gate E — Report Accuracy** | Đối chiếu số liệu Báo cáo Doanh thu & Báo cáo Bán vé trên MySQL 8.4.0 với truy vấn SQL độc lập và trích xuất văn bản tiếng Việt từ tệp PDF | `ReportAccuracyIT` (2/2) | **PASSED** (`Sai lệch = 0.00 VNĐ`) | `reports/exports/BaoCaoDoanhThu_Mau.pdf`, `reports/exports/BaoCaoBanVe_Mau.pdf` |
| **Gate F — Academic Compliance** | Đối chiếu toàn bộ 8 tiêu chí chấm điểm R1–R8 với ERD, chuẩn hóa 3NF, mã nguồn, kết quả kiểm thử trên MySQL 8.4.0 LTS, ảnh chụp và báo cáo học thuật | `REQUIREMENTS_TRACEABILITY.md` | **PASSED** | `docs/academic-report/BaoCao_QuanLyChuoiRap.docx` & `.pdf` |

---

## 2. Kết quả Thống kê Bộ Kiểm thử Tự động trên MySQL 8.4.0 LTS (JUnit 5 & Failsafe)

| Bộ Kiểm thử (Test Class) | Số bài test | Passed | Failed | Errors | Nội dung Kiểm chứng Chính |
|---|---:|---:|---:|---:|---|
| `com.cinema.architecture.ArchitectureComplianceTest` | 3 | **3** | 0 | 0 | Cấm SQL trong UI, tách biệt UI/Service/Repository |
| `com.cinema.unit.ScreeningValidationTest` | 5 | **5** | 0 | 0 | Kiểm tra hợp lệ thời lượng suất chiếu, giá cơ sở, danh sách ghế |
| `com.cinema.integration.DatabaseIntegrityIT` | 8 | **8** | 0 | 0 | Xác nhận `SELECT VERSION() = 8.4.0` (MySQL Server), quy mô dữ liệu mẫu, Trigger chống trùng lịch, ghế sai phòng, vé trùng, thanh toán đúp, giá lịch sử, chuẩn UTF-8 |
| `com.cinema.integration.ScreeningConcurrencyIT` | 1 | **1** | 0 | 0 | 12 luồng đồng thời tạo suất chiếu chồng lấn trên MySQL 8.4 $\rightarrow$ đúng 1 thắng, 11 bị chặn |
| `com.cinema.integration.BookingConcurrencyIT` | 1 | **1** | 0 | 0 | 20 luồng đồng thời đặt cùng 1 ghế trên MySQL 8.4 $\rightarrow$ đúng 1 thắng, 19 bị chặn; hủy đơn & bán lại ghế; chuyển trạng thái thanh toán |
| `com.cinema.integration.ScreeningSearchIT` | 1 | **1** | 0 | 0 | Tra cứu suất chiếu tham số hóa & kiểm tra số ghế trống trên MySQL 8.4 |
| `com.cinema.integration.ReportAccuracyIT` | 2 | **2** | 0 | 0 | Đối chiếu Báo cáo Doanh thu & Bán vé với SQL độc lập trên MySQL 8.4 + Kiểm chứng PDF tiếng Việt |
| **TỔNG CỘNG** | **21** | **21** | **0** | **0** | **100% PASS TRÊN MYSQL COMMUNITY SERVER 8.4.0 LTS** |

---

## 3. Đối soát Số liệu Tài chính Giữa Ứng dụng, File PDF và SQL Độc lập trên MySQL 8.4 (Gate E)

Trên tập dữ liệu khởi tạo chuẩn nạp vào **MySQL Community Server 8.4.0 LTS** (`10_seed.sql` — 4 chi nhánh, 12 phòng, 960 ghế, 20 phim, 160 suất chiếu, 160 đơn đặt vé với 373 vé và 175 giao dịch thanh toán):
- **Tổng số đơn đặt vé đã xác nhận và thanh toán thành công (`CONFIRMED` + `SUCCESS`):** `125 đơn` (trong đó có `15 đơn` từng có 1 giao dịch `FAILED` trước khi thanh toán `SUCCESS`, và `10 đơn` đặt lại các ghế vừa được giải phóng từ `10 đơn` hủy trước đó).
- **Các đơn bị loại trừ đúng quy tắc khỏi doanh thu:** `15 đơn PENDING`, `10 đơn CANCELLED + FAILED`, `10 đơn CANCELLED + REFUNDED`.
- **Tổng doanh thu từ truy vấn SQL độc lập (`SUM(ticket.unit_price)` của vé `VALID` thuộc đơn `CONFIRMED` có `EXISTS payment SUCCESS`):** **`35.165.000 VNĐ`**.
- **Tổng doanh thu trên Màn hình Xem trước & Tệp PDF `BaoCaoDoanhThu_Mau.pdf`:** **`35.165.000 VNĐ`**.
- **Tổng doanh thu trên Màn hình Xem trước & Tệp PDF `BaoCaoBanVe_Mau.pdf`:** **`35.165.000 VNĐ`**.
- **Chênh lệch (Discrepancy):** **`0,00 VNĐ`** (Khớp tuyệt đối 100%).
