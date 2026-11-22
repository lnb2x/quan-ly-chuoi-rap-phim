# Ánh xạ câu lệnh SQL theo từng màn hình và báo cáo

---

## Nguyên tắc đối chiếu SQL trong mã nguồn Java và SQL kiểm tra thực tế

Các câu lệnh SQL dưới đây được trích từ 5 lớp `*Repository.java` của ứng dụng (sử dụng `PreparedStatement`) và được chạy kiểm tra trên **`cinema_chain_test` (MySQL Community Server 8.4)** thông qua tệp `database/queries/functional_sql_verification.sql`.

Mỗi chức năng đều phân biệt rõ hai dạng biểu diễn:
1. **Câu lệnh SQL tham số hóa (`?`) trong mã nguồn Java (`PreparedStatement`):** Mã thực thi thật trong ứng dụng.
2. **Câu lệnh SQL ví dụ cụ thể chạy trực tiếp trên `cinema_chain_test`:** Dán trực tiếp vào MySQL CLI / MySQL Workbench để kiểm chứng ngay kết quả (mọi ví dụ ghi dữ liệu đều bọc trong `START TRANSACTION ... ROLLBACK` khi kiểm thử để không làm bẩn dữ liệu nền; trong luồng nghiệp vụ thật của ứng dụng, `TransactionTemplate` kết thúc bằng `COMMIT` khi hợp lệ hoặc `ROLLBACK` khi vi phạm quy tắc).

---

## 1. Biểu mẫu Nhập liệu 1: Quản lý Lịch chiếu & Suất chiếu

- **Mục đích:** Cho phép Quản lý Chi nhánh nạp danh mục chi nhánh/phòng/phim, kiểm tra hợp lệ thời lượng phim (`BR-05`), khóa dòng phòng chiếu chống tranh chấp đồng thời (`FOR UPDATE`), phát hiện xung đột lịch chiếu trong cùng phòng (`BR-06`), thêm mới/cập nhật suất chiếu và phân trang danh sách suất chiếu (`50 dòng/trang`).
- **Đầu vào:** `movieId`, `roomId`, `startTime`, `endTime`, `basePrice`, `status` (`OPEN` / `CANCELLED`), bộ lọc phân trang `(branchId, roomId, movieId, dateFilter, limit, offset)`.
- **Bảng & View liên quan:** `cinema_branch`, `screening_room`, `movie`, `screening`, `booking`, `ticket`, `vw_screening_search`.
- **Lớp mã nguồn Java:** `com.cinema.feature.screening.ScreeningRepository` & `ScreeningService`.
- **Ảnh chụp màn hình demo:** `verification/screenshots/01_screening_management_valid.png` (hợp lệ), `verification/screenshots/02_screening_management_overlap_rejected.png` (từ chối trùng lịch `[BR-06]`).

### 1.1. Câu lệnh SQL tham số hóa (`?`) trong Java (`ScreeningRepository.java`)
```sql
-- (a) Nạp phòng chiếu đang hoạt động theo chi nhánh (findRoomsByBranch)
SELECT r.room_id, r.branch_id, b.branch_name, r.room_name, r.room_type, r.active
FROM screening_room r
JOIN cinema_branch b ON b.branch_id = r.branch_id
WHERE r.active = 1 AND r.branch_id = ?
ORDER BY r.branch_id ASC, r.room_id ASC;

-- (b) Trong giao dịch TransactionTemplate: khóa dòng phòng chiếu & kiểm tra active [BR-01, BR-06]
SELECT room_id, active FROM screening_room WHERE room_id = ? FOR UPDATE;

-- (c) Tra cứu thời lượng phim để kiểm tra end_time >= start_time + duration_minutes [BR-05]
SELECT movie_id, movie_code, title, duration_minutes, release_date, age_rating, country, description
FROM movie
WHERE movie_id = ?;

-- (d) Kiểm tra xung đột khung giờ chiếu trong cùng phòng [BR-06] (findOverlappingScreenings)
SELECT screening_id, movie_id, movie_code, movie_title, duration_minutes, age_rating,
       room_id, room_name, room_type, branch_id, branch_code, branch_name,
       start_time, end_time, base_price, screening_status, total_seats, sold_seats, available_seats
FROM vw_screening_search
WHERE room_id = ?
  AND screening_status = 'OPEN'
  AND start_time < ?
  AND end_time > ?
  AND screening_id <> ?; -- (chỉ thêm điều kiện này khi UPDATE suất chiếu hiện có)

-- (e) Kiểm tra số vé đã phát hành qua quan hệ chuẩn 3NF (ticket JOIN booking) trước khi đổi phòng/hủy [BR-08, BR-09]
SELECT COUNT(*)
FROM ticket tk
JOIN booking bk ON bk.booking_id = tk.booking_id
WHERE bk.screening_id = ?;

-- (f) Thêm mới suất chiếu (insertScreening) & Cập nhật suất chiếu (updateScreening)
INSERT INTO screening (movie_id, room_id, start_time, end_time, base_price, status)
VALUES (?, ?, ?, ?, ?, ?);

UPDATE screening
SET movie_id = ?, room_id = ?, start_time = ?, end_time = ?, base_price = ?, status = ?
WHERE screening_id = ?;

-- (g) Đếm tổng số dòng và truy vấn phân trang tường minh 50 dòng/trang (countScreenings & findScreeningsPage)
SELECT COUNT(*) FROM vw_screening_search WHERE 1=1 AND branch_id = ? AND screening_date = ?;

SELECT screening_id, movie_id, movie_code, movie_title, duration_minutes, age_rating,
       room_id, room_name, room_type, branch_id, branch_code, branch_name,
       start_time, end_time, base_price, screening_status, total_seats, sold_seats, available_seats
FROM vw_screening_search
WHERE 1=1
ORDER BY start_time DESC, screening_id DESC
LIMIT ? OFFSET ?;
```

### 1.2. Ví dụ SQL cụ thể chạy trên `cinema_chain_test` & Kết quả mong đợi
```sql
START TRANSACTION;
SELECT room_id, active FROM screening_room WHERE room_id = 1 FOR UPDATE;
-- Phát hiện trùng lịch với Suất #1 (13:00 -> 16:05 ngày 2026-11-04 tại Phòng 1):
SELECT screening_id, movie_title, room_name, start_time, end_time
FROM vw_screening_search
WHERE room_id = 1 AND screening_status = 'OPEN'
  AND start_time < '2026-11-04 15:00:00'
  AND end_time   > '2026-11-04 13:30:00';
-- Thêm suất hợp lệ vào khung trống ngày 2026-11-19:
INSERT INTO screening (movie_id, room_id, start_time, end_time, base_price, status)
VALUES (1, 1, '2026-11-19 09:00:00', '2026-11-19 11:15:00', 95000.00, 'OPEN');
ROLLBACK; -- Chỉ kiểm chứng, không lưu dữ liệu ví dụ
```
- **Kết quả mong đợi:** Truy vấn kiểm tra xung đột ngày `2026-11-04` trả về đúng `screening_id = 1` (*Dune: Hành Tinh Cát - Phần Hai*, `13:00:00 -> 16:05:00`) nên bị từ chối với mã `[BR-06]`; lệnh `INSERT` ngày `2026-11-19` thành công vì không giao nhau và thời lượng `135 phút >= 105 phút`.

---

## 2. Biểu mẫu Nhập liệu 2: Đặt vé, Chọn ghế Sơ đồ Phòng & Hủy Hoàn tiền

- **Mục đích:** Tải danh sách suất chiếu mở bán (có bộ lọc nhanh Chi nhánh + Ngày chiếu, hiển thị đầy đủ tổng số suất không cắt ngầm), hiển thị sơ đồ 80 ghế trực quan dựa trên bảng chuẩn 3NF `screening_seat_lock`, khóa dòng suất chiếu `FOR UPDATE`, tạo nguyên tử `booking` + `ticket` (không chứa `screening_id`) + `payment`, và xử lý hủy đơn hoàn tiền giải phóng ghế mở bán lại (`BR-10`, `BR-11`, `BR-12`, `BR-13`).
- **Đầu vào:** `screeningId`, `customerId` (`NULL` nếu Khách vãng lai tại quầy), `employeeId`, `seatIds` (danh sách mã ghế), `paymentMethod` (`CASH`/`CARD`/`TRANSFER`/`EWALLET`), `payImmediately` (`true` -> `CONFIRMED/SUCCESS`, `false` -> `PENDING/PENDING`).
- **Bảng liên quan:** `screening`, `seat`, `seat_type`, `screening_seat_lock`, `booking`, `ticket`, `payment`, `customer`, `employee`.
- **Lớp mã nguồn Java:** `com.cinema.feature.booking.BookingRepository` & `BookingService`.
- **Ảnh chụp màn hình demo:** `verification/screenshots/03_ticket_booking_seat_selection.png`, `04_ticket_booking_success.png`, `04b_ticket_booking_occupied_rejected.png`.

### 2.1. Câu lệnh SQL tham số hóa (`?`) trong Java (`BookingRepository.java`)
```sql
-- (a) Truy vấn sơ đồ 80 ghế của suất chiếu qua bảng khóa ghế chuẩn 3NF `screening_seat_lock` (findSeatsForScreening)
SELECT
    st.seat_id, st.room_id, st.seat_row, st.seat_number, st.seat_type_code,
    tp.type_name, tp.surcharge,
    (sc.base_price + tp.surcharge) AS final_unit_price,
    CASE WHEN sl.ticket_id IS NOT NULL THEN 1 ELSE 0 END AS is_occupied
FROM screening sc
JOIN seat st      ON st.room_id = sc.room_id
JOIN seat_type tp ON tp.seat_type_code = st.seat_type_code
LEFT JOIN screening_seat_lock sl
       ON sl.screening_id = sc.screening_id
      AND sl.seat_id = st.seat_id
WHERE sc.screening_id = ?
ORDER BY st.seat_row ASC, st.seat_number ASC;

-- (b) Giao dịch nguyên tử Đặt vé & Thanh toán (BookingService.createBooking trong TransactionTemplate):
-- Bước 1: Khóa bi quan dòng suất chiếu để tuần tự hóa các thu ngân đặt cùng suất
SELECT screening_id, room_id, base_price, status
FROM screening
WHERE screening_id = ?
FOR UPDATE;

-- Bước 2: Kiểm tra từng ghế thuộc đúng phòng chiếu và chưa bị giữ trong `screening_seat_lock`
SELECT
    st.seat_id, st.room_id,
    CONCAT(st.seat_row, LPAD(st.seat_number, 2, '0')) AS seat_label,
    tp.surcharge,
    CASE WHEN sl.ticket_id IS NOT NULL THEN 1 ELSE 0 END AS already_occupied
FROM seat st
JOIN seat_type tp ON tp.seat_type_code = st.seat_type_code
LEFT JOIN screening_seat_lock sl
       ON sl.screening_id = ?
      AND sl.seat_id = st.seat_id
WHERE st.seat_id = ?;

-- Bước 3: Tạo đơn đặt vé (customer_id truyền NULL nếu khách vãng lai)
INSERT INTO booking (booking_code, screening_id, customer_id, employee_id, booked_at, booking_status)
VALUES (?, ?, ?, ?, ?, ?);

-- Bước 4: Tạo từng vé chuẩn 3NF (KHÔNG có cột screening_id; Trigger tự chèn khóa vào screening_seat_lock)
INSERT INTO ticket (booking_id, seat_id, unit_price, ticket_status)
VALUES (?, ?, ?, ?);

-- Bước 5: Ghi nhận giao dịch thanh toán khớp tổng tiền vé hợp lệ [BR-13]
INSERT INTO payment (booking_id, amount, payment_method, payment_status, paid_at, transaction_ref)
VALUES (?, ?, ?, ?, ?, ?);
-- Kết thúc khối TransactionTemplate: conn.commit() nếu tất cả hợp lệ; conn.rollback() nếu có bất kỳ lỗi nào!

-- (c) Giao dịch nguyên tử Hủy đơn vé, Hoàn tiền & Giải phóng khóa ghế (cancelBookingRecords)
SELECT booking_id, booking_status FROM booking WHERE booking_id = ? FOR UPDATE;
UPDATE booking SET booking_status = 'CANCELLED' WHERE booking_id = ?;
UPDATE ticket  SET ticket_status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE booking_id = ?;
-- (Trigger `trg_ticket_after_upd` tự động DELETE dòng tương ứng khỏi `screening_seat_lock` ngay lập tức)
UPDATE payment SET payment_status = 'REFUNDED' WHERE booking_id = ? AND payment_status = 'SUCCESS';
UPDATE payment SET payment_status = 'FAILED'   WHERE booking_id = ? AND payment_status = 'PENDING';
```

### 2.2. Ví dụ SQL cụ thể chạy trên `cinema_chain_test` & Kết quả mong đợi
```sql
START TRANSACTION;
SELECT screening_id, room_id, base_price, status FROM screening WHERE screening_id = 1 FOR UPDATE;
INSERT INTO booking (booking_code, screening_id, customer_id, employee_id, booked_at, booking_status)
VALUES ('BK-DEMO-3NF-01', 1, NULL, 3, '2026-11-07 10:00:00', 'CONFIRMED');
SET @new_bk := LAST_INSERT_ID();

INSERT INTO ticket (booking_id, seat_id, unit_price, ticket_status)
VALUES (@new_bk, 3, 85000.00, 'VALID'), (@new_bk, 4, 85000.00, 'VALID');

INSERT INTO payment (booking_id, amount, payment_method, payment_status, paid_at, transaction_ref)
VALUES (@new_bk, 170000.00, 'CASH', 'SUCCESS', '2026-11-07 10:00:05', 'TXN-DEMO-3NF-01');

-- Hủy đơn vừa đặt -> giải phóng khóa ghế để bán lại:
UPDATE booking SET booking_status = 'CANCELLED' WHERE booking_id = @new_bk;
UPDATE ticket  SET ticket_status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE booking_id = @new_bk;
UPDATE payment SET payment_status = 'REFUNDED' WHERE booking_id = @new_bk AND payment_status = 'SUCCESS';
ROLLBACK;
```
- **Kết quả mong đợi:** Ngay sau `INSERT INTO ticket`, bảng `screening_seat_lock` tự động sinh 2 dòng khóa ghế `(1, 3)` và `(1, 4)` (chặn mọi giao dịch thứ hai đặt trùng ghế `A03`, `A04`). Ngay sau `UPDATE ticket SET ticket_status = 'CANCELLED'`, số khóa ghế trong `screening_seat_lock` của 2 vé này về `0` (mở khóa bán lại ghế), trong khi 2 dòng lịch sử `CANCELLED` trong `ticket` và giao dịch `REFUNDED` trong `payment` được giữ nguyên vẹn.

---

## 3. Màn hình Tìm kiếm Vận hành: Tra cứu Suất chiếu & Kiểm tra Ghế trống

- **Mục đích:** Lọc đa tiêu chí theo từ khóa tên/mã phim, bộ phim, chi nhánh, ngày chiếu, khoảng giờ bắt đầu và cờ *"Chỉ suất đang mở & còn ghế trống"*; hiển thị tổng hợp KPI trên toàn bộ tập kết quả khớp và phân trang bảng kết quả `50 dòng/trang` (loại bỏ hoàn toàn lỗi cắt ngầm `LIMIT 1000`).
- **Đầu vào:** `movieKeyword`, `movieId`, `branchId`, `screeningDate`, `fromTime`, `toTime`, `onlyOpenWithAvailableSeats`, `pageIndex`, `pageSize = 50`.
- **Bảng & View liên quan:** `vw_screening_search` (tổng hợp từ `screening`, `movie`, `screening_room`, `cinema_branch`, `seat`, `screening_seat_lock`).
- **Lớp mã nguồn Java:** `com.cinema.feature.search.ScreeningSearchRepository` & `ScreeningSearchService`.
- **Ảnh chụp màn hình demo:** `verification/screenshots/05_screening_search.png`.

### 3.1. Câu lệnh SQL tham số hóa (`?`) trong Java (`ScreeningSearchRepository.java`)
```sql
-- (a) Tổng hợp chính xác KPI trên toàn tập kết quả khớp điều kiện lọc (summarizeSearch)
SELECT
    COUNT(*) AS total_count,
    COALESCE(SUM(available_seats), 0) AS sum_available,
    COALESCE(SUM(sold_seats), 0) AS sum_sold
FROM vw_screening_search
WHERE 1=1
  AND (LOWER(movie_title) LIKE ? OR LOWER(movie_code) LIKE ?)
  AND branch_id = ?
  AND screening_date = ?
  AND TIME(start_time) >= ?
  AND TIME(start_time) <= ?
  AND screening_status = 'OPEN' AND available_seats > 0;

-- (b) Truy vấn phân trang kết quả tra cứu với thứ tự ổn định (searchScreeningsPage)
SELECT
    screening_id, movie_id, movie_code, movie_title, duration_minutes, age_rating,
    room_id, room_name, room_type, branch_id, branch_code, branch_name,
    start_time, end_time, base_price, screening_status,
    total_seats, sold_seats, available_seats
FROM vw_screening_search
WHERE 1=1
  AND branch_id = ?
  AND screening_date = ?
  AND TIME(start_time) >= ?
  AND TIME(start_time) <= ?
  AND screening_status = 'OPEN' AND available_seats > 0
ORDER BY start_time ASC, branch_id ASC, room_id ASC, screening_id ASC
LIMIT ? OFFSET ?;
```

### 3.2. Ví dụ SQL cụ thể chạy trên `cinema_chain_test` & Kết quả mong đợi
```sql
SELECT screening_id, movie_code, movie_title, branch_name, room_name,
       start_time, base_price, total_seats, sold_seats, available_seats
FROM vw_screening_search
WHERE branch_id = 1
  AND screening_date = '2026-11-04'
  AND TIME(start_time) >= '08:00:00'
  AND TIME(start_time) <= '23:00:00'
  AND screening_status = 'OPEN'
  AND available_seats > 0
ORDER BY start_time ASC, screening_id ASC
LIMIT 50 OFFSET 0;
```
- **Kết quả thực tế trên bộ dữ liệu mẫu (`10_seed.sql`):** Trả về đúng **06 suất chiếu** tại *CineViet Vincom Bà Triệu* ngày `04/11/2026` (`total_count = 6`, tổng vé đang giữ `sum_sold = 14`, tổng ghế còn trống `sum_available = 466`, khớp `6 * 80 - 14 = 466`).

---

## 4. Báo cáo Quản trị 1: Báo cáo Doanh thu Chuỗi rạp

- **Định danh quản trị:** **Người lập:** *Kế toán Chuỗi rạp* $\rightarrow$ **Người nhận:** *Giám đốc Kinh doanh Chuỗi rạp*.
- **Mục đích:** Tổng hợp số đơn đã thu tiền, số vé hợp lệ và tổng doanh thu thực thu theo từng Chi nhánh và theo Ngày thanh toán (`paid_at`), phục vụ xem trên màn hình (Preview) và kết xuất PDF tiếng Việt (`reports/exports/BaoCaoDoanhThu_Mau.pdf`).
- **Đầu vào:** `fromDate`, `toDate`, `branchId` (tùy chọn `NULL` = toàn chuỗi).
- **Bảng & View liên quan:** `cinema_branch`, `screening_room`, `screening`, `booking`, `ticket`, `payment`, `vw_verified_booking_revenue`, `vw_branch_daily_revenue`.
- **Lớp mã nguồn Java:** `com.cinema.feature.report.revenue.RevenueReportRepository` & `RevenueReportService`.
- **Ảnh chụp màn hình & PDF demo:** `verification/screenshots/06_revenue_report_preview.png`, `08_exported_revenue_pdf_page1.png`, `reports/exports/BaoCaoDoanhThu_Mau.pdf`.

### 4.1. Quy tắc Hạch toán Minh bạch Các Trạng thái `SUCCESS`, `PENDING`, `CANCELLED`, `REFUNDED`, `FAILED`

| Trạng thái Đơn (`booking_status`) | Trạng thái Vé (`ticket_status`) | Trạng thái Thanh toán (`payment_status`) | Giữ Khóa Ghế (`screening_seat_lock`)? | Ghi nhận vào Doanh thu Thực thu? | Giải thích Nghiệp vụ Kế toán & Vận hành |
|---|---|---|---|---|---|
| `CONFIRMED` | `VALID` | `SUCCESS` *(kể cả đơn từng có dòng `FAILED` trước đó)* | **Có** (`292` ghế trên seed) | **CÓ — Ghi nhận đúng 1 lần** (`125` đơn = `35.165.000 VNĐ`) | Chỉ tổng hợp `SUM(ticket.unit_price)` của vé `VALID` một lần duy nhất nhờ `vw_verified_booking_revenue` / `EXISTS`, không bị nhân đôi bởi dòng `FAILED` cũ (`BR-14`, `BR-15`). |
| `PENDING` | `VALID` | `PENDING` | **Có** (`35` ghế đang giữ chỗ tạm) | **KHÔNG (`0 VNĐ`)** | Đơn đặt giữ chỗ chờ thu tiền: ghế đã trừ khỏi `available_seats`, nhưng chưa thu tiền nên Kế toán chưa ghi nhận doanh thu thực thu. |
| `CANCELLED` | `CANCELLED` | `REFUNDED` | **Không** (đã xóa khóa, mở bán lại ghế) | **KHÔNG (`0 VNĐ`)** | Đơn đã thu tiền nhưng khách hủy hợp lệ và đã hoàn tiền (`REFUNDED`): loại trừ hoàn toàn khỏi doanh thu thực thu. |
| `CANCELLED` | `CANCELLED` | `FAILED` | **Không** | **KHÔNG (`0 VNĐ`)** | Giao dịch thanh toán thất bại và đơn đã hủy. |

Hai truy vấn tổng chi nhánh và chi tiết ngày được thực hiện trên cùng một connection read-only ở mức REPEATABLE READ để dùng chung snapshot; không loại doanh thu lịch sử của chi nhánh đã ngừng hoạt động.

### 4.2. Câu lệnh SQL tham số hóa (`?`) trong Java (`RevenueReportRepository.java`)
```sql
-- (a) Bảng I: Tổng hợp doanh thu thực thu theo từng Chi nhánh (findBranchSummaries)
SELECT
    br.branch_id, br.branch_code, br.branch_name, br.city,
    COUNT(DISTINCT vr.booking_id)              AS paid_bookings,
    COALESCE(SUM(vr.valid_ticket_count), 0)    AS valid_tickets,
    COALESCE(SUM(vr.recognized_revenue), 0.00) AS paid_revenue
FROM cinema_branch br
LEFT JOIN vw_verified_booking_revenue vr
    ON vr.branch_id = br.branch_id
   AND vr.revenue_date BETWEEN ? AND ?
WHERE 1 = 1
GROUP BY br.branch_id, br.branch_code, br.branch_name, br.city
ORDER BY paid_revenue DESC, br.branch_id ASC;

-- (b) Bảng II: Chi tiết doanh thu theo Ngày thanh toán & Chi nhánh (findDailyDetails)
SELECT
    revenue_date, branch_code, branch_name,
    paid_booking_count, paid_ticket_count, total_paid_revenue
FROM vw_branch_daily_revenue
WHERE revenue_date BETWEEN ? AND ?
ORDER BY revenue_date ASC, branch_code ASC;

-- (c) Truy vấn SQL độc lập trực tiếp trên bảng gốc dùng EXISTS để đối soát chéo (queryIndependentVerifiedRevenue)
SELECT COALESCE(SUM(tk.unit_price), 0.00) AS total_rev
FROM ticket tk
JOIN booking bk        ON bk.booking_id = tk.booking_id
JOIN screening sc      ON sc.screening_id = bk.screening_id
JOIN screening_room rm ON rm.room_id = sc.room_id
WHERE tk.ticket_status = 'VALID'
  AND bk.booking_status = 'CONFIRMED'
  AND EXISTS (
      SELECT 1
      FROM payment pm
      WHERE pm.booking_id = bk.booking_id
        AND pm.payment_status = 'SUCCESS'
        AND DATE(pm.paid_at) BETWEEN ? AND ?
  );
```

### 4.3. Kết quả chạy thực tế kỳ `2026-11-01` $\to$ `2026-11-15` trên `cinema_chain_test`
- `CN-HN01` (*CineViet Vincom Bà Triệu*): `36 đơn`, `84 vé`, **`10.140.000 VNĐ`**.
- `CN-HCM01` (*CineViet Landmark 81*): `34 đơn`, `79 vé`, **`9.370.000 VNĐ`**.
- `CN-HP01` (*CineViet Aeon Mall Hải Phòng*): `29 đơn`, `69 vé`, **`8.325.000 VNĐ`**.
- `CN-DN01` (*CineViet Cầu Rồng Đà Nẵng*): `26 đơn`, `60 vé`, **`7.330.000 VNĐ`**.
- **Tổng cộng toàn chuỗi:** **`125 đơn`**, **`292 vé`**, **`35.165.000 VNĐ`** (sai lệch so với SQL độc lập = **`0,00 VNĐ`**).

---

## 5. Báo cáo Quản trị 2: Báo cáo Tình hình Bán vé & Tỷ lệ Lấp đầy

- **Định danh quản trị:** **Người lập:** *Quản lý Chi nhánh* $\rightarrow$ **Người nhận:** *Ban Điều hành và Quản lý Kinh doanh*.
- **Mục đích:** Thống kê hiệu quả khai thác từng suất chiếu theo ngày chiếu (`screening_date`) và chi nhánh: sức chứa phòng (`room_capacity`), số vé đã thanh toán thành công (`tickets_sold`), số ghế còn trống thực tế (`available_seats` — trừ cả các ghế đang giữ bởi đơn `PENDING`), tỷ lệ lấp đầy (`occupancy_rate_pct %`) và doanh thu bán vé (`sales_revenue`), xuất PDF tiếng Việt (`reports/exports/BaoCaoBanVe_Mau.pdf`).
- **Đầu vào:** `fromDate`, `toDate`, `branchId` (tùy chọn), `screeningId` (tùy chọn).
- **Bảng & View liên quan:** `vw_screening_occupancy` (tổng hợp từ `screening`, `movie`, `screening_room`, `cinema_branch`, `seat`, `screening_seat_lock`, `vw_verified_booking_revenue`).
- **Lớp mã nguồn Java:** `com.cinema.feature.report.sales.TicketSalesReportRepository` & `TicketSalesReportService`.
- **Ảnh chụp màn hình & PDF demo:** `verification/screenshots/07_ticket_sales_report_preview.png`, `09_exported_sales_pdf_page1.png`, `reports/exports/BaoCaoBanVe_Mau.pdf`.

### 5.1. Câu lệnh SQL tham số hóa (`?`) trong Java (`TicketSalesReportRepository.java`)
```sql
-- (a) Truy vấn chi tiết bán vé & tỷ lệ lấp đầy theo Suất chiếu (findScreeningSales)
SELECT
    screening_id, screening_date, start_time, end_time,
    branch_name, room_name, room_type, movie_title,
    room_capacity, tickets_sold, available_seats,
    occupancy_rate_pct, sales_revenue, screening_status
FROM vw_screening_occupancy
WHERE screening_date BETWEEN ? AND ?
  AND branch_id = ?
ORDER BY start_time ASC, screening_id ASC;

-- (b) Truy vấn SQL độc lập trên bảng gốc chuẩn 3NF (ticket -> booking -> screening) để kiểm chứng chéo
SELECT COALESCE(SUM(tk.unit_price), 0.00) AS total_sales_rev
FROM ticket tk
JOIN booking bk        ON bk.booking_id = tk.booking_id
JOIN screening sc      ON sc.screening_id = bk.screening_id
JOIN screening_room rm ON rm.room_id = sc.room_id
WHERE tk.ticket_status = 'VALID'
  AND bk.booking_status = 'CONFIRMED'
  AND DATE(sc.start_time) BETWEEN ? AND ?
  AND EXISTS (
      SELECT 1
      FROM payment pm
      WHERE pm.booking_id = bk.booking_id
        AND pm.payment_status = 'SUCCESS'
  );
```

### 5.2. Ví dụ SQL cụ thể chạy trên `cinema_chain_test` & Kết quả mong đợi
```sql
SELECT screening_id, movie_title, room_name, room_capacity,
       tickets_sold, available_seats, occupancy_rate_pct, sales_revenue
FROM vw_screening_occupancy
WHERE screening_date BETWEEN '2026-11-04' AND '2026-11-04'
  AND branch_id = 1
ORDER BY start_time ASC, screening_id ASC;
```
- **Kết quả thực tế trên bộ dữ liệu mẫu (`10_seed.sql`):** Trả về 06 suất chiếu ngày `04/11/2026` tại Chi nhánh #1 (VD: Suất `#3` *Quật Mộ Trùng Ma*: sức chứa `80`, đã bán `3` vé, còn trống `77` ghế, lấp đầy `3.75%`, doanh thu `330.000 VNĐ`; trên toàn kỳ `04/11/2026 -> 10/11/2026` toàn chuỗi khớp chính xác **`292 vé`** và **`35.165.000 VNĐ`**).

---

## 6. Biểu diễn Đại số Quan hệ (Slide 8) & Đối chiếu Lý thuyết SQL (Slide 9–12, TS. Nguyễn Quỳnh Chi)

### 6.1. Hệ thống Ký hiệu Đại số Quan hệ Chuẩn theo Bài giảng Chương 3 (Slide 8)
Theo Slide 8 (*Ngôn ngữ truy vấn Đại số quan hệ*) và Giáo trình *Bài giảng DBMS v1.1* (trang 79–98):
- **5 phép toán cơ bản:** Phép chọn $\sigma_{(\text{mệnh đề})}(r)$ (kết hợp $=, \neq, <, \le, >, \ge$ và $\land, \lor, \neg$), Phép chiếu $\pi_{(\text{danh sách thuộc tính})}(r)$, Phép hợp $r \cup s$ (trên hai quan hệ *khả hợp*: cùng bậc và miền giá trị tương ứng giống nhau), Phép trừ $r - s$ (khả hợp), Phép tích Đề-các $r \times s$; đi kèm toán tử đặt lại tên $\rho_{x(A_1, \dots, A_n)}(r)$ (Slide 8, trang 54–56).
- **Các phép toán mở rộng:** Phép giao $r \cap s \equiv r - (r - s)$; Phép kết nối theta/bằng $r \bowtie_{(\text{predicate})} s \equiv \sigma_{(\text{predicate})}(r \times s)$; **Phép kết nối tự nhiên** ký hiệu bằng dấu sao **`r * s`** (kết nối bằng trên mọi thuộc tính cùng tên và giữ mỗi thuộc tính chung một lần — Slide 8, trang 36–37); **Phép kết nối ngoài** gồm kết nối ngoài trái $r \supset\!\triangleleft s$, kết nối ngoài phải $r \triangleright\!\subset s$ và kết nối ngoài đầy đủ $r \supset\!\triangleleft\triangleright\!\subset s$ (mở rộng kết nối tự nhiên, giữ lại các bộ không khớp và gán giá trị rỗng `null` — Slide 8, trang 38–39); **Phép bán kết nối (Semi-Join)** $r \triangleright_{(\text{predicate})} s \equiv \pi_{(\text{thuộc tính của } r)}(r \bowtie_{(\text{predicate})} s)$ (Slide 8, trang 40–41); và **Phép chia (Division)** $r \div s \equiv \pi_{(X-Y)}(r) - \pi_{(X-Y)}((\pi_{(X-Y)}(r) \times s) - r)$ với $Y \subseteq X$ (Slide 8, trang 42–48).

### 6.2. Biểu thức Đại số Quan hệ Tương ứng cho Các Chức năng và Phép Chia
1. **Kiểm tra xung đột khung giờ chiếu cùng phòng (`BR-06` — Phép chọn $\sigma$ & Phép chiếu $\pi$):**
   $$Q_{\text{overlap}} = \pi_{(\text{screening\_id}, \text{movie\_id}, \text{start\_time}, \text{end\_time})}\big(\sigma_{(\text{room\_id} = r_0 \land \text{status} = \text{'OPEN'} \land \text{start\_time} < t_{\text{end}} \land \text{end\_time} > t_{\text{start}})}(\text{screening})\big)$$
2. **Nạp sơ đồ ghế của suất chiếu $s_0$ (Kết nối tự nhiên `*` kết hợp Kết nối ngoài trái $\supset\!\triangleleft$ — Slide 8, trang 36–39):**
   $$S_{\text{map}}(s_0) = \big(\sigma_{(\text{screening\_id} = s_0)}(\text{screening}) * \text{seat} * \text{seat\_type}\big) \supset\!\triangleleft \text{screening\_seat\_lock}$$
   *(Kết nối tự nhiên `*` ghép suất chiếu $s_0$ với 80 ghế cùng `room_id` và phụ thu hạng ghế `seat_type_code`; tiếp theo phép kết nối ngoài trái $\supset\!\triangleleft$ với `screening_seat_lock` trên hai thuộc tính chung `(screening_id, seat_id)` giữ đủ cả 80 ghế, trong đó ghế trống nhận `ticket_id = null` và ghế đang giữ nhận `ticket_id != null`).*
3. **Tìm tập mã ghế còn trống của suất chiếu $s_0$ (Phép trừ $r - s$ trên hai quan hệ khả hợp bậc 1):**
   $$G_{\text{trống}}(s_0) = \pi_{(\text{seat\_id})}\big(\sigma_{(\text{screening\_id} = s_0)}(\text{screening}) * \text{seat}\big) - \pi_{(\text{seat\_id})}\big(\sigma_{(\text{screening\_id} = s_0)}(\text{screening\_seat\_lock})\big)$$
4. **Tập vé hợp lệ đã thu tiền phục vụ Báo cáo Doanh thu & Bán vé (Kết nối tự nhiên `*` và Phép bán kết nối $\triangleright$ — Slide 8, trang 40–41):**
   $$V_{\text{paid}} = \Big(\sigma_{(\text{ticket\_status} = \text{'VALID'})}(\text{ticket}) * \sigma_{(\text{booking\_status} = \text{'CONFIRMED'})}(\text{booking})\Big) \triangleright_{(\text{booking.booking\_id} = \text{payment.booking\_id})} \sigma_{(\text{payment\_status} = \text{'SUCCESS'} \land \text{DATE(paid\_at)} \in [d_1, d_2])}(\text{payment})$$
   *(Phép bán kết nối $\triangleright$ tương ứng mệnh đề `EXISTS (SELECT 1 FROM payment ...)` trong Mục 4.2c và 5.1b: chỉ giữ lại các bộ thuộc quan hệ bên trái mà không nhân bản dòng vé nếu lịch sử `payment` có nhiều giao dịch).*
5. **Truy vấn Phép Chia ($\div$) trên CSDL Chuỗi Rạp theo 5 Bước $T_1 \dots T_5$ (Slide 8 & Slide 11 trang 18–19):**
   - **Bài toán:** *Tìm mã phim (`movie_id`) có lịch chiếu mở (`status = 'OPEN'`) tại tất cả các chi nhánh đang hoạt động (`active = 1`).*
   - **Giải pháp dùng phép toán mở rộng $\div$ (Slide 8):**
     $$R_{\text{mb}} = \pi_{(\text{movie\_id}, \text{branch\_id})}\big(\sigma_{(\text{status} = \text{'OPEN'})}(\text{screening}) * \text{screening\_room}\big), \quad S_{\text{br}} = \pi_{(\text{branch\_id})}\big(\sigma_{(\text{active} = 1)}(\text{cinema\_branch})\big)$$
     $$Q_{\text{all\_branches}} = R_{\text{mb}} \div S_{\text{br}}$$
   - **Giải pháp chỉ dùng 5 phép toán cơ bản (tương đương định nghĩa $r \div s$ trên Slide 8):**
     - $T_1 = R_{\text{mb}}$ *(tất cả các cặp `(movie_id, branch_id)` có lịch chiếu mở thực tế)*;
     - $T_2 = S_{\text{br}}$ *(tất cả các `branch_id` đang hoạt động)*;
     - $T_3 = \pi_{(\text{movie\_id})}(T_1) \times T_2$ *(ghép mọi `movie_id` trong $T_1$ với mọi `branch_id` trong $T_2$)*;
     - $T_4 = T_3 - T_1$ *(các cặp `(movie_id, branch_id)` bị khuyết lịch chiếu)*;
     - $T_5 = \pi_{(\text{movie\_id})}(T_1) - \pi_{(\text{movie\_id})}(T_4)$ *(loại bỏ các phim còn thiếu ít nhất một chi nhánh — cho kết quả đúng bằng $R_{\text{mb}} \div S_{\text{br}}$)*.
   - **Câu lệnh SQL tương đương trên MySQL 8.4 (theo mẫu `GROUP BY / HAVING COUNT(DISTINCT ...)` trên Slide 11 trang 19 và phủ định kép `NOT EXISTS`):**
     ```sql
     SELECT m.movie_id, m.movie_code, m.title
     FROM movie m
     JOIN screening sc      ON sc.movie_id = m.movie_id AND sc.status = 'OPEN'
     JOIN screening_room rm ON rm.room_id = sc.room_id
     JOIN cinema_branch br  ON br.branch_id = rm.branch_id AND br.active = 1
     GROUP BY m.movie_id, m.movie_code, m.title
     HAVING COUNT(DISTINCT br.branch_id) = (
         SELECT COUNT(*) FROM cinema_branch WHERE active = 1
     );
     ```

### 6.3. Đối chiếu Tuân thủ Quy tắc Ngôn ngữ SQL (Slide 9, 10, 11, 12)
- **Đặt tên tường minh cho các ràng buộc toàn vẹn (Slide 9):** Toàn bộ `01_schema.sql` và `01b_foreign_keys.sql` đều đặt tên `CONSTRAINT` chuẩn hóa (`pk_*`, `fk_*`, `uq_*`, `chk_*`), đồng thời phân biệt rõ `PRIMARY KEY` (luôn `NOT NULL`) với `UNIQUE` trên cột cho phép `NULL` (`transaction_ref`, `active_success_booking`).
- **Xử lý logic ba giá trị của `NULL` và toán tử miền (Slide 10):** Mọi điều kiện kiểm tra rỗng đều dùng `IS NULL` / `IS NOT NULL` (không dùng `= NULL`); các phép tổng hợp báo cáo đều bọc `COALESCE(..., 0)` để tránh `NULL` làm rỗng kết quả cộng; lọc khoảng ngày/giờ dùng `BETWEEN ... AND ...` và tìm kiếm chuỗi dùng `LIKE`.
- **Quy tắc $N - 1$ điều kiện kết nối không vòng tròn, `LEFT JOIN` và 4 đặc tính `VIEW` (Slide 11, trang 24–34):**
  - Truy vấn đối soát 4 bảng `ticket -> booking -> screening -> screening_room` (Mục 4.2c, 5.1b) dùng đúng $4 - 1 = 3$ điều kiện kết nối khóa ngoại, tránh kết nối vòng tròn dư thừa qua `seat` (Slide 11, trang 29).
  - Báo cáo doanh thu chi nhánh dùng `cinema_branch LEFT JOIN vw_verified_booking_revenue` (Slide 11, trang 32–34) để giữ lại cả chi nhánh có doanh thu bằng `0` trong kỳ lọc.
  - Bốn giao diện hiển thị (`vw_screening_search`, `vw_verified_booking_revenue`, `vw_branch_daily_revenue`, `vw_screening_occupancy`) trong `03_views.sql` vận dụng đúng 4 đặc tính của `CREATE VIEW` trên Slide 11 trang 26: dùng thay tên bảng trong `SELECT`, tự động cập nhật dữ liệu mới nhất khi kích hoạt, giới hạn cột/hàng theo ngữ cảnh nghiệp vụ, và làm nguồn tổng hợp chuẩn cho báo cáo.
- **Đơn vị giao dịch nguyên tử `COMMIT` / `ROLLBACK` (Slide 12):** Mọi chuỗi lệnh `SELECT ... FOR UPDATE` + `INSERT`/`UPDATE` nhiều bảng trong `TransactionTemplate` chỉ `COMMIT` khi toàn bộ quy tắc hợp lệ, và lập tức `ROLLBACK` khôi phục tính nhất quán CSDL khi xảy ra lỗi hoặc vi phạm Trigger.

## Thanh toán VNPay Sandbox (mở rộng demo, chỉ DB test)

Đặc tả: ../requirements/sandbox-payment.md. Launcher SandboxMain dùng
AppConfig.loadTest, HMAC-SHA512, IPN có kiểm tra merchant/reference/amount,
locking booking/payment và idempotency. Return URL không cập nhật CSDL.
Giữ 14 bảng/12 trigger; không thêm trạng thái. Main bình thường không bật callback.

Đối soát callback thành công đến sau khi đơn đã hủy/hết hạn:

```sql
SELECT b.booking_id, b.booking_status, p.payment_id, p.amount,
       p.payment_status, p.transaction_ref
FROM booking b JOIN payment p ON p.booking_id = b.booking_id
WHERE p.transaction_ref LIKE 'VNPAY-LATE-%';
```

Dấu LATE nghĩa là cổng báo tiền về sau khi đơn không còn hiệu lực: không ghi
nhận doanh thu/không chiếm lại ghế; cần đối soát thủ công. Không diễn giải FAILED
của đơn nội bộ là ngân hàng chưa trừ tiền, hoặc là đã refund. API hoàn tiền/query
chưa tích hợp; không dùng demo để nhận tiền thật.
