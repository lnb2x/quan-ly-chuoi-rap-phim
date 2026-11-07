# HỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM CINEVIET
**(Dự án Học phần Cơ sở dữ liệu — Java 21 LTS • Java Swing FlatLaf • MySQL 8.4 LTS • Apache PDFBox)**

---

## 1. Giới thiệu Dự án

**Hệ thống Quản lý Chuỗi Rạp Chiếu Phim CineViet** là ứng dụng Desktop hoàn chỉnh phục vụ quản lý vận hành chuỗi rạp chiếu phim đa chi nhánh, được thiết kế và kiểm chứng theo phương pháp **Verification-First Engineering**.

Dự án đáp ứng đầy đủ 8 tiêu chí chấm điểm học thuật (**R1 – R8**, tổng 10.0 điểm):
- **R1 (1.0đ):** Phân tích yêu cầu nghiệp vụ & dữ liệu cần lưu trữ (`docs/requirements/requirements.md`, `docs/requirements/business-rules.md`).
- **R2 (2.5đ):** Sơ đồ Thực thể – Liên kết (ERD) hoàn chỉnh theo cả chuẩn **Chen** và **Crow's Foot** (`docs/erd/erd-chen.puml/.png/.svg`, `docs/erd/erd-logical.puml/.png/.svg`, `docs/erd/erd-explanation.md`).
- **R3 (2.0đ):** Lược đồ CSDL quan hệ 13 bảng chuẩn 3NF và từ điển dữ liệu chi tiết (`docs/relational-model/schema-design.md`, `database/schema/01_schema.sql`, `01b_foreign_keys.sql`, `02_triggers.sql`, `03_views.sql`).
- **R4 (1.0đ):** Phân tích phụ thuộc hàm, tìm khóa dự tuyển bằng bao đóng thuộc tính và chứng minh đạt chuẩn **1NF, 2NF, 3NF** cho cả 13 quan hệ (`docs/normalization/normalization.md`).
- **R5 (1.0đ):** Hai màn hình nhập liệu vận hành có kiểm tra hợp lệ đầy đủ và an toàn giao dịch đồng thời:
  1. **Màn hình Quản lý Suất chiếu** (`ScreeningManagementPanel`) — kiểm tra thời lượng phim, giá cơ sở, chặn đổi phòng hoặc hủy suất chiếu đã có vé (`BR-08`, `BR-09`), và chống chồng lấn lịch chiếu khi thêm mới lẫn cập nhật (`SELECT ... FOR UPDATE` + Trigger `BR-06`).
  2. **Màn hình Đặt vé & Chọn ghế trực quan** (`TicketBookingPanel`) — sơ đồ 80 ghế trực quan, hỗ trợ khách thành viên và khách vãng lai, giao dịch nguyên tử (`booking` + `ticket` + `payment`), chống bán trùng ghế đồng thời (`BR-10`), hủy đơn & giải phóng ghế để bán lại (`BR-11`).
- **R6 (0.5đ):** Một màn hình tìm kiếm vận hành (**Tra cứu Suất chiếu & Kiểm tra Ghế trống** — `ScreeningSearchPanel`) sử dụng truy vấn tham số hóa (`PreparedStatement`).
- **R7 (1.0đ):** Hai báo cáo quản trị thực tế có xem trước trên màn hình (Preview) và xuất tệp **PDF tiếng Việt** (`Apache PDFBox 3`):
  1. **Báo cáo Doanh thu Chuỗi rạp** (Người lập: *Kế toán Chuỗi rạp* → Người nhận: *Giám đốc Kinh doanh Chuỗi rạp*) — `reports/exports/BaoCaoDoanhThu_Mau.pdf`.
  2. **Báo cáo Tình hình Bán vé & Tỷ lệ Lấp đầy** (Người lập: *Quản lý Chi nhánh* → Người nhận: *Ban Điều hành và Quản lý Kinh doanh*) — `reports/exports/BaoCaoBanVe_Mau.pdf`.
- **R8 (1.0đ):** Báo cáo học thuật tiếng Việt hoàn chỉnh kèm phụ lục SQL (`docs/academic-report/BaoCao_QuanLyChuoiRap.docx` và `.pdf`).

---

## 2. Yêu cầu Hệ thống, Bảo toàn Dữ liệu & Khởi chạy Nhanh (Quick Start)

### 2.1. Công cụ yêu cầu & Lưu ý Môi trường Đa bản MySQL
- **Hệ điều hành:** Windows 10/11 (tương thích với **IntelliJ IDEA** và **Apache NetBeans**).
- **Java:** **Java 21 LTS** (`mvnw.cmd` tự động nhận diện bản portable Temurin JDK 21 tại `%USERPROFILE%\.jdks\temurin-21` hoặc `JAVA_HOME`).
- **Hệ quản trị CSDL:** **MySQL Community Server 8.4.0 LTS** chạy tại `%USERPROFILE%\.mysql\mysql-8.4` với cấu hình `%USERPROFILE%\.mysql\mysql-8.4\my.ini` và thư mục dữ liệu `C:\Users\LNB\.mysql\data-8.4` trên cổng `127.0.0.1:3306` (`InnoDB`, `utf8mb4_unicode_ci`).
- **Lưu ý sau khi khởi động lại Windows:** Bản MySQL 8.4.0 LTS portable không đăng ký Windows Service tự chạy ngầm để tránh xung đột với các bộ cài khác trên máy (`MySQL Server 26.10` hoặc `XAMPP MariaDB`). Hãy luôn dùng cặp script `database/start_mysql84.ps1` và `database/stop_mysql84.ps1` (script tự động kiểm tra đúng `@@version = 8.4.0` và `@@datadir = C:/Users/LNB/.mysql/data-8.4` trước khi thao tác):
  ```powershell
  # Khởi động đúng instance MySQL 8.4.0 LTS sau khi bật lại máy Windows:
  powershell -ExecutionPolicy Bypass -File database/start_mysql84.ps1

  # Dừng an toàn đúng instance MySQL 8.4.0 LTS khi kết thúc phiên làm việc:
  powershell -ExecutionPolicy Bypass -File database/stop_mysql84.ps1
  ```
  *(Lưu ý: Nếu mở bằng **MySQL Workbench 8.0 CE**, cảnh báo không tương thích phiên bản với MySQL Server 8.4.0 là hành vi bình thường của Workbench 8.0 CE đối với câu lệnh quản trị; các truy vấn dữ liệu chuẩn vẫn xem được bình thường).*

### 2.2. Cấu hình kết nối cục bộ & Tách biệt CSDL Demo / Kiểm thử
Sao chép `config/application.properties.example` thành tệp cục bộ `config/application.properties` (nằm trong `.gitignore`, tuyệt đối không ghi mật khẩu vào mã nguồn hay báo cáo):
- **`cinema_chain` (CSDL Demo & Vận hành — ĐƯỢC BẢO TOÀN):** Dành riêng cho ứng dụng chính (`com.cinema.app.Main`). Luồng khởi động ứng dụng chỉ mở kết nối đọc/ghi nghiệp vụ, **tuyệt đối không có lệnh `DROP`, `TRUNCATE`, `DELETE` hàng loạt hay nạp đè seed**.
- **`cinema_chain_test` (CSDL Kiểm thử Tự động — Cô lập hoàn toàn):** Mọi bài kiểm thử tự động có ghi/xóa dữ liệu (`*IT`, `GuiVerificationRunner`, `AppPersistenceVerificationRunner`) đều bị khóa cứng vào `cinema_chain_test` bởi chốt chặn thời gian thực `TestDatabaseGuard` (kiểm tra `SELECT DATABASE() = 'cinema_chain_test'` trước khi thực thi).
- **Tài khoản kết nối:** `cinema_app` (phân quyền tối thiểu `SELECT, INSERT, UPDATE, DELETE, EXECUTE, SHOW VIEW`).

### 2.3. Khởi tạo Cơ sở dữ liệu MySQL 8.4 LTS (`Normal Setup` & `Clean Reset`)

- **Chế độ 1 — `Normal Setup` (Mặc định, An toàn, KHÔNG xóa dữ liệu hiện có):**
  ```powershell
  powershell -ExecutionPolicy Bypass -File database/setup.ps1
  ```
- **Chế độ 2 — `Clean Reset` (Chỉ chạy khi chủ động muốn dựng lại từ đầu, yêu cầu nhập xác nhận `'YES'`):**
  Chỉ xóa duy nhất `cinema_chain` và dựng lại tuần tự: `01_schema.sql` → `01b_foreign_keys.sql` → `02_triggers.sql` → `03_views.sql` → `10_seed.sql`:
  ```powershell
  powershell -ExecutionPolicy Bypass -File database/setup.ps1 -CleanReset
  ```

### 2.4. Biên dịch và Kiểm chứng Toàn diện Một Lệnh (`34/34 PASS, Skipped = 0`)
```powershell
# Chạy riêng bộ kiểm thử JUnit 5 + Failsafe với profile mysql-it:
.\mvnw.cmd clean verify -Pmysql-it

# Hoặc chạy trọn gói 5 bước kiểm chứng (MySQL 8.4 + 34 JUnit tests + 5 màn hình GUI + Đóng/Mở lại qua 2 JVM + Đối chứng bảo toàn cinema_chain):
powershell -ExecutionPolicy Bypass -File database/verify_all.ps1
```

### 2.5. Khởi chạy Ứng dụng Desktop Phục vụ Demo
```powershell
powershell -ExecutionPolicy Bypass -File database/start_mysql84.ps1
.\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"
```

---

## 3. Cấu trúc Thư mục Dự án

```text
QuanLyChuoiRapPhim/
├── AGENTS.md                        # Quy ước kỹ thuật & phân công đội ngũ Agent
├── ARCHITECTURE.md                  # Tài liệu kiến trúc phân tầng & xử lý đồng thời
├── TASKS.md                         # Sổ cái theo dõi trạng thái & bằng chứng thực thi
├── REQUIREMENTS_TRACEABILITY.md     # Ma trận truy vết yêu cầu R1 - R8
├── DEMO_GUIDE.md                    # Kịch bản trình diễn bảo toàn dữ liệu cho giảng viên
├── README.md                        # Hướng dẫn cài đặt, khởi động lại MySQL 8.4 & vận hành
├── pom.xml / mvnw.cmd               # Cấu hình Maven Java 21 LTS (profile mysql-it)
├── config/
│   └── application.properties.example
├── database/
│   ├── start_mysql84.ps1            # Khởi động đúng instance MySQL 8.4.0 LTS sau khi bật máy
│   ├── stop_mysql84.ps1             # Dừng an toàn đúng instance MySQL 8.4.0 LTS
│   ├── setup.ps1                    # Khởi tạo CSDL (Normal Setup bảo toàn dữ liệu / Clean Reset có xác nhận)
│   ├── verify_all.ps1               # Lệnh kiểm chứng lặp lại 5 lớp & kiểm tra bất biến cinema_chain
│   ├── schema/                      # 00_init, 00_clean_reset, 01_schema, 01b_foreign_keys, 02_triggers, 03_views
│   ├── seed/                        # 10_seed.sql (4 chi nhánh, 12 phòng, 960 ghế, 20 phim, 160 suất, 160 đơn)
│   ├── queries/                     # business_queries.sql (Truy vấn nghiệp vụ & đối soát)
│   └── verification/                # verify_constraints.sql (Kiểm chứng toàn vẹn CSDL)
├── docs/
│   ├── requirements/                # R1: requirements.md, business-rules.md
│   ├── erd/                         # R2: erd-chen.puml/.png/.svg, erd-logical.puml/.png/.svg, erd-explanation.md
│   ├── relational-model/            # R3: schema-design.md
│   ├── normalization/               # R4: normalization.md (Chứng minh 1NF, 2NF, 3NF)
│   ├── demo/                        # test-plan.md, test-cases.md, verification-results.md
│   └── academic-report/             # R8: BaoCao_QuanLyChuoiRap.docx & BaoCao_QuanLyChuoiRap.pdf
├── reports/
│   └── exports/                     # R7: BaoCaoDoanhThu_Mau.pdf & BaoCaoBanVe_Mau.pdf
├── src/
│   ├── main/java/com/cinema/        # Mã nguồn ứng dụng Java Swing + FlatLaf + JDBC + PDFBox
│   └── test/java/com/cinema/        # TestDatabaseGuard, ArchUnit, Unit, Concurrency, DB & GUI Runners
└── verification/
    ├── logs/                        # Log cài đặt CSDL, Maven verify, GUI & Persistence restart
    ├── screenshots/                 # Ảnh chụp màn hình thực tế từ ứng dụng đang chạy
    └── test-results/                # Báo cáo kết quả kiểm thử JUnit XML (34 tests, 0 skipped)
```
