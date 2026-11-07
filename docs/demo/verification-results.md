# BÁO CÁO KẾT QUẢ KIỂM CHỨNG VÀ ĐỐI SOÁT SỐ LIỆU (VERIFICATION RESULTS)
**(Tài liệu Bằng chứng Thực thi trên MySQL Community Server 8.4.0 LTS — Gate A, B, C, D, E, F)**

---

## 1. Tổng hợp Kết quả Các Cổng Chất lượng Tự động (Quality Gates Summary)

- **Thời điểm thực thi kiểm chứng mới nhất:**
  - Diễn tập trực tiếp `com.cinema.app.Main` trên CSDL demo chính thức **`cinema_chain` (14 bảng chuẩn 3NF):** `2026-11-07 15:14` (múi giờ `+07:00`).
  - Chạy bộ kiểm thử hồi quy tự động 6 bước (`verify_all.ps1`) trên CSDL kiểm thử cô lập **`cinema_chain_test`:** `2026-11-07 15:15:43` (múi giờ `+07:00`).
- **Dấu vân tay SHA-256 bảo toàn CSDL demo `cinema_chain` (bao gồm cả dữ liệu demo vừa nhập qua `Main`) trước và sau toàn bộ quá trình kiểm thử tự động:**
  `7af5d0052ade56eedd37184d82ce7a00a178bbcaa7806f828e06ed23aaa66d37` (**100% bất biến trong phạm vi kiểm tra kết nối TCP MySQL** — xem `verification/logs/cinema_chain_baseline_fingerprint.json`; dấu vân tay mốc seed gốc sạch trước khi nhập liệu demo là `fc060239eb1a23e86c3bd1f4cff556be5d0f36598d70dfe4385da7ca37339c39` tại `verification/logs/cinema_chain_clean_seed_fingerprint.json`).

| Cổng Chất lượng | Nội dung Kiểm chứng | Lệnh / Cơ chế Thực thi | Kết quả | Bằng chứng Lưu trữ |
|---|---|---|---|---|
| **Gate A — Fresh Build & XML Audit** | Xác minh JDK 21, đúng instance **MySQL 8.4.0 LTS** (`datadir=C:/Users/LNB/.mysql/data-8.4`), xóa sạch `TEST-*.xml` cũ trước khi biên dịch và kiểm toán đủ 8 bộ test bắt buộc với `Skipped = 0` | `.\mvnw.cmd clean verify -Pmysql-it` (qua `verify_all.ps1`) | **ĐẠT** (`BUILD SUCCESS`, `40/40 PASS, 0 Skipped`) | `verification/logs/mvn_clean_verify.log`, `verification/test-results/TEST-*.xml` |
| **Gate B — Architecture, Provisioning & Secret Safety** | ArchUnit (5 tests) cấm SQL trong UI & cấm SQL phá hủy trong `src/main/java`; `TestSafetyGuardTest` (6 tests) chứng minh `TestDatabaseGuard` chặn `cinema_chain`, `setup.ps1` chặn `-CleanReset` / chặn reset `cinema_chain`, `verify_all.ps1` chỉ đọc trên `cinema_chain`, và `mysql_spy` xác nhận không lộ mật khẩu trên CLI/env | `ArchitectureComplianceTest` (5/5), `TestSafetyGuardTest` (6/6) | **ĐẠT** | `verification/test-results/TEST-com.cinema.architecture.ArchitectureComplianceTest.xml`, `TEST-com.cinema.unit.TestSafetyGuardTest.xml` |
| **Gate C — True 3NF Integrity, Fingerprint & Concurrency** | Kiểm chứng 14 bảng chuẩn 3NF (`ticket` không có `screening_id`), khóa ghế `screening_seat_lock` chống sửa sai trực tiếp, dấu vân tay SHA-256 phát hiện sửa 1 dòng giữ nguyên `COUNT(*)`, 12 Trigger bảo vệ nghiệp vụ, 12 luồng `CREATE` + 8 luồng `UPDATE` trùng lịch, 20 luồng tranh chấp cùng 1 ghế + hủy/bán lại ghế | `DatabaseIntegrityIT` (15/15), `ScreeningConcurrencyIT` (2/2), `BookingConcurrencyIT` (1/1) | **ĐẠT** | `verification/logs/db_reset_test_verify.log`, `verification/logs/migration_clone_dryrun.log`, `verification/test-results/TEST-com.cinema.integration.*IT.xml` |
| **Gate D — Programmatic Swing EDT GUI, Pagination & 2-JVM Lifecycle** | Điều khiển tự động 5 màn hình Swing trên EDT (`GuiVerificationRunner` — kiểm tra hợp lệ, bị từ chối và lật trang phân trang 50 dòng/trang); kiểm chứng vòng đời 2 tiến trình JVM tách biệt (`AppPersistenceVerificationRunner --session1` → `--session2`); thực thi bộ SQL chức năng R5–R7 | `GuiVerificationRunner`, `AppPersistenceVerificationRunner`, `functional_sql_verification.sql` (`Exit Code = 0`) | **ĐẠT** *(trên EDT tự động; thao tác chuột/phím người thật thực hiện theo checklist `DEMO_GUIDE.md`)* | `verification/screenshots/01_*.png` .. `09_*.png`, `verification/logs/gui_verification_test_db.log`, `verification/logs/persistence_restart_verify.log`, `verification/logs/functional_sql_examples_test_db.log` |
| **Gate E — Report Accuracy & Known-Answer Fixture** | Đối chiếu số liệu Báo cáo Doanh thu & Báo cáo Bán vé và văn bản PDF tiếng Việt với truy vấn SQL độc lập (`35.165.000 VNĐ`, sai lệch `0.00 VNĐ`) và bộ dữ liệu kiểm chứng tính tay (*Known-Answer Fixture*) | `ReportAccuracyIT` (3/3) | **ĐẠT** (`Sai lệch = 0.00 VNĐ`) | `reports/exports/BaoCaoDoanhThu_Mau.pdf`, `reports/exports/BaoCaoBanVe_Mau.pdf` |
| **Gate F — Academic Synchronization** | Đồng bộ 14 bảng chuẩn 3NF, chứng minh Lossless-Join & 3NF theo FD, SQL chức năng R5–R7, báo cáo `.docx`/`.pdf` và kịch bản bảo vệ 15 phút | `REQUIREMENTS_TRACEABILITY.md`, `tools/build_academic_report.py` | **ĐẠT** | `docs/academic-report/BaoCao_QuanLyChuoiRap.docx` & `.pdf` |

---

## 2. Kết quả Thống kê 40 Bài Kiểm thử Tự động trên `cinema_chain_test` — MySQL 8.4.0 LTS (`2026-11-07 15:15:43`)

| Bộ Kiểm thử (Test Class) | Cấp độ Kiểm chứng | Số bài test | Passed | Failed | Errors | Skipped | Nội dung Kiểm chứng Chính |
|---|---|---:|---:|---:|---:|---:|---|
| `com.cinema.architecture.ArchitectureComplianceTest` | Kiến trúc tĩnh (ArchUnit) | 5 | **5** | 0 | 0 | 0 | Cấm SQL trong UI, tách biệt UI/Service/Repository, cấm SQL phá hủy & gọi tiến trình trong `src/main/java` |
| `com.cinema.unit.TestSafetyGuardTest` | Đơn vị & An toàn Tiến trình | 6 | **6** | 0 | 0 | 0 | `TestDatabaseGuard` chặn `cinema_chain`/MariaDB/`root`; `setup.ps1` chặn `-CleanReset`; `verify_all.ps1` không phá hủy; `mysql_spy` xác nhận không lộ mật khẩu CLI/env |
| `com.cinema.unit.ScreeningValidationTest` | Đơn vị (Validation) | 5 | **5** | 0 | 0 | 0 | Kiểm tra hợp lệ thời lượng suất chiếu, giá cơ sở dương, danh sách ghế, thông báo tiếng Việt |
| `com.cinema.integration.DatabaseIntegrityIT` | Tích hợp CSDL MySQL 8.4 | 15 | **15** | 0 | 0 | 0 | Kiểm chứng `ticket` chuẩn 3NF & `screening_seat_lock`, SHA-256 phát hiện sửa 1 dòng, 12 Trigger chống trùng lịch/sai phòng/đổi phòng đã có vé/thanh toán đúp, giá lịch sử, UTF-8 |
| `com.cinema.integration.ScreeningConcurrencyIT` | Tích hợp Đồng thời | 2 | **2** | 0 | 0 | 0 | 12 luồng `CREATE` đồng thời và 8 luồng `UPDATE` đồng thời suất chiếu chồng lấn → đúng 1 thắng |
| `com.cinema.integration.BookingConcurrencyIT` | Tích hợp Đồng thời | 1 | **1** | 0 | 0 | 0 | 20 luồng đồng thời đặt cùng 1 ghế → đúng 1 thắng, **0 bản ghi mồ côi**; hủy đơn giải phóng `screening_seat_lock` để bán lại ghế; chuyển tiếp `PENDING` |
| `com.cinema.integration.ScreeningSearchIT` | Tích hợp Tra cứu & Phân trang | 3 | **3** | 0 | 0 | 0 | Tra cứu tham số hóa kèm ghế trống, danh sách suất `OPEN` đầy đủ và duyệt phân trang `50 dòng/trang` không trùng/sót dòng |
| `com.cinema.integration.ReportAccuracyIT` | Tích hợp Báo cáo & PDF | 3 | **3** | 0 | 0 | 0 | Đối chiếu Báo cáo Doanh thu & Bán vé + PDF tiếng Việt với SQL độc lập và Known-Answer Fixture |
| **TỔNG CỘNG** | **8 Bộ Test Bắt buộc** | **40** | **40** | **0** | **0** | **0** | **40/40 PASS TRÊN `cinema_chain_test` (MYSQL 8.4.0 LTS) — 0 SKIPPED** |

---

## 3. Đối soát Số liệu Tài chính Giữa Ứng dụng, File PDF và SQL Độc lập trên MySQL 8.4 (Gate E)

Trên tập dữ liệu chuẩn của **MySQL Community Server 8.4.0 LTS** (`10_seed.sql` — 4 chi nhánh, 12 phòng, 960 ghế, 20 phim, 160 suất chiếu, 160 đơn đặt vé với 373 vé, **327 khóa ghế hiệu lực** trong `screening_seat_lock` và 175 giao dịch thanh toán):
- **Tổng số đơn đặt vé đã xác nhận và thanh toán thành công (`CONFIRMED` + `SUCCESS`):** `125 đơn` (`292 vé VALID`, trong đó có `15 đơn` từng có 1 giao dịch `FAILED` trước khi thanh toán lại `SUCCESS`, và `10 đơn` đặt lại `23` ghế vừa được giải phóng từ `10 đơn` hủy trước đó).
- **Các đơn bị loại trừ đúng quy tắc khỏi doanh thu:** `15 đơn PENDING` (đang giữ `35` khóa ghế trong `screening_seat_lock` nên số ghế trống toàn kỳ `04/11–10/11` là `12.800 - 292 - 35 = 12.473 ghế`), `10 đơn CANCELLED + FAILED`, `10 đơn CANCELLED + REFUNDED`.
- **Tổng doanh thu từ truy vấn SQL độc lập (`SUM(ticket.unit_price)` của vé `VALID` thuộc đơn `CONFIRMED` có `EXISTS payment SUCCESS`):** **`35.165.000 VNĐ`**.
- **Tổng doanh thu trên Màn hình Xem trước & Tệp PDF `BaoCaoDoanhThu_Mau.pdf`:** **`35.165.000 VNĐ`**.
- **Tổng doanh thu trên Màn hình Xem trước & Tệp PDF `BaoCaoBanVe_Mau.pdf`:** **`35.165.000 VNĐ`**.
- **Chênh lệch (Discrepancy):** **`0,00 VNĐ`**.

---

## 4. Bằng chứng Di chuyển Schema Không Phá hủy trên Bản sao CSDL (`Clone Dry-Run`)

Để bảo vệ tuyệt đối dữ liệu hiện có trong `cinema_chain`, quy trình nâng cấp từ V1 (13 bảng) lên V2 (14 bảng chuẩn 3NF) đã được diễn tập đầy đủ trên CSDL bản sao độc lập `cinema_chain_migration_dryrun` qua `database/migrations/dry_run_migration_on_clone.ps1` (`verification/logs/migration_clone_dryrun.log`):
- **Trước và sau khi nâng cấp tiến (`V2_3nf_ticket_up.sql`):** Mã băm kết nối bảo toàn thông tin (`ticket JOIN booking`) khớp tuyệt đối `ef904b39c8e1b57b812d0b01b1ee76f3c4992c6b0be7f146cc150fb2e0ba3107` trên toàn bộ `373` vé; `screening_seat_lock` nạp chính xác `327` ghế hiệu lực; doanh thu thực thu giữ nguyên `35.165.000,00 VNĐ`.
- **Sau khi hoàn tác ngược (`V2_3nf_ticket_rollback.sql`):** Dấu vân tay SHA-256 trên toàn bộ 13 bảng khớp 100% với bản gốc trước khi nâng cấp.

---

## 5. Kết quả Kiểm chứng Trực tiếp Bản Demo Chính thức trên CSDL `cinema_chain` (Phân biệt rõ với `cinema_chain_test`)

Sau khi được người dùng phê duyệt chủ động chuẩn bị lại CSDL demo, `cinema_chain` đã được khởi tạo trên lược đồ **14 bảng chuẩn 3NF** (`17` FK, `12` Trigger, `4` View) qua `database/prepare_demo_db.ps1` (`verification/logs/cinema_chain_14table_provision.log`) và kiểm chứng trực tiếp điểm vào `com.cinema.app.Main` (`config/application.properties` -> `cinema_chain`, `cinema_app`):
1. **Khởi động `Main` độc lập giữ nguyên dữ liệu (`main_standalone_startup_cinema_chain.log`):** Khởi chạy `.\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"` kết nối `cinema_chain` ở chế độ `NORMAL_READ_ONLY_INIT | DestructiveReset=DISABLED`; mã băm SHA-256 `fc060239eb1a23e86c3bd1f4cff556be5d0f36598d70dfe4385da7ca37339c39` trước và sau khi mở `Main` khớp 100%.
2. **Kiểm chứng 5 màn hình (Hợp lệ + Bị từ chối) trên `cinema_chain` trong Phiên JVM #1 (`demo_cinema_chain_live_verify.log`):**
   - **Form 1 (Quản lý Suất chiếu):** Từ chối giá vé `<= 0` (`[BR-04]`), từ chối giờ kết thúc ngắn hơn thời lượng phim (`[BR-05]`), từ chối chồng lấn lịch chiếu với Suất `#1` (`[BR-06]`), từ chối đổi phòng Suất `#1` đã có vé (`[BR-09]`); tạo thành công Suất chiếu `#161` ngày `2026-11-18` (`09:00 – 11:15`, giá cơ sở `95.000 VNĐ`).
   - **Form 2 (Đặt vé & Chọn ghế):** Từ chối khi chưa chọn ghế (`[BR-08]`); đặt thành công Đơn `#161` (`BK-20261107-151413-101`, ghế `A01` STD `95.000 VNĐ` + `E01` VIP `115.000 VNĐ` = `210.000 VNĐ`, `CARD` + `SUCCESS`); từ chối ghế `A02` vừa bị quầy khác giữ khóa (`[BR-10]`); hủy hoàn tiền Đơn `#162` (`CANCELLED` + `REFUNDED`) giải phóng khóa ghế `A02`.
   - **Màn hình 3 (Tra cứu Suất chiếu):** Từ chối ngày sai định dạng `2026-99-99`; kiểm tra lật trang phân trang `50 dòng/trang`; tra cứu ngày `2026-11-18` trả về đúng Suất `#161` (`80` ghế, `2` ghế đã bán, `78` ghế trống).
   - **Màn hình 4 & 5 (Hai Báo cáo Quản trị & Xuất PDF):** Từ chối khoảng ngày ngược và ngày sai định dạng; xem trước và xuất 2 tệp PDF thực tế trên `cinema_chain` (`reports/exports/BaoCaoDoanhThu_CinemaChain_Demo.pdf` và `reports/exports/BaoCaoBanVe_CinemaChain_Demo.pdf`) đạt tổng thực thu **`35.375.000 VNĐ`** (`35.165.000 VNĐ` seed gốc + `210.000 VNĐ` Đơn `#161` `CONFIRMED`, loại trừ đúng `0 VNĐ` cho Đơn `#162` đã `CANCELLED`/`REFUNDED`), khớp tuyệt đối `0,00 VNĐ` sai lệch với truy vấn SQL độc lập trên `cinema_chain`.
3. **Đóng hoàn toàn ứng dụng và mở lại `Main` trong Phiên JVM #2 mới:** Mở lại `com.cinema.app.Main` bình thường trên `cinema_chain` xác nhận Suất `#161`, Đơn `#161` (`ticket_id=374, 375`, `payment_id=176`), Đơn `#162` (`CANCELLED`) và cả hai báo cáo (`35.375.000 VNĐ`, `126 đơn`, `294 vé`, `161 suất chiếu`) vẫn còn nguyên vẹn 100%.
