# BÁO CÁO KẾT QUẢ KIỂM CHỨNG VÀ ĐỐI SOÁT SỐ LIỆU (VERIFICATION RESULTS)
**(Tài liệu Bằng chứng Thực thi trên MySQL Community Server 8.4.0 LTS — Gate A, B, C, D, E, F)**

---

## 1. Tổng hợp Kết quả Các Cổng Chất lượng Tự động (Quality Gates Summary)

| Cổng Chất lượng | Nội dung Kiểm chứng | Lệnh / Cơ chế Thực thi | Kết quả | Bằng chứng Lưu trữ |
|---|---|---|---|---|
| **Gate A — Environment & Build** | Xác minh JDK 21 (`21.0.13`), đúng instance **MySQL 8.4.0 LTS** (`datadir=C:/Users/LNB/.mysql/data-8.4`), biên dịch và chạy toàn bộ test với `Skipped = 0` | `.\mvnw.cmd clean verify -Pmysql-it` | **PASSED** (`BUILD SUCCESS`, `34/34 PASS, 0 Skipped`) | `verification/logs/mvn_clean_verify.log` |
| **Gate B — Architecture & Safety Guard** | ArchUnit (5 bài test) cấm SQL trong UI, phân tầng nghiêm ngặt, cấm lệnh `DROP`/`TRUNCATE`/`DELETE` & cấm `ProcessBuilder`/`Runtime` trong `src/main/java`; `TestSafetyGuardTest` (3 bài test) chứng minh `TestDatabaseGuard` chặn `cinema_chain`, MariaDB, `root` và không lộ mật khẩu | `ArchitectureComplianceTest` (5/5), `TestSafetyGuardTest` (3/3) | **PASSED** | `verification/test-results/TEST-com.cinema.architecture.ArchitectureComplianceTest.xml`, `TEST-com.cinema.unit.TestSafetyGuardTest.xml` |
| **Gate C — MySQL 8.4 Integrity & Concurrency** | Kiểm chứng PK, FK, UNIQUE, CHECK, Generated Columns, Savepoint rollback, 6 Triggers: chống trùng lịch trên cả **CREATE** (12 luồng) & **UPDATE** (8 luồng), chặn ghế sai phòng, **chặn đổi phòng suất chiếu đã có vé (`BR-09`)**, chặn chuyển sang phòng ngừng hoạt động (`BR-01`), 20 luồng tranh chấp cùng 1 ghế (1 thắng, **0 đơn/thanh toán mồ côi**), hủy vé & bán lại ghế, chặn thanh toán đúp & chặn xác nhận thanh toán cho đơn đã hủy, bảo toàn giá vé lịch sử | `DatabaseIntegrityIT` (13/13), `ScreeningConcurrencyIT` (2/2), `BookingConcurrencyIT` (1/1) | **PASSED** | `verification/logs/db_setup_and_verify.log`, `verification/logs/bug_reproduction_before_fix.log`, `verification/test-results/TEST-com.cinema.integration.*IT.xml` |
| **Gate D — Real GUI & Persistence Lifecycle** | Chạy thật cả 5 màn hình Swing (`GuiVerificationRunner`) kiểm tra đủ cả kịch bản **hợp lệ** và **bị từ chối**; chạy vòng đời 2 tiến trình JVM tách biệt (`AppPersistenceVerificationRunner --session1` → đóng hoàn toàn → `--session2`) xác nhận 100% dữ liệu và báo cáo khớp trước/sau khi mở lại ứng dụng; xác nhận `cinema_chain` bất biến | `GuiVerificationRunner`, `AppPersistenceVerificationRunner` (`Exit Code = 0`) | **PASSED** | `verification/screenshots/01_*.png` .. `09_*.png`, `verification/logs/gui_verification_test_db.log`, `verification/logs/persistence_restart_verify.log` |
| **Gate E — Report Accuracy & Known-Answer Fixture** | Đối chiếu số liệu Báo cáo Doanh thu & Báo cáo Bán vé và văn bản PDF tiếng Việt với **truy vấn SQL độc lập trực tiếp trong lớp test** (`35.165.000 VNĐ`, sai lệch `0.00 VNĐ`) và **bộ dữ liệu kiểm chứng tính tay (Known-Answer Fixture)** | `ReportAccuracyIT` (3/3) | **PASSED** (`Sai lệch = 0.00 VNĐ`) | `reports/exports/BaoCaoDoanhThu_Mau.pdf`, `reports/exports/BaoCaoBanVe_Mau.pdf` |
| **Gate F — Academic Compliance** | Đối chiếu toàn bộ 8 tiêu chí chấm điểm R1–R8 với ERD, chuẩn hóa 3NF, mã nguồn, kết quả 34 bài kiểm thử, ảnh chụp và báo cáo học thuật | `REQUIREMENTS_TRACEABILITY.md`, `database/verify_all.ps1` | **PASSED** | `docs/academic-report/BaoCao_QuanLyChuoiRap.docx` & `.pdf` |

---

## 2. Kết quả Thống kê Bộ Kiểm thử Tự động trên MySQL 8.4.0 LTS (JUnit 5 & Failsafe)

| Bộ Kiểm thử (Test Class) | Số bài test | Passed | Failed | Errors | Skipped | Nội dung Kiểm chứng Chính |
|---|---:|---:|---:|---:|---:|---|
| `com.cinema.architecture.ArchitectureComplianceTest` | 5 | **5** | 0 | 0 | 0 | Cấm SQL trong UI, tách biệt UI/Service/Repository, cấm SQL phá hủy & gọi tiến trình trong `src/main/java` |
| `com.cinema.unit.TestSafetyGuardTest` | 3 | **3** | 0 | 0 | 0 | Chốt chặn `TestDatabaseGuard` từ chối `cinema_chain`, MariaDB, `root` và ẩn mật khẩu trong `AppConfig` |
| `com.cinema.unit.ScreeningValidationTest` | 5 | **5** | 0 | 0 | 0 | Kiểm tra hợp lệ thời lượng suất chiếu, giá cơ sở dương, danh sách ghế |
| `com.cinema.integration.DatabaseIntegrityIT` | 13 | **13** | 0 | 0 | 0 | Xác nhận MySQL `8.4.0`, Trigger chống trùng lịch (`INSERT` & `UPDATE`), chặn đổi phòng khi đã có vé, chặn phòng `active=0`, chống bán trùng ghế, chống thanh toán đúp, chặn xác nhận thanh toán đơn đã hủy, FK/CHECK/Savepoint rollback, giá lịch sử, chuẩn UTF-8 |
| `com.cinema.integration.ScreeningConcurrencyIT` | 2 | **2** | 0 | 0 | 0 | 12 luồng `CREATE` đồng thời và 8 luồng `UPDATE` đồng thời suất chiếu chồng lấn → đúng 1 thắng + kiểm tra chặn đổi phòng |
| `com.cinema.integration.BookingConcurrencyIT` | 1 | **1** | 0 | 0 | 0 | 20 luồng đồng thời đặt cùng 1 ghế → đúng 1 thắng, **0 bản ghi mồ côi**; hủy đơn & bán lại ghế; chuyển trạng thái `PENDING → FAILED / SUCCESS` |
| `com.cinema.integration.ScreeningSearchIT` | 2 | **2** | 0 | 0 | 0 | Tra cứu suất chiếu tham số hóa kèm ghế trống + kiểm chứng danh sách suất `OPEN` (`> 120` dòng) không bị cắt cụt |
| `com.cinema.integration.ReportAccuracyIT` | 3 | **3** | 0 | 0 | 0 | Đối chiếu Báo cáo Doanh thu & Bán vé + PDF tiếng Việt với SQL độc lập và Known-Answer Fixture |
| **TỔNG CỘNG** | **34** | **34** | **0** | **0** | **0** | **100% PASS TRÊN `cinema_chain_test` (MYSQL 8.4.0 LTS) — 0 SKIPPED** |

---

## 3. Đối soát Số liệu Tài chính Giữa Ứng dụng, File PDF và SQL Độc lập trên MySQL 8.4 (Gate E)

Trên tập dữ liệu chuẩn của **MySQL Community Server 8.4.0 LTS** (`10_seed.sql` — 4 chi nhánh, 12 phòng, 960 ghế, 20 phim, 160 suất chiếu, 160 đơn đặt vé với 373 vé và 175 giao dịch thanh toán):
- **Tổng số đơn đặt vé đã xác nhận và thanh toán thành công (`CONFIRMED` + `SUCCESS`):** `125 đơn` (trong đó có `15 đơn` từng có 1 giao dịch `FAILED` trước khi thanh toán lại `SUCCESS`, và `10 đơn` đặt lại các ghế vừa được giải phóng từ `10 đơn` hủy trước đó — tổng cộng `23` ghế bán lại hợp lệ).
- **Các đơn bị loại trừ đúng quy tắc khỏi doanh thu:** `15 đơn PENDING` (đang giữ `35` ghế `VALID` nên số ghế trống toàn kỳ là `12.800 - 292 - 35 = 12.473 ghế`), `10 đơn CANCELLED + FAILED`, `10 đơn CANCELLED + REFUNDED`.
- **Tổng doanh thu từ truy vấn SQL độc lập (`SUM(ticket.unit_price)` của vé `VALID` thuộc đơn `CONFIRMED` có `EXISTS payment SUCCESS`):** **`35.165.000 VNĐ`**.
- **Tổng doanh thu trên Màn hình Xem trước & Tệp PDF `BaoCaoDoanhThu_Mau.pdf`:** **`35.165.000 VNĐ`**.
- **Tổng doanh thu trên Màn hình Xem trước & Tệp PDF `BaoCaoBanVe_Mau.pdf`:** **`35.165.000 VNĐ`**.
- **Chênh lệch (Discrepancy):** **`0,00 VNĐ`** (Khớp tuyệt đối 100%).
