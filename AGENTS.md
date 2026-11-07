# AGENTS.md — Quy ước Kỹ thuật & Điều phối Đội ngũ Agent

## 1. Sứ mệnh Dự án (Project Mission)

Xây dựng hoàn chỉnh dự án học phần Cơ sở dữ liệu **Hệ thống Quản lý Chuỗi Rạp Chiếu Phim** theo định hướng **Evidence-Driven, Verification-First Engineering**:
- Đảm bảo tính đúng đắn học thuật (ERD chuẩn Chen & Crow's Foot, lược đồ quan hệ **14 bảng chuẩn 3NF nghiêm ngặt**, phụ thuộc hàm, chứng minh kết nối không mất thông tin và chuẩn hóa 3NF chặt chẽ).
- Đảm bảo toàn vẹn dữ liệu ở tầng DBMS (PK, FK, UNIQUE, CHECK, 12 Trigger đồng bộ & bảo vệ, Transaction với `SELECT ... FOR UPDATE`).
- Đảm bảo toàn bộ giao diện người dùng (Java Swing + FlatLaf), phân trang tường minh, thông báo kiểm tra hợp lệ, báo cáo PDF và tài liệu học thuật đều bằng **tiếng Việt** chuẩn xác, rõ ràng.
- Không công nhận bất kỳ tính năng nào là `VERIFIED` nếu chưa có bằng chứng thực thi thực tế (log tươi sau khi xóa XML cũ, kết quả kiểm thử tự động `Skipped = 0`, đối chiếu SQL độc lập, dấu vân tay SHA-256, ảnh chụp màn hình thật).

---

## 2. Quyết định Công nghệ & Môi trường (Technology Decisions)

| Thành phần | Công nghệ / Phiên bản | Ghi chú |
|---|---|---|
| Ngôn ngữ & Runtime | **Java 21 LTS** (Eclipse Temurin 21.x tại `%USERPROFILE%\.jdks\temurin-21`) | Biên dịch với `--release 21` và chạy thực tế trên JDK 21 |
| Giao diện Desktop | **Java Swing** + **FlatLaf 3.5.x** (`FlatMacLightLaf`) | Phong cách hiện đại, phân trang tường minh 50 dòng/trang |
| Quản lý Build | **Maven** (qua Maven Wrapper `mvnw.cmd`, sử dụng phân phối Maven tại `~/.m2/wrapper`) | Chạy độc lập không phụ thuộc biến môi trường hệ thống |
| Hệ quản trị CSDL | **MySQL Community Server 8.4.0 LTS** (`%USERPROFILE%\.mysql\mysql-8.4`, `datadir = %USERPROFILE%\.mysql\data-8.4`, InnoDB, `utf8mb4_unicode_ci`) | Dùng tài khoản phân quyền tối thiểu `cinema_app`; truyền thông tin xác thực qua `--defaults-extra-file` / `config/application.properties`, cấm lộ mật khẩu trên CLI/env |
| Kết nối CSDL | **JDBC** (`com.mysql:mysql-connector-j`) + `PreparedStatement` + `TransactionTemplate` | Cấm nhúng SQL vào tầng UI; cấm ghép chuỗi SQL gây lỗi SQL Injection |
| Kiểm thử | **JUnit 5** + **ArchUnit** (**40 bài kiểm thử tự động**, `Skipped = 0`) | Kiểm thử đơn vị, an toàn tiến trình & bảo vệ `cinema_chain` (`TestSafetyGuardTest`), kiến trúc, tích hợp 3NF MySQL 8.4 & đồng thời |
| Xuất báo cáo PDF | **Apache PDFBox 3.x** (nhúng font Unicode tiếng Việt) | Đảm bảo hiển thị đầy đủ dấu tiếng Việt và khớp 100% với số liệu SQL trên MySQL 8.4 |
| Sơ đồ ERD & Tài liệu | **PlantUML** (`.puml` → `.png`/`.svg`), **Mermaid**, `python-docx` + **ReportLab** (`.docx` & `.pdf`) | Đảm bảo sơ đồ ERD chỉnh sửa được và báo cáo học thuật xuất bản chuyên nghiệp |

---

## 3. Ranh giới Kiến trúc (Architecture Boundaries)

Dự án tổ chức theo cấu trúc gói hướng tính năng kết hợp phân tầng nghiêm ngặt trong `com.cinema`:

```text
com.cinema
├── app/                       # Điểm vào ứng dụng (Main), khung cửa sổ chính (AppShell), điều hướng Sidebar
├── domain/                    # Các lớp thực thể bất biến (Java records, PageResult, SearchPageResult) & Enum
├── shared/
│   ├── config/                # Đọc cấu hình từ biến môi trường hoặc config/application.properties
│   ├── db/                    # ConnectionProvider, TransactionTemplate, DataAccessException
│   ├── validation/            # ValidationResult, BusinessRuleException (thông báo tiếng Việt)
│   └── ui/                    # Hệ thống thiết kế FlatLaf (Theme, CardPanel, StatusBanner, SeatMapPanel...)
├── feature/
│   ├── screening/             # Quản lý suất chiếu (ScreeningRepository, ScreeningService, ui/ScreeningManagementPanel)
│   ├── booking/               # Đặt vé & chọn ghế (BookingRepository, BookingService, ui/TicketBookingPanel)
│   ├── search/                # Tra cứu suất chiếu (ScreeningSearchRepository, ScreeningSearchService, ui/ScreeningSearchPanel)
│   └── report/
│       ├── revenue/           # Báo cáo doanh thu (RevenueReportRepository, RevenueReportService, ui/RevenueReportPanel)
│       └── sales/             # Báo cáo bán vé (TicketSalesReportRepository, TicketSalesReportService, ui/TicketSalesReportPanel)
└── reporting/                 # PdfReportWriter dùng chung để xuất PDF tiếng Việt và trích xuất kiểm chứng
```

### Quy tắc kiến trúc bắt buộc (được kiểm tra tự động bởi ArchUnit — Gate B):
1. **Cấm SQL trong UI:** Các lớp trong `..ui..` tuyệt đối không được phụ thuộc vào `java.sql.*` hoặc `javax.sql.*`.
2. **Phân tách tầng:** `ui` chỉ gọi `service` (và `domain`/`shared.ui`); `service` gọi `repository`; `repository` làm việc với `shared.db` và `java.sql`.
3. **Không đảo chiều phụ thuộc:** `domain`, `repository`, và `service` không bao giờ được import bất kỳ lớp nào từ `..ui..`.
4. **Không cắt ngầm dữ liệu:** Cấm dùng `LIMIT 1000` cắt ngầm kết quả; phải phân trang tường minh hoặc hiển thị rõ tổng số dòng.
5. **Không mã hóa cứng hay lộ mật khẩu trên CLI:** Ứng dụng kết nối bằng `cinema_app`; mọi kịch bản CLI phải dùng `--defaults-extra-file` tạm thời và truyền SQL qua `stdin`.

---

## 4. Quyền Sở hữu CSDL & Quy tắc Bảo toàn Dữ liệu (Database Ownership & Preservation Rules)

- **Agent A (Database Architect)** giữ quyền sở hữu đối với mô hình dữ liệu chuẩn (**14 bảng chuẩn 3NF**).
- Mọi thay đổi tên bảng, tên cột, kiểu dữ liệu hoặc ý nghĩa trạng thái phải đồng bộ trên cả 5 nơi:
  1. `docs/erd/` (Sơ đồ ERD Chen & Crow's Foot)
  2. `docs/relational-model/schema-design.md` (Mô hình quan hệ 14 bảng & 17 FK)
  3. `docs/normalization/normalization.md` (Chứng minh 3NF & Lossless-Join)
  4. `docs/sql-features/functional-sql-mapping.md` (SQL chức năng R5–R7)
  5. `database/schema/01_schema.sql`, `01b_foreign_keys.sql`, `02_triggers.sql` & `03_views.sql`
- **Bảo vệ tuyệt đối `cinema_chain`:**
  - `cinema_chain` hiện đã hoàn tất trên lược đồ **14 bảng chuẩn 3NF**. Khởi động ứng dụng bình thường (`com.cinema.app.Main`) tuyệt đối giữ nguyên dữ liệu hiện có.
  - `database/setup.ps1` **đã khóa cứng và vô hiệu hóa mọi chế độ xóa/reset `cinema_chain`** (`-CleanReset` bị chặn ngay lập tức; `-ResetTestDb` chỉ chấp nhận duy nhất `-TargetDatabase cinema_chain_test` với `-ConfirmYes`).
  - Việc chuẩn bị lại `cinema_chain` về dữ liệu mẫu gốc chỉ được thực hiện khi người vận hành **chủ động** chạy kịch bản riêng `database/prepare_demo_db.ps1` có xác nhận tường minh.

---

## 5. Lệnh Kiểm chứng Bắt buộc (Required Verification Commands)

1. **Khởi động/dừng đúng instance MySQL 8.4.0 LTS (`datadir = %USERPROFILE%\.mysql\data-8.4`) và làm mới CSDL kiểm thử `cinema_chain_test`:**
   ```powershell
   # Khởi động an toàn đúng instance MySQL 8.4.0 LTS:
   powershell -NoProfile -ExecutionPolicy Bypass -File database/start_mysql84.ps1

   # Dừng an toàn đúng instance MySQL 8.4.0 LTS khi kết thúc phiên làm việc:
   powershell -NoProfile -ExecutionPolicy Bypass -File database/stop_mysql84.ps1

   # Kiểm tra/khởi tạo không phá hủy trên CSDL kiểm thử (mặc định TargetDatabase = cinema_chain_test):
   powershell -NoProfile -ExecutionPolicy Bypass -File database/setup.ps1

   # Dựng lại sạch duy nhất CSDL kiểm thử 'cinema_chain_test' (tuyệt đối không chạm 'cinema_chain'):
   powershell -NoProfile -ExecutionPolicy Bypass -File database/setup.ps1 -ResetTestDb -TargetDatabase cinema_chain_test -ConfirmYes
   ```
2. **Biên dịch và chạy toàn bộ 40 kiểm thử tự động trên `cinema_chain_test` (Gate A, B, C, E):**
   ```powershell
   .\mvnw.cmd clean verify -Pmysql-it
   ```
3. **Chạy trọn bộ 6 bước kiểm chứng tự động (SHA-256 `cinema_chain` trước/sau + 40 JUnit tests + 5 màn hình GUI EDT + 2-JVM Persistence + SQL chức năng R5–R7):**
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File database/verify_all.ps1
   ```
4. **Khởi chạy ứng dụng Desktop phục vụ demo (kết nối trực tiếp `cinema_chain` 14 bảng chuẩn 3NF, bảo toàn dữ liệu khi đóng/mở):**
   ```powershell
   .\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"
   ```

---

## 6. Định nghĩa Hoàn thành (Definition of Done)

Một công việc trong `TASKS.md` chỉ được chuyển sang trạng thái `VERIFIED` khi thỏa mãn đủ 6 điều kiện:
1. Đã cài đặt đầy đủ mã nguồn / tài liệu / SQL theo thiết kế 14 bảng chuẩn 3NF.
2. Dự án biên dịch thành công trên JDK 21 (`.\mvnw.cmd test-compile`).
3. Các bài kiểm thử tự động liên quan chạy tươi `PASS` 100% với `Skipped = 0`.
4. Hành vi nghiệp vụ thực tế đã được thực thi và kiểm chứng trên `cinema_chain_test` (MySQL 8.4.0 LTS thật) mà không làm thay đổi dấu vân tay SHA-256 của `cinema_chain`.
5. Bằng chứng (log kết quả test, file PDF xuất ra, ảnh chụp màn hình thật) đã được lưu và kiểm tra trực quan.
6. Đã cập nhật trạng thái và đường dẫn bằng chứng vào `TASKS.md` và `REQUIREMENTS_TRACEABILITY.md`.
