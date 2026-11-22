# Tiến độ bản nộp nhóm 8

## Cập nhật bản nộp ngày 23/11/2026

| Hạng mục | Trạng thái | Bằng chứng |
|---|---|---|
| Kiểm tra khóa ghế của đúng đơn SePay | VERIFIED | Commit `bd6340f`. `SandboxPaymentIT` đạt 19/19, gồm đơn SePay và đơn thủ công giữ hai ghế cùng suất. |
| Hồi quy JDK 21 và MySQL 8.4.0 ngày 22/11/2026 | VERIFIED | 109/109 Java, 15 bộ, 0 lỗi và 0 bỏ qua. Năm màn hình, hai PDF, hai JVM và SQL chức năng đạt. [Kết quả](docs/demo/verification-results.md) |
| Báo cáo học phần và slide | Hoàn tất | Word/PDF có bảy phần theo mẫu, SQL và ảnh theo chức năng, bảng phân công nhóm 8. Hoàng Tiến Dũng và Dương Bảo Trung thuyết trình. [Báo cáo PDF](docs/academic-report/BaoCao_QuanLyChuoiRap.pdf) · [Slide](docs/demo/Bao-ve-chuoi-rap.pptx) |

## Hoàn thiện bản nộp 15/11/2026 — lưu trữ

| Mục | Trạng thái | Kết quả và bằng chứng |
|---|---|---|
| Sửa nghiệp vụ/EDT/snapshot/PDF và công cụ kiểm chứng | VERIFIED | JDK21/MySQL8.4;104/104 tests/15suite/0skip,GUI đúng/sai,2JVM,SQL; verification/submission-2026-11-15/verify-all.log |
| Backup,refresh và restore trên clone | VERIFIED | migration-rehearsal/rehearsal-result.json; dữ liệu/DDL/view giữ nguyên,restore fingerprint và refresh lần2 khớp |
| Cập nhật12trigger demo đã được xác nhận | VERIFIED | demo-trigger-upgrade/;17invariant0,12bodymatch,14table data/DDL/view giữ nguyên; Main startup thực tế |
| Báo cáo 7 chương, ERD, SQL, Word/PDF và gói ZIP nộp | VERIFIED | Đã điền 5 SV/GV/lớp; xem PDF 70 trang, Word 77 trang; kiểm manifest SHA-256/CRC/runtime; verification/submission-2026-11-15/README.md và reports/submission/package-validation.json |

Các trạng thái/đường dẫn phía dưới là nhật ký lịch sử, không phải baseline kiểm chứng hiện hành.

## Nhật ký lịch sử

# TASKS.md — Sổ cái Thực thi & Kiểm chứng Dự án (Execution Ledger)

Trạng thái hợp lệ: `TODO` | `IN_PROGRESS` | `BLOCKED` | `VERIFIED`

| Task ID | Vai trò Phụ trách | Phụ thuộc | Trạng thái | Tiêu chí Nghiệm thu (Acceptance Criteria) | Bằng chứng Kiểm chứng (Verification Evidence) | Tiêu chí Chấm điểm |
|---|---|---|---|---|---|---|
| **T00** | Lead Agent | — | `VERIFIED` | Khảo sát môi trường, xác minh đúng instance **MySQL Community Server 8.4.0 LTS** (`%USERPROFILE%\.mysql\mysql-8.4`, `datadir=C:\Users\LNB\.mysql\data-8.4`, cổng `3306`) và chụp dấu vân tay SHA-256 ban đầu của `cinema_chain`. | `verification/logs/cinema_chain_baseline_fingerprint.json` (`c55feb1fc3d4...`), `database/start_mysql84.ps1`, `database/stop_mysql84.ps1`. | Toàn dự án |
| **T01** | Lead Agent | T00 | `VERIFIED` | Gia cố an toàn thực thi: khóa cứng cấm `-CleanReset` / cấm reset `cinema_chain` trong `setup.ps1`, tách `verify_all.ps1` khỏi `setup.ps1`, dùng `--defaults-extra-file` chống lộ mật khẩu CLI/env, kiểm toán XML tươi (`Skipped = 0`). | `TestSafetyGuardTest` (**6/6 PASS**), `ArchitectureComplianceTest` (**5/5 PASS**), `tools/db_fingerprint.py`. | Gate B |
| **A1** | Agent A (DB Architect) | T01 | `VERIFIED` | Phân tích yêu cầu nghiệp vụ & dữ liệu lưu trữ 14 bảng (`docs/requirements/requirements.md`) và danh mục quy tắc nghiệp vụ (`docs/requirements/business-rules.md`) bằng tiếng Việt. | `docs/requirements/requirements.md`, `docs/requirements/business-rules.md` (`BR-01`..`BR-16`). | **R1 (1.0đ)** |
| **A2** | Agent A (DB Architect) | A1 | `VERIFIED` | Thiết kế lại sơ đồ ERD chuẩn Chen và ERD mức logic Crow's Foot 14 bảng chuẩn 3NF (`ticket` không nối dư thừa sang `screening`, bổ sung `screening_seat_lock`) + kết xuất `.png`/`.svg` + thuyết minh (`docs/erd/erd-explanation.md`). | `docs/erd/erd-chen.png`, `docs/erd/erd-chen.svg`, `docs/erd/erd-logical.png`, `docs/erd/erd-logical.svg`, `docs/erd/erd-explanation.md`. | **R2 (2.5đ)** |
| **A3** | Agent A (DB Architect) | A2 | `VERIFIED` | Đặc tả lược đồ cơ sở dữ liệu quan hệ 14 bảng & từ điển dữ liệu 17 khóa ngoại (`docs/relational-model/schema-design.md`) tách biệt rõ mức logic và cột chỉ mục vật lý MySQL 8.4. | `docs/relational-model/schema-design.md`. | **R3 (2.0đ)** |
| **A4** | Agent A (DB Architect) | A3 | `VERIFIED` | Sửa dứt điểm vi phạm 3NF trên `ticket` (`ticket_id -> booking_id -> screening_id`), so sánh phương án, chứng minh Lossless-Join, bảo toàn phụ thuộc hàm, khóa tối thiểu toàn bảng và chuẩn 1NF/2NF/3NF cho đủ 14 quan hệ (`docs/normalization/normalization.md`). | `docs/normalization/normalization.md`. | **R4 (1.0đ)** |
| **G1** | Lead Agent | A1–A4 | `VERIFIED` | Đồng bộ nhất quán 14 bảng chuẩn 3NF giữa Yêu cầu ↔ ERD ↔ Lược đồ quan hệ ↔ Chứng minh 3NF ↔ DDL MySQL 8.4 ↔ Gói migration không phá hủy. | `database/migrations/dry_run_migration_on_clone.ps1`, `verification/logs/migration_clone_dryrun.log`. | **R1–R4 (6.5đ)** |
| **B1** | Agent B (DB Engineer) | G1 | `VERIFIED` | Cập nhật `01_schema.sql` (14 bảng), `01b_foreign_keys.sql` (17 FK), `02_triggers.sql` (12 Trigger đồng bộ & bảo vệ `screening_seat_lock`), `03_views.sql` (4 View) và kiểm chứng trên `cinema_chain_test`. | `verification/logs/db_reset_test_verify.log`. | **R3, R8** |
| **B2** | Agent B (DB Engineer) | B1 | `VERIFIED` | Cập nhật bộ dữ liệu mẫu tất định chuẩn 3NF (`10_seed.sql`): 4 chi nhánh, 12 phòng, 960 ghế, 20 phim, 160 suất chiếu, 160 đơn, 373 vé (`327` khóa ghế hiệu lực), 175 thanh toán; bảo toàn bất biến `cinema_chain`. | `DatabaseIntegrityIT.verifySeedDataScale`, `verification/logs/cinema_chain_baseline_fingerprint.json`. | **R1, R3, R8** |
| **B3** | Agent B & Agent D | B2 | `VERIFIED` | Kiểm chứng toàn vẹn CSDL 14 bảng trên MySQL 8.4.0: kiểm tra cấu trúc 3NF của `ticket`, bảo vệ `screening_seat_lock` chống sửa trực tiếp, kiểm tra SHA-256 phát hiện sửa 1 dòng giữ nguyên `COUNT(*)`, kiểm chứng 12 Trigger (`DatabaseIntegrityIT` 15/15 PASS). | `verification/test-results/TEST-com.cinema.integration.DatabaseIntegrityIT.xml` (**15/15 PASS**). | **Gate C, R4, R8** |
| **C1** | Agent C (Java Engineer) | T01 | `VERIFIED` | Cập nhật `ConnectionProvider` (cảnh báo tiếng Việt rõ ràng nếu CSDL chưa nâng cấp `screening_seat_lock`), `PageResult`, `SearchPageResult` trên JDK 21. | `verification/logs/mvn_clean_verify.log` (`BUILD SUCCESS`, `40/40 PASS, Skipped=0`). | **Gate A, Gate B** |
| **C2** | Agent C (Java Engineer) | C1 | `VERIFIED` | Loại bỏ hoàn toàn giới hạn ngầm `LIMIT 1000` và bổ sung phân trang tường minh `50 dòng/trang` + bộ lọc nhanh trên giao diện Swing FlatLaf. | `ScreeningSearchIT` (**3/3 PASS**), `GuiVerificationRunner`. | **R5–R6, Gate D** |
| **F1** | Agent C (Java Engineer) | C1, C2 | `VERIFIED` | Màn hình nhập liệu 1: **Quản lý Suất chiếu** (`ScreeningRepository`, `ScreeningService`, `ScreeningManagementPanel`) truy vấn vé chuẩn 3NF (`ticket JOIN booking`), phân trang 50 dòng/trang, chống trùng lịch 12 luồng `CREATE` & 8 luồng `UPDATE`. | `ScreeningValidationTest` (5/5), `ScreeningConcurrencyIT` (2/2), `verification/screenshots/01_screening_management_valid.png`, `02_screening_management_overlap_rejected.png`. | **R5 (0.5đ)** |
| **F2** | Agent C (Java Engineer) | C1, C2 | `VERIFIED` | Màn hình nhập liệu 2: **Đặt vé & Chọn ghế** (`BookingRepository`, `BookingService`, `TicketBookingPanel`) dùng `screening_seat_lock` & `ticket` chuẩn 3NF, bộ lọc nhanh Chi nhánh/Ngày, 20 luồng tranh chấp ghế + hủy/bán lại ghế. | `BookingConcurrencyIT` (1/1), `verification/screenshots/03_ticket_booking_seat_selection.png`, `04_ticket_booking_success.png`, `04b_ticket_booking_occupied_rejected.png`. | **R5 (0.5đ)** |
| **F3** | Agent C (Java Engineer) | C1, C2 | `VERIFIED` | Màn hình tìm kiếm: **Tra cứu Suất chiếu** (`ScreeningSearchRepository`, `ScreeningSearchService`, `ScreeningSearchPanel`) tổng hợp KPI toàn tập kết quả + phân trang `50 dòng/trang`. | `ScreeningSearchIT` (3/3), `verification/screenshots/05_screening_search.png`. | **R6 (0.5đ)** |
| **R1** | Agent E (Report Eng) | C1 | `VERIFIED` | Báo cáo 1: **Báo cáo Doanh thu Chuỗi rạp** (Người lập: Kế toán → Người nhận: Giám đốc Kinh doanh), đối chiếu SQL độc lập 3NF + Known-Answer Fixture + xuất PDF tiếng Việt. | `reports/exports/BaoCaoDoanhThu_Mau.pdf`, `verification/screenshots/06_revenue_report_preview.png`, `08_exported_revenue_pdf_page1.png`, `ReportAccuracyIT` (`35.165.000 VNĐ`, sai lệch `0.00`). | **R7 (0.5đ), Gate E** |
| **R2** | Agent E (Report Eng) | C1 | `VERIFIED` | Báo cáo 2: **Báo cáo Tình hình Bán vé & Tỷ lệ Lấp đầy** (Người lập: Quản lý Chi nhánh → Người nhận: Ban Điều hành & Quản lý Kinh doanh), đối chiếu SQL độc lập 3NF + xuất PDF tiếng Việt. | `reports/exports/BaoCaoBanVe_Mau.pdf`, `verification/screenshots/07_ticket_sales_report_preview.png`, `09_exported_sales_pdf_page1.png`, `ReportAccuracyIT`. | **R7 (0.5đ), Gate E** |
| **E1** | Agent E (Docs Engineer) | F1–F3, R1–R2 | `VERIFIED` | Đặc tả SQL chức năng R5–R7 (`docs/sql-features/functional-sql-mapping.md`, `functional_sql_verification.sql`), kịch bản bảo vệ 15 phút (`DEMO_GUIDE.md`) và xuất bản lại `BaoCao_QuanLyChuoiRap.docx` & `.pdf`. | `docs/sql-features/functional-sql-mapping.md`, `verification/logs/functional_sql_examples_test_db.log`, `docs/academic-report/BaoCao_QuanLyChuoiRap.docx` & `.pdf`. | **R8 (1.0đ)** |
| **Z** | Lead Agent | E1 | `VERIFIED` | Chạy `database/verify_all.ps1` kiểm chứng 6 bước trên `cinema_chain_test` (`40/40 PASS, 0 Skipped`), kiểm tra trực quan ảnh chụp & trang PDF báo cáo học thuật, xác nhận `cinema_chain` giữ nguyên dấu vân tay SHA-256. | `REQUIREMENTS_TRACEABILITY.md`, `docs/demo/verification-results.md`, `verification/logs/cinema_chain_baseline_fingerprint.json` (`7af5d0052ade...`). | **Gate F (10.0đ)** |
| **Z2** | Lead Agent | Z | `VERIFIED` | Hoàn tất CSDL demo chính thức `cinema_chain` với lược đồ 14 bảng chuẩn 3NF (`prepare_demo_db.ps1`); khởi chạy `com.cinema.app.Main` cấu hình chuẩn `cinema_chain`, kiểm chứng đủ 5 màn hình (hợp lệ + bị từ chối), xuất 2 PDF đối chiếu SQL độc lập (`0.00 VNĐ` sai lệch) và đóng/mở qua 2 tiến trình JVM xác nhận giữ nguyên dữ liệu demo vừa nhập. | `verification/logs/cinema_chain_14table_provision.log`, `verification/logs/main_standalone_startup_cinema_chain.log`, `verification/logs/demo_cinema_chain_live_verify.log`, `reports/exports/BaoCaoDoanhThu_CinemaChain_Demo.pdf`, `reports/exports/BaoCaoBanVe_CinemaChain_Demo.pdf`, `verification/screenshots/demo_cinema_chain_01_*.png` .. `10_*.png`. | **Bàn giao Demo Thực tế (`cinema_chain`)** |


## Kiểm thử dataset bổ sung — 2026-11-13

Cinema Sample Database 1K: **3/3 PASS, Failures=0, Errors=0, Skipped=0**;
1.000 vé, 450 đơn, 15 suất. Bộ hồi quy gốc: **40/40 PASS**;
`verify_all.ps1` hoàn tất cả 6 bước trên MySQL 8.4/JDK 21.
SHA-256 `cinema_chain` trước/sau khớp
`7af5d0052ade56eedd37184d82ce7a00a178bbcaa7806f828e06ed23aaa66d37`.
Bằng chứng: `verification/dataset-sample/README.md`, `manifest.json`,
`result.json`, `TEST-CinemaDatasetCheck.xml`, `dataset-test.log` và `verify-all.log`.
R5: chặn ghế trùng qua service và SQL; R6: phân trang/số ghế;
R7: đối chiếu ledger CSV với báo cáo từng chi nhánh.
GUI/PDF chỉ chạy lại với fixture gốc; chưa kiểm thử GUI trên dataset,
chưa đo tải 100K/1M. Không đổi trạng thái VERIFIED của các mục hiện có.


## Kiểm thử mức 2 và 4 — 2026-11-13

Bổ sung `PaymentRecoveryIT`: **13/13 PASS** (8 test trạng thái thanh toán,
5 test sự cố/rollback), không lỗi và không bỏ qua. Đã tái hiện lỗi trước sửa
(`verification/payment-recovery/red.log`, `RED-PaymentRecoveryIT.xml`).
Sửa `TransactionTemplate` rollback cả Error; hai trigger payment chặn SUCCESS
sai số tiền hoặc đơn đã hủy. Không đổi cấu trúc bảng, kiểu cột hoặc tập trạng thái.
Toàn bộ bộ test mặc định: **53/53 PASS, Failures=0, Errors=0, Skipped=0**;
dataset 1K chạy riêng **3/3 PASS**. `verify_all.ps1` hoàn tất 6 bước,
bao gồm GUI/PDF, hai JVM và SQL chức năng. Đã xem ảnh PDF được kết xuất.
Dữ liệu kiểm thử đã dọn; SHA-256 `cinema_chain` trước/sau khớp
`7af5d0052ade56eedd37184d82ce7a00a178bbcaa7806f828e06ed23aaa66d37`.
Bằng chứng và giới hạn: `verification/payment-recovery/README.md`, `result.json`,
`GREEN-PaymentRecoveryIT.xml`, `verify-all.log`, `DATASET-CinemaDatasetCheck.xml`.
Liên hệ BR-13/R5: từ chối thanh toán sai và lặp; BR-10/R5: không để khóa ghế
mồ côi sau sự cố; BR-14/R7: giữ tính nhất quán tiền/vé khi chuyển trạng thái.
Trigger mới chỉ áp dụng trên `cinema_chain_test`; **DB demo chưa được migration**.
Chưa thử mất điện, crash MySQL hoặc mất phản hồi đúng lúc COMMIT.
Không thay đổi trạng thái VERIFIED của các mục hiện có.


## Kiểm thử nghiệp vụ chuyên sâu và VNPay Sandbox — 2026-11-13

Bộ mặc định **95/95 PASS, Failures=0, Errors=0, Skipped=0** (XML tươi, 12 suite);
dataset 1K chạy riêng **3/3 PASS**. Có 17 test BusinessRulesIT, 10 test giao thức
VnpayGatewayTest và 15 test SandboxPaymentIT (HTTP/JDBC thật, chữ ký provider giả lập).
Đã chạy lại verify_all.ps1 đủ 6 bước, kiểm tra giao diện sandbox thiếu merchant,
xem ảnh thực tế và SQL độc lập trên toàn DB test: 9 bộ đếm sai lệch/fixture đều 0.
Lỗi trước sửa và kết quả sau sửa: verification/business-audit/README.md, red.log,
state-red.log, scheduling-red.log, verify-all.log, result.json và sandbox-ui-unconfigured.png.
Sửa kiểm tra chi nhánh hoạt động, bảo toàn doanh thu lịch sử, validation ghế,
khóa lịch SQL đồng thời, bất biến giá vé, bảo vệ vé/đơn đã hủy, đồng bộ hủy và
điều kiện xác nhận thanh toán. Giữ 14 bảng, 12 trigger, tập trạng thái hiện có.
Đặc tả/giới hạn sandbox: docs/requirements/sandbox-payment.md.
SHA-256 cinema_chain trước/sau khớp
7af5d0052ade56eedd37184d82ce7a00a178bbcaa7806f828e06ed23aaa66d37.
Chưa có merchant: chưa kiểm chứng giao dịch VNPay thật, public IPN hoặc refund/query.
Trigger mới chỉ áp dụng DB test; DB demo chưa migration. Không đổi trạng thái
VERIFIED hiện có để suy diễn gateway đã VERIFIED.

## Dọn mã database — 2026-11-15

Đã hoàn tất refactor nhỏ: bỏ SQL bootstrap không được gọi; tái sử dụng logic
khởi tạo cho Normal/ResetTestDb, giữ nguyên các chốt bảo vệ và schema.
104 test / 15 suite chạy mới PASS, Skipped=0; GUI/PDF/2-JVM/SQL đạt;
SHA-256 cinema_chain trước/sau khớp. Đã xem ảnh/PDF mới, giữ lại tệp mẫu cũ.
Giới hạn: clean mặc định bị ACL backup chặn; lần kiểm chứng thành công dùng
maven.clean.excludeDefaultDirectories=true, chưa xác nhận xóa toàn bộ target.
Không nâng trạng thái VERIFIED hiện có. Bằng chứng: verification/db-cleanup/README.md.
