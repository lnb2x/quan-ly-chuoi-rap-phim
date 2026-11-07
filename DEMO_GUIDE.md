# HƯỚNG DẪN TRÌNH DIỄN DỰ ÁN BẢO TOÀN DỮ LIỆU (INSTRUCTOR DEMO SCRIPT)
**(Kịch bản vấn đáp, khởi động sau khi bật lại Windows & trình diễn trực tiếp 10 phút trên MySQL 8.4.0 LTS)**

---

## Bước 0 — Khởi động Đúng Instance MySQL 8.4.0 LTS Sau Khi Khởi động lại Windows (1 phút)

Trên máy phát triển có thể tồn tại nhiều bộ cài CSDL khác nhau (`MySQL Server 26.10`, `XAMPP MariaDB 10.4`, `MySQL Workbench 8.0 CE`), nhưng dự án sử dụng duy nhất bản **MySQL Community Server 8.4.0 LTS** tại `C:\Users\LNB\.mysql\mysql-8.4` (`my.ini` trỏ vào `datadir = C:\Users\LNB\.mysql\data-8.4`, cổng `3306`). Bản này không chạy ngầm dưới dạng Windows Service nên sau khi khởi động lại Windows, thực hiện:

```powershell
# 1. Khởi động và tự động xác minh đúng MySQL 8.4.0 LTS + đúng datadir:
powershell -ExecutionPolicy Bypass -File database/start_mysql84.ps1

# 2. Mở trực tiếp ứng dụng Desktop để demo trên dữ liệu hiện có của 'cinema_chain' (KHÔNG reset dữ liệu):
.\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"
```

- **Quy tắc bảo toàn dữ liệu khi Demo:**
  - Mở ứng dụng bằng `com.cinema.app.Main` **tuyệt đối không chạy bất kỳ lệnh reset, `DROP`, `TRUNCATE` hay `DELETE` nào** (được xác nhận trong log khởi động `[APP-STARTUP] ... Mode=NORMAL_READ_ONLY_INIT ... DestructiveReset=DISABLED` và được bảo vệ bởi bài test kiến trúc `ArchitectureComplianceTest`).
  - Mọi dữ liệu tạo mới trong buổi demo trên `cinema_chain` đều được lưu bền vững qua các lần đóng và mở lại ứng dụng.
  - Khi kết thúc buổi demo và muốn tắt MySQL 8.4.0 an toàn để tránh hỏng InnoDB tablespace:
    ```powershell
    powershell -ExecutionPolicy Bypass -File database/stop_mysql84.ps1
    ```

---

## Bước 1 — Trình bày Kiến trúc Khởi tạo CSDL: Normal Setup vs. Clean Reset (2 phút)

### 1.1. Chế độ 1 — `Normal Setup` (Mặc định, Bảo toàn 100% dữ liệu hiện có)
```powershell
powershell -ExecutionPolicy Bypass -File database/setup.ps1
```
- Tự động xác minh `SELECT VERSION() = 8.4.0` (`MySQL Community Server - GPL`) và chặn MariaDB.
- Giữ nguyên 100% dữ liệu hiện có trong `cinema_chain` (`4` chi nhánh, `12` phòng, `960` ghế, `20` phim, `160` suất chiếu, `160` đơn đặt vé, `373` vé, `175` thanh toán, tổng doanh thu thực thu `35.165.000 VNĐ`).

### 1.2. Chế độ 2 — `Clean Reset` (Chỉ dùng khi Giảng viên yêu cầu dựng lại từ con số 0)
```powershell
powershell -ExecutionPolicy Bypass -File database/setup.ps1 -CleanReset
```
- Bắt buộc nhập xác nhận chính xác `'YES'`; chỉ xóa duy nhất `cinema_chain` (cô lập hoàn toàn với các CSDL khác và dữ liệu XAMPP) rồi chạy tuần tự 6 bước: `00_clean_reset_cinema_chain.sql` → `01_schema.sql` (13 bảng) → `01b_foreign_keys.sql` (16 FK) → `02_triggers.sql` (6 Trigger) → `03_views.sql` (4 View) → `10_seed.sql`.

---

## Bước 2 — Chạy Bộ Kiểm thử Tự động Cô lập trên `cinema_chain_test` (2 phút)

Toàn bộ các bài kiểm thử tự động có thao tác thêm/sửa/xóa đều bị chốt chặn `TestDatabaseGuard` khóa cứng vào `cinema_chain_test` (nếu cấu hình nhầm vào `cinema_chain`, bài test sẽ từ chối chạy ngay lập tức để bảo vệ dữ liệu demo):

```powershell
.\mvnw.cmd clean verify -Pmysql-it
```

Trình bày **34/34 bài kiểm thử tự động PASS 100% (`Failures = 0, Errors = 0, Skipped = 0`)**:
- `ArchitectureComplianceTest` (**5/5**): Cấm SQL trong UI, phân tầng nghiêm ngặt, cấm SQL phá hủy (`DROP`/`TRUNCATE`/`DELETE`) và cấm gọi tiến trình hệ thống trong `src/main/java`.
- `TestSafetyGuardTest` (**3/3**): Chứng minh `TestDatabaseGuard` chặn `cinema_chain`, chặn MariaDB, chặn `root`, và `AppConfig` không rò rỉ mật khẩu.
- `ScreeningValidationTest` (**5/5**): Kiểm tra thời lượng phim, giá cơ sở dương, danh sách ghế.
- `DatabaseIntegrityIT` (**13/13**): Xác nhận MySQL `8.4.0`, kiểm chứng Trigger chống trùng lịch khi **tạo mới (`INSERT`) và cập nhật (`UPDATE`)**, chặn ghế sai phòng, **chặn đổi phòng chiếu của suất đã phát sinh vé (`[BR-09]`)**, chặn chuyển suất chiếu sang phòng ngừng hoạt động (`[BR-01]`), chặn bán trùng ghế (`[BR-10]`), chặn thanh toán đúp (`[BR-14]`), chặn xác nhận thanh toán cho đơn đã hủy (`[BR-13]`), kiểm tra FK/CHECK/Savepoint rollback và bảo toàn giá vé lịch sử (`[BR-12]`).
- `ScreeningConcurrencyIT` (**2/2**): 12 luồng tạo đồng thời và 8 luồng cập nhật đồng thời suất chiếu chồng lấn giờ trong cùng phòng → chỉ đúng 1 luồng thành công.
- `BookingConcurrencyIT` (**1/1**): 20 luồng đồng thời đặt cùng 1 ghế → đúng 1 luồng thành công, **0 đơn/thanh toán mồ côi**; hủy đơn giải phóng ghế để bán lại và kiểm chứng chuyển trạng thái `PENDING → FAILED / SUCCESS`.
- `ScreeningSearchIT` (**2/2**): Tra cứu đa tiêu chí kèm ghế trống và hiển thị đầy đủ `>= 160` suất chiếu mở bán trên Màn hình Đặt vé.
- `ReportAccuracyIT` (**3/3**): Đối chiếu cả 2 báo cáo & PDF tiếng Việt với truy vấn SQL độc lập (`35.165.000 VNĐ`, sai lệch `0.00 VNĐ`) và bộ dữ liệu kiểm chứng tính tay (Known-Answer Fixture).

---

## Bước 3 — Trình diễn Trực tiếp 5 Màn hình Nghiệp vụ trên Ứng dụng Desktop (5 phút)

Khởi chạy ứng dụng:
```powershell
.\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"
```

### 3.1. Màn hình 1: Quản lý Lịch chiếu & Suất chiếu (`1. Quản lý Suất chiếu` — R5)
1. **Tạo suất chiếu hợp lệ:** Chọn Chi nhánh Hà Nội, Phòng 01, Phim *"Đào, Phở và Piano"* (105 phút), ngày `2026-11-12`, giờ bắt đầu `09:00`, nhấn **"Tính giờ kết thúc (+20p dọn phòng)"** (`11:05`), giá cơ sở `95000`, nhấn **"+ Thêm Suất chiếu mới"** → Thanh trạng thái màu xanh báo tạo thành công.
2. **Kiểm chứng chặn trùng lịch (`[BR-06]`) & chặn đổi phòng khi đã bán vé (`[BR-09]`):**
   - Nhập khung giờ giao thoa `10:00` – `12:15` cùng Phòng 01 ngày `2026-11-12` → Hệ thống từ chối ngay với mã lỗi `[BR-06]`.
   - Chọn một suất chiếu đã có vé bán (ví dụ `#1`), thử đổi sang Phòng 02 và nhấn **"Lưu Cập nhật Suất đang chọn"** → Hệ thống từ chối ngay với mã lỗi `[BR-09]`.

### 3.2. Màn hình 2: Đặt vé & Chọn ghế trực quan (`2. Đặt vé & Chọn ghế` — R5)
1. Chọn suất chiếu vừa tạo (hoặc suất đang mở bán). Quan sát sơ đồ 80 ghế (A01–H10): ghế đã bán bị khóa; ghế trống phân màu theo hạng ghế (`STD`, `VIP`, `COUPLE`).
2. Nhấn **Xác nhận Đặt vé** khi chưa chọn ghế → Hệ thống từ chối rõ ràng (`[BR-08]`).
3. Chọn 2 ghế trống → Tổng tiền tự động tính (`base_price + surcharge`) → Nhấn **"Xác nhận Đặt vé & Xuất vé"** → Giao dịch thành công, ghế đổi sang trạng thái đã bán.
4. **Trình diễn Hủy đơn & Bán lại ghế (`[BR-11]`):** Chọn đơn vừa đặt trong bảng bên dưới, nhấn **"Hủy Đơn đang chọn & Giải phóng ghế"** → Đơn chuyển sang `CANCELLED`, thanh toán `SUCCESS` chuyển sang `REFUNDED`, và 2 ghế lập tức mở khóa để bán lại.

### 3.3. Màn hình 3: Tra cứu Suất chiếu & Kiểm tra Ghế trống (`3. Tra cứu Suất chiếu` — R6)
1. Nhập thử ngày sai định dạng (`2026-99-99`) → Thanh trạng thái báo lỗi kiểm tra hợp lệ tiếng Việt.
2. Lọc theo từ khóa tên phim (`Dune` hoặc `Đào`), chi nhánh và ngày chiếu `2026-11-04` → Hiển thị chính xác sức chứa (80 ghế), vé đã bán và số ghế còn trống.

### 3.4. Màn hình 4: Báo cáo Doanh thu (`4. Báo cáo Doanh thu` — R7)
1. Định danh: **Người lập: Kế toán Chuỗi rạp** → **Người nhận: Giám đốc Kinh doanh Chuỗi rạp**.
2. Chọn kỳ `2026-11-01` đến `2026-11-15`, nhấn **"Xem trước Báo cáo (Preview)"** (`125 đơn`, `292 vé`, `35.165.000 VNĐ` trên bộ seed gốc) và nhấn **"Xuất Báo cáo ra PDF"** (`reports/exports/BaoCaoDoanhThu_Mau.pdf`).

### 3.5. Màn hình 5: Báo cáo Tình hình Bán vé & Tỷ lệ Lấp đầy (`5. Báo cáo Tình hình Bán vé` — R7)
1. Định danh: **Người lập: Quản lý Chi nhánh** → **Người nhận: Ban Điều hành và Quản lý Kinh doanh**.
2. Chọn kỳ `2026-11-04` đến `2026-11-10`, nhấn **"Xem trước Báo cáo (Preview)"** và **"Xuất Báo cáo ra PDF"** (`reports/exports/BaoCaoBanVe_Mau.pdf`).
