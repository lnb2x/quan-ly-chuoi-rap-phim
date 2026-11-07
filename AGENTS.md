# AGENTS.md — Quy ước Kỹ thuật & Điều phối Đội ngũ Agent

## 1. Sứ mệnh Dự án (Project Mission)

Xây dựng hoàn chỉnh dự án học phần Cơ sở dữ liệu **Hệ thống Quản lý Chuỗi Rạp Chiếu Phim** theo định hướng **Evidence-Driven, Verification-First Engineering**:
- Đảm bảo tính đúng đắn học thuật (ERD chuẩn Chen & Crow's Foot, lược đồ quan hệ 13 bảng, phụ thuộc hàm và chứng minh chuẩn hóa 3NF chặt chẽ).
- Đảm bảo toàn vẹn dữ liệu ở tầng DBMS (PK, FK, UNIQUE, CHECK, Trigger, Transaction với `SELECT ... FOR UPDATE`).
- Đảm bảo toàn bộ giao diện người dùng (Java Swing + FlatLaf), thông báo kiểm tra hợp lệ, báo cáo PDF và tài liệu học thuật đều bằng **tiếng Việt** chuẩn xác, rõ ràng.
- Không công nhận bất kỳ tính năng nào là `VERIFIED` nếu chưa có bằng chứng thực thi thực tế (log, kết quả kiểm thử tự động, đối chiếu SQL độc lập, ảnh chụp màn hình thật).

---

## 2. Quyết định Công nghệ & Môi trường (Technology Decisions)

| Thành phần | Công nghệ / Phiên bản | Ghi chú |
|---|---|---|
| Ngôn ngữ & Runtime | **Java 21 LTS** (Eclipse Temurin 21.x tại `%USERPROFILE%\.jdks\temurin-21`) | Biên dịch với `--release 21` và chạy thực tế trên JDK 21 |
| Giao diện Desktop | **Java Swing** + **FlatLaf 3.5.x** (`FlatMacLightLaf`) | Phong cách hiện đại, sáng, bo góc tinh tế |
| Quản lý Build | **Maven** (qua Maven Wrapper `mvnw.cmd`, sử dụng phân phối Maven tại `~/.m2/wrapper`) | Chạy độc lập không phụ thuộc biến môi trường hệ thống |
| Hệ quản trị CSDL | **MySQL Community Server 8.4.0 LTS** (cài đặt portable chính thức tại `%USERPROFILE%\.mysql\mysql-8.4`, InnoDB, `utf8mb4_unicode_ci`) | Dùng tài khoản phân quyền tối thiểu `cinema_app` cho ứng dụng; mật khẩu chỉ lưu trong cấu hình cục bộ (`config/application.properties`) hoặc biến môi trường |
| Kết nối CSDL | **JDBC** (`com.mysql:mysql-connector-j`) + `PreparedStatement` + `TransactionTemplate` | Cấm nhúng SQL vào tầng UI; cấm ghép chuỗi SQL gây lỗi SQL Injection |
| Kiểm thử | **JUnit 5** + **ArchUnit** (34 bài kiểm thử tự động, `Skipped = 0`) | Kiểm thử đơn vị, chốt chặn bảo vệ `cinema_chain` (`TestDatabaseGuard`), kiểm thử kiến trúc, tích hợp MySQL 8.4 & đồng thời |
| Xuất báo cáo PDF | **Apache PDFBox 3.x** (nhúng font Unicode tiếng Việt) | Đảm bảo hiển thị đầy đủ dấu tiếng Việt và khớp 100% với số liệu SQL trên MySQL 8.4 |
| Sơ đồ ERD & Tài liệu | **PlantUML** (`.puml` → `.png`/`.svg`), **Mermaid**, `python-docx` + **ReportLab** (`.docx` & `.pdf`) | Đảm bảo sơ đồ ERD chỉnh sửa được và báo cáo học thuật xuất bản chuyên nghiệp |

---

## 3. Ranh giới Kiến trúc (Architecture Boundaries)

Dự án tổ chức theo cấu trúc gói hướng tính năng kết hợp phân tầng nghiêm ngặt trong `com.cinema`:

```text
com.cinema
├── app/                       # Điểm vào ứng dụng (Main), khung cửa sổ chính (AppShell), điều hướng Sidebar
├── domain/                    # Các lớp thực thể bất biến (Java records) & Enum trạng thái nghiệp vụ
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
4. **Không nuốt ngoại lệ:** Mọi lỗi CSDL hoặc vi phạm quy tắc nghiệp vụ phải được chuyển thành `BusinessRuleException` hoặc `DataAccessException` có thông điệp tiếng Việt rõ ràng và ghi log đầy đủ.
5. **Không mã hóa cứng mật khẩu root:** Ứng dụng kết nối bằng tài khoản `cinema_app` (chỉ có quyền `SELECT, INSERT, UPDATE, DELETE, EXECUTE` trên `cinema_chain` và `cinema_chain_test`).

---

## 4. Quyền Sở hữu CSDL & Quy tắc Toàn vẹn (Database Ownership Rules)

- **Agent A (Database Architect)** giữ quyền sở hữu duy nhất đối với mô hình dữ liệu chuẩn (13 bảng).
- Không một Agent hay lớp Java nào được tự ý thay đổi tên bảng, tên cột, kiểu dữ liệu hoặc ý nghĩa trạng thái nếu chưa cập nhật đồng bộ trên cả 4 nơi:
  1. `docs/erd/` (Sơ đồ ERD)
  2. `docs/relational-model/schema-design.md` (Mô hình quan hệ)
  3. `docs/normalization/normalization.md` (Chứng minh 3NF)
  4. `database/schema/01_schema.sql`, `01b_foreign_keys.sql` & `02_triggers.sql` (Mã lệnh DDL)
- **An toàn dữ liệu:** Script `database/setup.ps1` phân tách rõ 2 chế độ: **Normal Setup** (mặc định, bảo toàn 100% dữ liệu hiện có, không xóa bảng) và **Clean Reset** (`-CleanReset`, bắt buộc xác nhận `'YES'`, chỉ xóa và dựng lại duy nhất `cinema_chain`). Tuyệt đối không xóa hoặc sửa các cơ sở dữ liệu có sẵn khác của người dùng hay XAMPP.

---

## 5. Lệnh Kiểm chứng Bắt buộc (Required Verification Commands)

1. **Khởi động/dừng đúng instance MySQL 8.4.0 LTS (`datadir = %USERPROFILE%\.mysql\data-8.4`) và chạy Normal Setup / Clean Reset (Gate C):**
   ```powershell
   # Khởi động an toàn đúng instance MySQL 8.4.0 LTS (sau khi bật/khởi động lại Windows):
   powershell -ExecutionPolicy Bypass -File database/start_mysql84.ps1

   # Dừng an toàn đúng instance MySQL 8.4.0 LTS khi kết thúc phiên làm việc:
   powershell -ExecutionPolicy Bypass -File database/stop_mysql84.ps1

   # Normal Setup (không xóa dữ liệu hiện có):
   powershell -ExecutionPolicy Bypass -File database/setup.ps1

   # Clean Reset (yêu cầu xác nhận 'YES', chỉ xóa và tạo lại tuần tự cinema_chain):
   powershell -ExecutionPolicy Bypass -File database/setup.ps1 -CleanReset
   ```
2. **Biên dịch và chạy toàn bộ 34 kiểm thử tự động trên `cinema_chain_test` (Gate A, B, C, E):**
   ```powershell
   .\mvnw.cmd clean verify -Pmysql-it
   ```
3. **Chạy kịch bản kiểm thử 5 màn hình GUI (hợp lệ + bị từ chối), kiểm chứng đóng/mở lại ứng dụng qua 2 JVM & chụp ảnh/xuất PDF trên `cinema_chain_test` (Gate D, E):**
   ```powershell
   .\mvnw.cmd test-compile exec:java "-Dexec.mainClass=com.cinema.gui.GuiVerificationRunner" "-Dexec.classpathScope=test"
   .\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.gui.AppPersistenceVerificationRunner" "-Dexec.classpathScope=test" "-Dexec.args=--session1 target/persistence-session-state.properties"
   .\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.gui.AppPersistenceVerificationRunner" "-Dexec.classpathScope=test" "-Dexec.args=--session2 target/persistence-session-state.properties"
   ```
   *(Hoặc chạy một lệnh kiểm chứng tổng hợp toàn bộ 5 bước và kiểm tra bảo toàn `cinema_chain`: `powershell -ExecutionPolicy Bypass -File database/verify_all.ps1`)*
4. **Khởi chạy ứng dụng Desktop phục vụ demo (chỉ đọc/ghi nghiệp vụ chuẩn, tuyệt đối không reset dữ liệu):**
   ```powershell
   .\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"
   ```

---

## 6. Định nghĩa Hoàn thành (Definition of Done)

Một công việc trong `TASKS.md` chỉ được chuyển sang trạng thái `VERIFIED` khi thỏa mãn đủ 6 điều kiện:
1. Đã cài đặt đầy đủ mã nguồn / tài liệu / SQL theo thiết kế.
2. Dự án biên dịch thành công trên JDK 21 (`.\mvnw.cmd test-compile`).
3. Các bài kiểm thử tự động liên quan chạy `PASS` 100%.
4. Hành vi nghiệp vụ thực tế đã được thực thi và kiểm chứng trên CSDL thật.
5. Bằng chứng (log kết quả test, file PDF xuất ra, ảnh chụp màn hình thật) đã được lưu vào `verification/` hoặc `reports/exports/`.
6. Đã cập nhật trạng thái và đường dẫn bằng chứng vào `TASKS.md` và `REQUIREMENTS_TRACEABILITY.md`.
