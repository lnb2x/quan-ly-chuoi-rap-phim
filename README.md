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
  1. **Màn hình Quản lý Suất chiếu** (`ScreeningManagementPanel`) — kiểm tra thời lượng phim, giá cơ sở và chống chồng lấn lịch chiếu trong cùng một phòng chiếu (`SELECT ... FOR UPDATE` + Trigger `BR-06`).
  2. **Màn hình Đặt vé & Chọn ghế trực quan** (`TicketBookingPanel`) — sơ đồ 80 ghế trực quan, hỗ trợ khách thành viên và khách vãng lai, giao dịch nguyên tử (`booking` + `ticket` + `payment`), chống bán trùng ghế đồng thời (`BR-10`), hủy đơn & giải phóng ghế để bán lại (`BR-11`).
- **R6 (0.5đ):** Một màn hình tìm kiếm vận hành (**Tra cứu Suất chiếu & Kiểm tra Ghế trống** — `ScreeningSearchPanel`) sử dụng truy vấn tham số hóa (`PreparedStatement`).
- **R7 (1.0đ):** Hai báo cáo quản trị thực tế có xem trước trên màn hình (Preview) và xuất tệp **PDF tiếng Việt** (`Apache PDFBox 3`):
  1. **Báo cáo Doanh thu Chuỗi rạp** (Người lập: *Kế toán Chuỗi rạp* $\to$ Người nhận: *Giám đốc Kinh doanh Chuỗi rạp*) — `reports/exports/BaoCaoDoanhThu_Mau.pdf`.
  2. **Báo cáo Tình hình Bán vé & Tỷ lệ Lấp đầy** (Người lập: *Quản lý Chi nhánh* $\to$ Người nhận: *Ban Điều hành và Quản lý Kinh doanh*) — `reports/exports/BaoCaoBanVe_Mau.pdf`.
- **R8 (1.0đ):** Báo cáo học thuật tiếng Việt hoàn chỉnh kèm phụ lục SQL (`docs/academic-report/BaoCao_QuanLyChuoiRap.docx` và `.pdf`).

---

## 2. Yêu cầu Hệ thống & Cài đặt Nhanh (Quick Start)

### 2.1. Công cụ yêu cầu
- **Hệ điều hành:** Windows 10/11 (tương thích hoàn toàn với **IntelliJ IDEA** và **Apache NetBeans**).
- **Java:** **Java 21 LTS** (`mvnw.cmd` tự động nhận diện bản portable Temurin JDK 21 tại `%USERPROFILE%\.jdks\temurin-21` hoặc `JAVA_HOME`).
- **Hệ quản trị CSDL:** **MySQL Community Server 8.4 LTS** (bản phân phối chính thức tại `%USERPROFILE%\.mysql\mysql-8.4` hoặc cài đặt hệ thống MySQL 8.x trên cổng `3306`, engine `InnoDB`, bộ mã `utf8mb4_unicode_ci`).

### 2.2. Bước 1 — Cấu hình kết nối cục bộ (Không lưu mật khẩu trong Git)
Sao chép `config/application.properties.example` thành tệp cục bộ `config/application.properties` (tệp này nằm trong `.gitignore`) và điền mật khẩu cục bộ cho tài khoản phân quyền tối thiểu `cinema_app` (hoặc đặt biến môi trường `CINEMA_DB_PASSWORD` và `CINEMA_TEST_DB_PASSWORD`):
- **Database:** `cinema_chain` (vận hành) và `cinema_chain_test` (kiểm thử tự động)
- **Username:** `cinema_app` (Least-Privilege User)
- **Password:** Đọc từ tệp cục bộ `config/application.properties` hoặc biến môi trường `CINEMA_DB_PASSWORD`

### 2.3. Bước 2 — Khởi tạo Cơ sở dữ liệu MySQL 8.4 LTS (`Normal Setup` & `Clean Reset`)

Script `database/setup.ps1` hỗ trợ **2 chế độ khởi tạo rõ ràng** trên **MySQL Community Server 8.4 LTS** (tự động kiểm tra phiên bản MySQL 8.x, chặn MariaDB, và tuyệt đối không tác động tới các CSDL khác hoặc dữ liệu XAMPP):

- **Chế độ 1 — `Normal Setup` (Mặc định, An toàn, KHÔNG xóa dữ liệu hiện có):**
  Kiểm tra/tạo CSDL nếu chưa tồn tại, tạo bảng bằng `CREATE TABLE IF NOT EXISTS`, bổ sung khóa ngoại nếu thiếu, đồng bộ Trigger & View, và **chỉ nạp dữ liệu mẫu khi CSDL đang rỗng** (giữ nguyên 100% dữ liệu đang có):
  ```powershell
  powershell -ExecutionPolicy Bypass -File database/setup.ps1
  ```

- **Chế độ 2 — `Clean Reset` (Xóa `cinema_chain` có xác nhận `'YES'` & Dựng lại tuần tự từ đầu):**
  Hiển thị cảnh báo và yêu cầu người dùng nhập chính xác `'YES'` trước khi xóa **duy nhất** database `cinema_chain`, sau đó tạo mới lại từ đầu theo đúng trình tự chuẩn: **Tạo 13 Bảng (`01_schema.sql`) $\to$ Tạo 16 Khóa ngoại (`01b_foreign_keys.sql`) $\to$ Tạo 6 Trigger (`02_triggers.sql`) $\to$ Tạo 4 View (`03_views.sql`) $\to$ Nạp Dữ liệu Mẫu (`10_seed.sql`)**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File database/setup.ps1 -CleanReset
  ```

### 2.4. Bước 3 — Biên dịch và Chạy Toàn bộ Kiểm thử Tự động trên MySQL 8.4 (21 bài test)
```powershell
.\mvnw.cmd clean verify
```

### 2.5. Bước 4 — Khởi chạy Ứng dụng Desktop
```powershell
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
├── DEMO_GUIDE.md                    # Kịch bản trình diễn (Demo Script) dành cho giảng viên
├── README.md                        # Hướng dẫn cài đặt và vận hành dự án
├── pom.xml / mvnw.cmd               # Cấu hình Maven Java 21 LTS
├── config/
│   └── application.properties.example
├── database/
│   ├── setup.ps1                    # Script khởi tạo & kiểm chứng CSDL (Normal Setup / Clean Reset)
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
│   └── test/java/com/cinema/        # Kiểm thử ArchUnit, Unit, Concurrency, Database & GUI
└── verification/
    ├── logs/                        # Log cài đặt CSDL, log Maven verify, log GUI verification
    ├── screenshots/                 # Ảnh chụp màn hình thực tế từ ứng dụng đang chạy
    └── test-results/                # Báo cáo kết quả kiểm thử JUnit XML
```
