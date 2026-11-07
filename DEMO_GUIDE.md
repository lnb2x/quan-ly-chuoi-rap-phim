# HƯỚNG DẪN TRÌNH DIỄN DỰ ÁN (INSTRUCTOR DEMO SCRIPT)
**(Kịch bản vấn đáp & trình diễn trực tiếp 10 phút cho Giảng viên chấm bài)**

---

## Bước 1 — Kiểm chứng Cơ sở dữ liệu MySQL 8.4 LTS: Normal Setup & Clean Reset (2 phút)

Dự án phân tách rõ ràng **2 chế độ khởi tạo CSDL** chuyên nghiệp để dễ dàng trình bày khi bảo vệ bài tập lớn CSDL:

### 1.1. Chế độ 1 — `Normal Setup` (Mặc định, An toàn 100%, KHÔNG xóa dữ liệu hiện có)
1. Chạy lệnh khởi tạo an toàn trên PowerShell:
   ```powershell
   powershell -ExecutionPolicy Bypass -File database/setup.ps1
   ```
2. **Điểm nhấn trình bày trước Hội đồng:**
   - Kết nối tới **MySQL Community Server 8.4.0 LTS** (`InnoDB`, `utf8mb4_unicode_ci`) và kiểm tra chặn MariaDB tự động.
   - Chạy `00_init_databases_and_user.sql` (`CREATE DATABASE IF NOT EXISTS`), kiểm tra/tạo bảng bằng `CREATE TABLE IF NOT EXISTS` (`01_schema.sql`), kiểm tra khóa ngoại qua `information_schema.REFERENTIAL_CONSTRAINTS` (`01b_foreign_keys.sql`), đồng bộ Trigger (`02_triggers.sql`) & View (`03_views.sql`).
   - **Bảo toàn dữ liệu tuyệt đối:** Nếu `cinema_chain` đã có dữ liệu vận hành, hệ thống **giữ nguyên 100% dữ liệu hiện có**, tuyệt đối không chạy `DROP TABLE`, không `TRUNCATE TABLE`, và không ghi đè dữ liệu mẫu.

### 1.2. Chế độ 2 — `Clean Reset` (Xóa `cinema_chain` có xác nhận & Dựng lại tuần tự từ đầu)
1. Khi muốn trình diễn dựng lại CSDL từ con số 0 trước Giảng viên, chạy:
   ```powershell
   powershell -ExecutionPolicy Bypass -File database/setup.ps1 -CleanReset
   ```
2. **Điểm nhấn an toàn & kiến trúc SQL chuẩn mực:**
   - **Cơ chế xác nhận bắt buộc:** Hiển thị bảng cảnh báo trạng thái hiện tại và yêu cầu nhập chính xác chuỗi `'YES'` (phân biệt hoa/thường); nhập `'NO'` hoặc phím khác sẽ hủy ngay lập tức và giữ nguyên dữ liệu.
   - **Phạm vi cô lập tuyệt đối:** Chỉ xóa duy nhất `cinema_chain` (`00_clean_reset_cinema_chain.sql`), tuyệt đối không đụng chạm tới `mysql`, `sys`, `cinema_chain_test` hay bất kỳ dữ liệu XAMPP nào.
   - **Quy trình 6 bước tuần tự không cần `DROP TABLE` hay `SET FOREIGN_KEY_CHECKS = 0`:**
     1. `[Bước 1/6]` Xóa & tạo mới `cinema_chain` (`00_clean_reset_cinema_chain.sql`) + cấp quyền tối thiểu `cinema_app`
     2. `[Bước 2/6]` Tạo 13 bảng chuẩn 3NF (`01_schema.sql`)
     3. `[Bước 3/6]` Thiết lập 16 ràng buộc Khóa ngoại `FOREIGN KEY` (`01b_foreign_keys.sql`)
     4. `[Bước 4/6]` Thiết lập 06 `TRIGGER` kiểm tra toàn vẹn nghiệp vụ (`02_triggers.sql`)
     5. `[Bước 5/6]` Thiết lập 04 `VIEW` tra cứu & báo cáo (`03_views.sql`)
     6. `[Bước 6/6]` Nạp dữ liệu mẫu theo đúng thứ tự tô-pô **Bảng Cha $\to$ Bảng Con** (`10_seed.sql`)

### 1.3. Quan sát bảng đối chứng toàn vẹn trả về từ tài khoản phân quyền tối thiểu `cinema_app`
   - `cinema_branch`: **4 chi nhánh** (`CN-HN01`, `CN-HCM01`, `CN-DN01`, `CN-HP01`)
   - `screening_room`: **12 phòng chiếu** (`2D`, `3D`, `IMAX`)
   - `seat`: **960 ghế vật lý** (`STD`, `VIP`, `COUPLE`)
   - `movie`: **20 bộ phim** | `genre`: **8 thể loại** | `movie_genre`: **43 liên kết**
   - `screening`: **160 suất chiếu** (`overlapping_open_screenings = 0`)
   - `booking`: **160 đơn đặt vé** | `ticket`: **373 vé** | `payment`: **175 giao dịch**
   - `resold_seats_after_cancellation >= 1` (chứng minh ghế thuộc đơn đã hủy được mở khóa và bán lại thành công)
   - `bookings_with_retry_payment = 15` (15 đơn có cả `FAILED` và `SUCCESS`)
   - `independent_revenue = 35165000.00` khớp tuyệt đối `view_revenue = 35165000.00` (`revenue_difference = 0.00`).

---

## Bước 2 — Chạy Bộ Kiểm thử Tự động (ArchUnit, Concurrency, SQL Trigger, PDF) (2 phút)

Chạy lệnh:
```powershell
.\mvnw.cmd clean verify
```
Trình bày 21 bài kiểm thử tự động vượt qua 100% trên **MySQL Community Server 8.4.0 LTS**:
- `ArchitectureComplianceTest` (3/3): Chứng minh không có bất kỳ câu lệnh SQL nào trong các lớp giao diện Swing (`..ui..`).
- `ScreeningValidationTest` (5/5): Kiểm tra thời lượng phim, giá cơ sở, danh sách ghế.
- `DatabaseIntegrityIT` (8/8): Xác nhận `SELECT VERSION()` là MySQL 8.4.0 (không phải MariaDB), chứng minh Trigger CSDL chặn trực tiếp lệnh SQL chèn suất chiếu trùng lịch, chặn ghế sai phòng chiếu, chặn bán trùng ghế, chặn thanh toán đúp, bảo toàn giá vé lịch sử và chuẩn dấu tiếng Việt UTF-8.
- `ScreeningConcurrencyIT` (1/1): 12 luồng đồng thời tạo suất chiếu chồng lấn giờ trong cùng phòng $\rightarrow$ chỉ đúng 1 luồng thành công.
- `BookingConcurrencyIT` (1/1): 20 luồng đồng thời đặt cùng 1 ghế $\rightarrow$ chỉ đúng 1 luồng thành công; hủy đơn giải phóng ghế và bán lại ghế đó thành công cho khách mới.
- `ScreeningSearchIT` (1/1): Kiểm chứng tra cứu đa tiêu chí và số ghế còn trống.
- `ReportAccuracyIT` (2/2): Chứng minh số liệu báo cáo khớp 100% với SQL độc lập trên MySQL 8.4 và tệp PDF tiếng Việt hợp lệ.

---

## Bước 3 — Trình diễn Trực tiếp 5 Màn hình Nghiệp vụ trên Ứng dụng Desktop (6 phút)

Khởi chạy ứng dụng:
```powershell
.\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"
```

### 3.1. Màn hình 1: Quản lý Lịch chiếu & Suất chiếu (`1. Quản lý Suất chiếu` — R5)
1. **Tạo suất chiếu hợp lệ:** Chọn Chi nhánh Hà Nội, Phòng 01, Phim *"Đào, Phở và Piano"* (105 phút), ngày `2026-11-12`, giờ bắt đầu `09:00`, nhấn **"Tính giờ kết thúc (+20p dọn phòng)"** (`11:05`), giá cơ sở `95000`, nhấn **"➕ Thêm Suất chiếu mới"** $\rightarrow$ Thanh trạng thái màu xanh báo tạo thành công.
2. **Kiểm chứng chặn trùng lịch chiếu (`[BR-06]`):** Giữ nguyên Phòng 01 và ngày `2026-11-12`, đổi giờ bắt đầu thành `10:00` đến `12:15`, nhấn **"➕ Thêm Suất chiếu mới"** $\rightarrow$ Hệ thống từ chối ngay lập tức với thông báo tiếng Việt màu đỏ chỉ rõ suất chiếu bị trùng.

### 3.2. Màn hình 2: Đặt vé & Chọn ghế trực quan (`2. Đặt vé & Chọn ghế` — R5)
1. Chọn một suất chiếu đang mở bán. Quan sát sơ đồ 80 ghế (A01–H10): các ghế đã bán hiển thị dấu `✖` và bị khóa không cho chọn; các ghế trống hiển thị màu theo hạng ghế (`STD`, `VIP`, `COUPLE`).
2. Nhấn chọn 2 ghế trống trên sơ đồ $\rightarrow$ Hệ thống tự động cộng tổng tiền (`base_price + surcharge`).
3. Chọn **Khách hàng thành viên** (hoặc tích chọn **"Khách vãng lai (Mua trực tiếp tại quầy)"**), chọn nhân viên thu ngân, phương thức thanh toán và nhấn **"🎟 Xác nhận Đặt vé & Xuất vé"** $\rightarrow$ Các ghế vừa đặt lập tức chuyển sang trạng thái **Đã bán (`✖`)**.
4. **Trình diễn Hủy đơn & Bán lại ghế (`[BR-11]`):** Chọn đơn vừa đặt trong bảng bên dưới, nhấn **"✖ Hủy Đơn đang chọn & Giải phóng ghế"** $\rightarrow$ Đơn chuyển sang `CANCELLED`, thanh toán chuyển sang `REFUNDED`, và 2 ghế trên sơ đồ lập tức mở khóa trở lại để khách hàng tiếp theo có thể đặt mua.

### 3.3. Màn hình 3: Tra cứu Suất chiếu & Kiểm tra Ghế trống (`3. Tra cứu Suất chiếu` — R6)
1. Nhập từ khóa tên phim (ví dụ: `Dune` hoặc `Đào`), chọn chi nhánh, ngày chiếu `2026-11-04` và nhấn **"🔍 Tìm kiếm Suất chiếu"**.
2. Quan sát bảng kết quả hiển thị đầy đủ thông tin suất chiếu, sức chứa (80 ghế), số ghế đã bán và số ghế còn trống chính xác theo thời gian thực.

### 3.4. Màn hình 4: Báo cáo Doanh thu (`4. Báo cáo Doanh thu` — R7)
1. Quan sát thông tin định danh trên đầu màn hình: **Người lập báo cáo: Kế toán Chuỗi rạp** $\to$ **Người nhận báo cáo: Giám đốc Kinh doanh Chuỗi rạp**.
2. Chọn kỳ báo cáo `2026-11-01` đến `2026-11-15`, nhấn **"📊 Xem trước Báo cáo (Preview)"** để xem bảng tổng hợp theo chi nhánh và bảng chi tiết theo ngày (Tổng doanh thu thực thu: `35.165.000 VNĐ`).
3. Nhấn **"📄 Xuất Báo cáo ra PDF"** và mở tệp `reports/exports/BaoCaoDoanhThu_Mau.pdf` để kiểm tra định dạng bảng và phông chữ tiếng Việt.

### 3.5. Màn hình 5: Báo cáo Tình hình Bán vé & Tỷ lệ Lấp đầy (`5. Báo cáo Tình hình Bán vé` — R7)
1. Quan sát thông tin định danh: **Người lập báo cáo: Quản lý Chi nhánh** $\to$ **Người nhận báo cáo: Ban Điều hành và Quản lý Kinh doanh**.
2. Lọc theo ngày `2026-11-04` đến `2026-11-10`, nhấn **"📊 Xem trước Báo cáo (Preview)"** để kiểm tra số vé bán ra, sức chứa, ghế trống, tỷ lệ lấp đầy (%) và doanh thu.
3. Nhấn **"📄 Xuất Báo cáo ra PDF"** và mở tệp `reports/exports/BaoCaoBanVe_Mau.pdf`.
