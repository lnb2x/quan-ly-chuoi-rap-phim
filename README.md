# HỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM CINEVIET
**(Dự án Học phần Cơ sở dữ liệu — Java 21 LTS • Java Swing FlatLaf • MySQL 8.4 LTS • Apache PDFBox)**

---

## 1. Giới thiệu Dự án

**Hệ thống Quản lý Chuỗi Rạp Chiếu Phim CineViet** là ứng dụng Desktop hoàn chỉnh phục vụ quản lý vận hành chuỗi rạp chiếu phim đa chi nhánh, được thiết kế và kiểm chứng theo phương pháp **Verification-First Engineering**.

Dự án đáp ứng đầy đủ 8 tiêu chí chấm điểm học thuật (**R1 – R8**, tổng **10,0 điểm**, đặc biệt ưu tiên chiều sâu thiết kế & chứng minh học thuật của **R1 – R4 = 6,5 điểm**):
- **R1 (1,0đ):** Phân tích yêu cầu nghiệp vụ & dữ liệu cần lưu trữ (`docs/requirements/requirements.md`, `docs/requirements/business-rules.md`).
- **R2 (2,5đ):** Sơ đồ Thực thể – Liên kết (ERD) hoàn chỉnh theo cả chuẩn **Chen** và **Crow's Foot** (`docs/erd/erd-chen.puml/.png/.svg`, `docs/erd/erd-logical.puml/.png/.svg`, `docs/erd/erd-explanation.md`).
- **R3 (2,0đ):** Lược đồ CSDL quan hệ **14 bảng đạt chuẩn 3NF thực sự** và từ điển dữ liệu 17 khóa ngoại (`docs/relational-model/schema-design.md`, `database/schema/01_schema.sql`, `01b_foreign_keys.sql`, `02_triggers.sql`, `03_views.sql`).
- **R4 (1,0đ):** Phân tích phụ thuộc hàm, chứng minh kết nối không mất thông tin (*Lossless-Join*), tìm khóa dự tuyển tối thiểu toàn bảng và chứng minh đạt chuẩn **1NF, 2NF, 3NF** cho cả 14 quan hệ — đã xử lý triệt để phụ thuộc bắc cầu trên `ticket` bằng quan hệ `screening_seat_lock` (`docs/normalization/normalization.md`).
- **R5 (1,0đ):** Hai màn hình nhập liệu vận hành có kiểm tra hợp lệ đúng/sai và an toàn giao dịch đồng thời:
  1. **Màn hình Quản lý Suất chiếu** (`ScreeningManagementPanel`) — phân trang tường minh `50 dòng/trang`, kiểm tra thời lượng phim, giá cơ sở, chặn đổi phòng hoặc hủy suất chiếu đã có vé (`BR-08`, `BR-09`), chống chồng lấn lịch chiếu (`SELECT ... FOR UPDATE` + Trigger `BR-06`).
  2. **Màn hình Đặt vé & Chọn ghế trực quan** (`TicketBookingPanel`) — bộ lọc nhanh Chi nhánh + Ngày chiếu hiển thị đủ tổng số suất mở bán, sơ đồ 80 ghế trực quan, hỗ trợ khách thành viên và khách vãng lai, giao dịch nguyên tử (`booking` + `ticket` + `payment`), chống bán trùng ghế đồng thời (`BR-10`), hủy đơn & giải phóng ghế để bán lại (`BR-11`).
- **R6 (0,5đ):** Một màn hình tìm kiếm vận hành (**Tra cứu Suất chiếu & Kiểm tra Ghế trống** — `ScreeningSearchPanel`) sử dụng truy vấn tham số hóa (`PreparedStatement`) kèm tổng hợp KPI và phân trang `50 dòng/trang`.
- **R7 (1,0đ):** Hai báo cáo quản trị thực tế xem trên màn hình (Preview) và xuất tệp **PDF tiếng Việt** (`Apache PDFBox 3`):
  1. **Báo cáo Doanh thu Chuỗi rạp** (Người lập: *Kế toán Chuỗi rạp* → Người nhận: *Giám đốc Kinh doanh Chuỗi rạp*) — `reports/exports/BaoCaoDoanhThu_CinemaChain_Demo.pdf` (trên `cinema_chain`) & `reports/exports/BaoCaoDoanhThu_Mau.pdf` (trên tập seed gốc).
  2. **Báo cáo Tình hình Bán vé & Tỷ lệ Lấp đầy** (Người lập: *Quản lý Chi nhánh* → Người nhận: *Ban Điều hành và Quản lý Kinh doanh*) — `reports/exports/BaoCaoBanVe_CinemaChain_Demo.pdf` (trên `cinema_chain`) & `reports/exports/BaoCaoBanVe_Mau.pdf` (trên tập seed gốc).
- **R8 (1,0đ):** Báo cáo học thuật tiếng Việt hoàn chỉnh bao phủ R1–R7, kèm đầy đủ SQL cài đặt và **SQL chức năng tương ứng R5–R7** (`docs/sql-features/functional-sql-mapping.md`, `docs/academic-report/BaoCao_QuanLyChuoiRap.docx` và `.pdf`).

---

## 2. Yêu cầu Hệ thống, Bảo toàn Dữ liệu & Khởi chạy Nhanh (Quick Start)

### 2.1. Công cụ yêu cầu & Lưu ý Môi trường MySQL 8.4.0 LTS
- **Java:** **Java 21 LTS** (`mvnw.cmd` tự động nhận diện Temurin JDK 21 tại `%USERPROFILE%\.jdks\temurin-21` hoặc `JAVA_HOME`).
- **Hệ quản trị CSDL:** **MySQL Community Server 8.4.0 LTS** chạy tại `%USERPROFILE%\.mysql\mysql-8.4` với cấu hình `%USERPROFILE%\.mysql\mysql-8.4\my.ini` và `datadir = C:\Users\LNB\.mysql\data-8.4` trên cổng `127.0.0.1:3306` (`InnoDB`, `utf8mb4_unicode_ci`).
- **Khởi động / dừng an toàn đúng instance MySQL 8.4.0 LTS:**
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File database/start_mysql84.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File database/stop_mysql84.ps1
  ```

### 2.2. Tách biệt Tuyệt đối CSDL Demo (`cinema_chain`) và CSDL Kiểm thử (`cinema_chain_test`)
Sao chép `config/application.properties.example` thành `config/application.properties` (nằm trong `.gitignore`, không lưu mật khẩu trong mã nguồn hay dòng lệnh CLI):
- **`cinema_chain` (CSDL Demo Chính thức — 14 bảng chuẩn 3NF):**
  - Đã được khởi tạo đầy đủ lược đồ **14 bảng chuẩn 3NF** (`17` FK, `12` Trigger, `4` View), nạp dữ liệu mẫu hợp lệ và kiểm chứng trực tiếp toàn trình qua `com.cinema.app.Main`.
  - **Khởi động ứng dụng bình thường tuyệt đối giữ nguyên dữ liệu** (`Mode=NORMAL_READ_ONLY_INIT | DestructiveReset=DISABLED`): mọi suất chiếu và đơn đặt vé vừa nhập trong phiên demo đều được bảo toàn nguyên vẹn qua các lần đóng/mở ứng dụng.
  - **Chỉ reset khi chủ động chuẩn bị lại CSDL demo:** `database/setup.ps1` và `database/verify_all.ps1` bị khóa không bao giờ được xóa `cinema_chain`. Chỉ khi người trình bày **chủ động** muốn đưa `cinema_chain` về lại dữ liệu mẫu gốc sạch trước buổi bảo vệ thì mới chạy tường minh:
    ```powershell
    powershell -NoProfile -ExecutionPolicy Bypass -File database/prepare_demo_db.ps1
    ```
    *(Yêu cầu nhập mã xác nhận `'PREPARE_DEMO_DB'` hoặc cờ `-ConfirmYes`; chỉ thao tác trên `cinema_chain`, giữ nguyên mọi CSDL khác).*
- **`cinema_chain_test` (CSDL Kiểm thử Tự động — Cô lập hoàn toàn):** Tất cả 40 bài test tự động, `GuiVerificationRunner` và `AppPersistenceVerificationRunner` đều bị khóa vào `cinema_chain_test` bởi `TestDatabaseGuard` (không bao giờ chạm vào `cinema_chain`).
  ```powershell
  # Dựng lại sạch duy nhất môi trường kiểm thử 'cinema_chain_test' (14 bảng chuẩn 3NF):
  powershell -NoProfile -ExecutionPolicy Bypass -File database/setup.ps1 -ResetTestDb -TargetDatabase cinema_chain_test -ConfirmYes
  ```

### 2.3. Biên dịch và Kiểm chứng Toàn diện (`40/40 PASS, Skipped = 0`)
```powershell
# Chạy riêng bộ kiểm thử JUnit 5 + Failsafe với profile mysql-it:
.\mvnw.cmd clean verify -Pmysql-it

# Chạy trọn gói 6 bước kiểm chứng chỉ đọc đối với cinema_chain (SHA-256 trước/sau + 40 JUnit tests + GUI EDT + 2 JVM + SQL R5-R7):
powershell -NoProfile -ExecutionPolicy Bypass -File database/verify_all.ps1
```

### 2.4. Khởi chạy Ứng dụng Desktop Phục vụ Demo
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/start_mysql84.ps1
.\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"
```

---

## 3. Cấu trúc Thư mục Dự án

```text
QuanLyChuoiRapPhim/
├── AGENTS.md                        # Quy ước kỹ thuật & bảo vệ an toàn dữ liệu
├── ARCHITECTURE.md                  # Tài liệu kiến trúc phân tầng, phân trang & đồng thời
├── TASKS.md                         # Sổ cái theo dõi trạng thái & bằng chứng thực thi
├── REQUIREMENTS_TRACEABILITY.md     # Ma trận truy vết yêu cầu R1 - R8
├── DEMO_GUIDE.md                    # Kịch bản thuyết trình & trình diễn 15 phút cho buổi bảo vệ
├── README.md                        # Hướng dẫn cài đặt, kiểm chứng & vận hành
├── pom.xml / mvnw.cmd               # Cấu hình Maven Java 21 LTS (profile mysql-it)
├── config/
│   └── application.properties.example
├── database/
│   ├── start_mysql84.ps1 / stop_mysql84.ps1
│   ├── prepare_demo_db.ps1          # Chuẩn bị chủ động có xác nhận cho riêng CSDL demo cinema_chain (14 bảng)
│   ├── setup.ps1                    # Khởi tạo môi trường test cinema_chain_test (cấm reset cinema_chain)
│   ├── verify_all.ps1               # Kiểm chứng 6 bước trên cinema_chain_test & đối chiếu SHA-256 cinema_chain
│   ├── migrations/                  # Gói migration V1 -> V2 chuẩn 3NF không phá hủy + rollback + dry-run
│   ├── schema/                      # 00_init, 00_clean_reset_test_db, 01_schema (14 bảng), 01b_fk (17 FK), 02_triggers, 03_views
│   ├── seed/                        # 10_seed.sql (4 chi nhánh, 12 phòng, 960 ghế, 20 phim, 160 suất, 160 đơn)
│   ├── queries/                     # business_queries.sql & functional_sql_verification.sql (R5-R7)
│   └── verification/                # verify_constraints.sql
├── docs/
│   ├── requirements/                # R1: requirements.md, business-rules.md
│   ├── erd/                         # R2: erd-chen.puml/.png/.svg, erd-logical.puml/.png/.svg, erd-explanation.md
│   ├── relational-model/            # R3: schema-design.md (14 bảng, 17 FK)
│   ├── normalization/               # R4: normalization.md (Chứng minh Lossless-Join & 3NF cho 14 bảng)
│   ├── sql-features/                # R8: functional-sql-mapping.md (SQL tương ứng 5 màn hình R5-R7)
│   ├── demo/                        # test-plan.md, test-cases.md, verification-results.md
│   └── academic-report/             # R8: BaoCao_QuanLyChuoiRap.docx & BaoCao_QuanLyChuoiRap.pdf
├── reports/
│   └── exports/                     # R7: BaoCaoDoanhThu_Mau.pdf & BaoCaoBanVe_Mau.pdf
├── src/
│   ├── main/java/com/cinema/        # Mã nguồn ứng dụng Java Swing + FlatLaf + JDBC + PDFBox
│   └── test/java/com/cinema/        # TestDatabaseGuard, ArchUnit, Safety, Concurrency, DB & GUI Runners
├── tools/                           # build_academic_report.py, db_fingerprint.py, generate_seed_sql.py, plantuml.jar
└── verification/
    ├── logs/                        # Log kiểm chứng, SHA-256 baseline, migration clone dry-run, SQL R5-R7
    ├── screenshots/                 # Ảnh chụp màn hình thực tế từ ứng dụng & trang PDF kết xuất
    └── test-results/                # Báo cáo JUnit XML tươi (40 tests, 0 failures, 0 skipped)
```
