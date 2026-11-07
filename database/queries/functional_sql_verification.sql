-- =============================================================================
-- TỆP: functional_sql_verification.sql
-- MỤC ĐÍCH: Bộ câu lệnh SQL ví dụ chạy được trên `cinema_chain_test` tương ứng
--           1-1 với các câu lệnh tham số hóa (?) trong mã nguồn Java của 5 màn hình
--           chấm điểm R5 (2 Form nhập liệu), R6 (Tìm kiếm), R7 (2 Báo cáo).
-- LƯU Ý AN TOÀN: Mọi ví dụ ghi (INSERT/UPDATE) đều bọc trong giao dịch và kết thúc
--                bằng ROLLBACK để bảo toàn nguyên trạng dữ liệu mẫu kiểm thử.
-- =============================================================================

SET NAMES utf8mb4;

SELECT '=== [R5.1] FORM 1: QUẢN LÝ SUẤT CHIẾU (KIỂM TRA PHÒNG, CHỐNG TRÙNG LỊCH, INSERT & UPDATE) ===' AS section_banner;

START TRANSACTION;

-- 1a. Khóa dòng phòng chiếu và kiểm tra trạng thái hoạt động [BR-01] (ScreeningRepository.lockRoomAndCheckActive)
SELECT room_id, active
FROM screening_room
WHERE room_id = 1
FOR UPDATE;

-- 1b. Tra cứu thời lượng bộ phim để kiểm tra thời lượng suất chiếu tối thiểu [BR-05] (ScreeningRepository.findMovieById)
SELECT movie_id, movie_code, title, duration_minutes, release_date, age_rating
FROM movie
WHERE movie_id = 1;

-- 1c. Kiểm tra xung đột lịch chiếu trong cùng phòng [BR-06] (ScreeningRepository.findOverlappingScreenings)
--     Ví dụ khung giờ trống hợp lệ ngày 2026-11-19 (09:00 -> 11:15) tại Phòng 1 -> Kỳ vọng 0 dòng xung đột:
SELECT screening_id, movie_title, room_name, start_time, end_time, screening_status
FROM vw_screening_search
WHERE room_id = 1
  AND screening_status = 'OPEN'
  AND start_time < '2026-11-19 11:15:00'
  AND end_time   > '2026-11-19 09:00:00';

-- 1d. Kiểm tra phát hiện trùng lịch với Suất #1 hiện có ngày 2026-11-04 (13:00 -> 15:31) tại Phòng 1 -> Kỳ vọng >= 1 dòng xung đột:
SELECT screening_id, movie_title, room_name, start_time, end_time, screening_status
FROM vw_screening_search
WHERE room_id = 1
  AND screening_status = 'OPEN'
  AND start_time < '2026-11-04 15:00:00'
  AND end_time   > '2026-11-04 13:30:00';

-- 1e. Thêm mới suất chiếu hợp lệ (ScreeningRepository.insertScreening)
INSERT INTO screening (movie_id, room_id, start_time, end_time, base_price, status)
VALUES (1, 1, '2026-11-19 09:00:00', '2026-11-19 11:15:00', 95000.00, 'OPEN');

SET @demo_new_screening_id := LAST_INSERT_ID();

-- 1f. Kiểm tra số vé đã phát hành trước khi cho phép đổi phòng hoặc hủy suất (ScreeningRepository.countTicketsForScreening)
SELECT COUNT(*) AS existing_tickets_for_new_screening
FROM ticket tk
JOIN booking bk ON bk.booking_id = tk.booking_id
WHERE bk.screening_id = @demo_new_screening_id;

-- 1g. Cập nhật giá cơ sở và khung giờ của suất chiếu vừa tạo (ScreeningRepository.updateScreening)
UPDATE screening
SET movie_id = 1,
    room_id = 1,
    start_time = '2026-11-19 09:30:00',
    end_time = '2026-11-19 11:45:00',
    base_price = 105000.00,
    status = 'OPEN'
WHERE screening_id = @demo_new_screening_id;

SELECT screening_id, movie_title, room_name, start_time, end_time, base_price, screening_status
FROM vw_screening_search
WHERE screening_id = @demo_new_screening_id;

ROLLBACK;

SELECT '=== [R5.2] FORM 2: ĐẶT VÉ & CHỌN GHẾ (SƠ ĐỒ GHẾ 3NF, FOR UPDATE, TẠO ĐƠN/VÉ/THANH TOÁN, HỦY/HOÀN TIỀN) ===' AS section_banner;

-- 2a. Truy vấn sơ đồ ghế trực quan của Suất #1 qua bảng khóa ghế 3NF `screening_seat_lock` (BookingRepository.findSeatsForScreening)
SELECT
    st.seat_id,
    st.room_id,
    st.seat_row,
    st.seat_number,
    st.seat_type_code,
    tp.type_name,
    tp.surcharge,
    (sc.base_price + tp.surcharge) AS final_unit_price,
    CASE WHEN sl.ticket_id IS NOT NULL THEN 1 ELSE 0 END AS is_occupied
FROM screening sc
JOIN seat st      ON st.room_id = sc.room_id
JOIN seat_type tp ON tp.seat_type_code = st.seat_type_code
LEFT JOIN screening_seat_lock sl
       ON sl.screening_id = sc.screening_id
      AND sl.seat_id = st.seat_id
WHERE sc.screening_id = 1
ORDER BY st.seat_row ASC, st.seat_number ASC
LIMIT 12;

-- 2b. Giao dịch nguyên tử đặt vé + thanh toán + hủy hoàn tiền trên suất chiếu thử nghiệm (mô phỏng đầy đủ BookingService)
START TRANSACTION;

INSERT INTO screening (movie_id, room_id, start_time, end_time, base_price, status)
VALUES (1, 1, '2026-11-20 18:00:00', '2026-11-20 20:15:00', 100000.00, 'OPEN');
SET @bk_demo_scr_id := LAST_INSERT_ID();

-- Khóa dòng suất chiếu chống tranh chấp đồng thời (BookingRepository.lockScreeningForUpdate)
SELECT screening_id, room_id, base_price, status
FROM screening
WHERE screening_id = @bk_demo_scr_id
FOR UPDATE;

-- Kiểm tra ghế #1 (A01 - STD) và ghế #45 (F05 - VIP) trước khi chèn vé (BookingRepository.inspectSeatForBooking)
SELECT
    st.seat_id,
    st.room_id,
    CONCAT(st.seat_row, LPAD(st.seat_number, 2, '0')) AS seat_label,
    tp.surcharge,
    CASE WHEN sl.ticket_id IS NOT NULL THEN 1 ELSE 0 END AS already_occupied
FROM seat st
JOIN seat_type tp ON tp.seat_type_code = st.seat_type_code
LEFT JOIN screening_seat_lock sl
       ON sl.screening_id = @bk_demo_scr_id
      AND sl.seat_id = st.seat_id
WHERE st.seat_id IN (1, 45);

-- Tạo đơn đặt vé (BookingRepository.insertBooking)
INSERT INTO booking (booking_code, screening_id, customer_id, employee_id, booked_at, booking_status)
VALUES ('BK-SQL-DEMO-001', @bk_demo_scr_id, 1, 3, '2026-11-07 10:00:00', 'CONFIRMED');
SET @bk_demo_id := LAST_INSERT_ID();

-- Tạo các dòng vé chuẩn 3NF — KHÔNG lưu cột dư thừa screening_id trên ticket (BookingRepository.insertTicket)
-- Trigger `trg_ticket_lock_after_ins` tự động ghi khóa ghế vào `screening_seat_lock`
INSERT INTO ticket (booking_id, seat_id, unit_price, ticket_status)
VALUES
    (@bk_demo_id, 1,  100000.00, 'VALID'),
    (@bk_demo_id, 45, 125000.00, 'VALID');

-- Ghi nhận giao dịch thanh toán thành công (BookingRepository.insertPayment)
INSERT INTO payment (booking_id, amount, payment_method, payment_status, paid_at, transaction_ref)
VALUES (@bk_demo_id, 225000.00, 'CARD', 'SUCCESS', '2026-11-07 10:00:05', 'TXN-SQL-DEMO-001');

-- Kiểm tra trạng thái khóa ghế tự động trong `screening_seat_lock` (Kỳ vọng: 2 dòng khóa cho ghế 1 và 45)
SELECT screening_id, seat_id, ticket_id
FROM screening_seat_lock
WHERE screening_id = @bk_demo_scr_id
ORDER BY seat_id ASC;

-- Minh họa Hủy đơn & Hoàn tiền giải phóng ghế để bán lại (BookingRepository.cancelBookingRecords)
UPDATE booking SET booking_status = 'CANCELLED' WHERE booking_id = @bk_demo_id;
UPDATE ticket  SET ticket_status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE booking_id = @bk_demo_id;
UPDATE payment SET payment_status = 'REFUNDED' WHERE booking_id = @bk_demo_id AND payment_status = 'SUCCESS';
UPDATE payment SET payment_status = 'FAILED'   WHERE booking_id = @bk_demo_id AND payment_status = 'PENDING';

-- Kiểm tra sau khi hủy: khóa ghế được giải phóng về 0, vé CANCELLED và giao dịch REFUNDED vẫn lưu lịch sử
SELECT
    (SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id = @bk_demo_scr_id) AS active_locks_after_cancel,
    (SELECT COUNT(*) FROM ticket WHERE booking_id = @bk_demo_id AND ticket_status = 'CANCELLED') AS cancelled_history_tickets,
    (SELECT payment_status FROM payment WHERE booking_id = @bk_demo_id) AS refunded_payment_status;

-- Trong ứng dụng thật, giao dịch hợp lệ kết thúc bằng COMMIT; ở kịch bản kiểm chứng này dùng ROLLBACK để giữ sạch DB test
ROLLBACK;

SELECT '=== [R6] MÀN HÌNH 3: TRA CỨU SUẤT CHIẾU & KIỂM TRA GHẾ TRỐNG (TỔNG HỢP KPI + PHÂN TRANG) ===' AS section_banner;

-- 3a. Tổng hợp KPI toàn tập kết quả khớp bộ lọc (ScreeningSearchRepository.summarizeSearch)
SELECT
    COUNT(*) AS total_count,
    COALESCE(SUM(available_seats), 0) AS sum_available,
    COALESCE(SUM(sold_seats), 0) AS sum_sold
FROM vw_screening_search
WHERE 1 = 1
  AND branch_id = 1
  AND screening_date = '2026-11-04'
  AND TIME(start_time) >= '08:00:00'
  AND TIME(start_time) <= '23:00:00'
  AND screening_status = 'OPEN'
  AND available_seats > 0;

-- 3b. Truy vấn trang 1 danh sách suất chiếu khớp điều kiện (ScreeningSearchRepository.searchScreeningsPage)
SELECT
    screening_id, movie_code, movie_title, age_rating, duration_minutes,
    branch_name, room_name, room_type, start_time, end_time,
    base_price, total_seats, sold_seats, available_seats, screening_status
FROM vw_screening_search
WHERE 1 = 1
  AND branch_id = 1
  AND screening_date = '2026-11-04'
  AND TIME(start_time) >= '08:00:00'
  AND TIME(start_time) <= '23:00:00'
  AND screening_status = 'OPEN'
  AND available_seats > 0
ORDER BY start_time ASC, branch_id ASC, room_id ASC, screening_id ASC
LIMIT 50 OFFSET 0;

SELECT '=== [R7.1] MÀN HÌNH 4: BÁO CÁO DOANH THU CHUỖI RẠP (KẾ TOÁN LẬP -> GỬI GIÁM ĐỐC KINH DOANH) ===' AS section_banner;

-- 4a. Bảng I — Tổng hợp doanh thu thực thu theo Chi nhánh kỳ 01/11/2026 - 15/11/2026 (RevenueReportRepository.findBranchSummaries)
SELECT
    br.branch_id,
    br.branch_code,
    br.branch_name,
    br.city,
    COUNT(DISTINCT vr.booking_id)              AS paid_bookings,
    COALESCE(SUM(vr.valid_ticket_count), 0)    AS valid_tickets,
    COALESCE(SUM(vr.recognized_revenue), 0.00) AS paid_revenue
FROM cinema_branch br
LEFT JOIN vw_verified_booking_revenue vr
    ON vr.branch_id = br.branch_id
   AND vr.revenue_date BETWEEN '2026-11-01' AND '2026-11-15'
WHERE br.active = 1
GROUP BY br.branch_id, br.branch_code, br.branch_name, br.city
ORDER BY paid_revenue DESC, br.branch_id ASC;

-- 4b. Bảng đối soát minh bạch cách xử lý các trạng thái thanh toán & đơn vé (SUCCESS vs PENDING vs CANCELLED/REFUNDED vs FAILED)
SELECT
    bk.booking_status,
    pm.payment_status,
    COUNT(DISTINCT bk.booking_id) AS booking_count,
    COUNT(DISTINCT tk.ticket_id)  AS ticket_rows,
    CASE
        WHEN bk.booking_status = 'CONFIRMED' AND pm.payment_status = 'SUCCESS'
            THEN 'ĐƯỢC GHI NHẬN DOANH THU (vé VALID, không nhân bản khi có retry FAILED)'
        WHEN bk.booking_status = 'PENDING' AND pm.payment_status = 'PENDING'
            THEN 'LOẠI TRỪ DOANH THU THỰC THU (chưa thu tiền; vẫn giữ khóa ghế tạm thời)'
        WHEN bk.booking_status = 'CANCELLED' AND pm.payment_status = 'REFUNDED'
            THEN 'LOẠI TRỪ DOANH THU THỰC THU (đã hủy vé & hoàn tiền, giải phóng ghế)'
        ELSE 'LOẠI TRỪ DOANH THU THỰC THU (giao dịch lỗi / đơn hủy chưa thu tiền)'
    END AS accounting_treatment
FROM booking bk
JOIN payment pm ON pm.booking_id = bk.booking_id
LEFT JOIN ticket tk ON tk.booking_id = bk.booking_id
GROUP BY bk.booking_status, pm.payment_status
ORDER BY bk.booking_status, pm.payment_status;

SELECT '=== [R7.2] MÀN HÌNH 5: BÁO CÁO TÌNH HÌNH BÁN VÉ & TỶ LỆ LẤP ĐẦY (QUẢN LÝ CHI NHÁNH -> BAN ĐIỀU HÀNH/QL KINH DOANH) ===' AS section_banner;

-- 5a. Chi tiết bán vé, sức chứa, ghế trống & tỷ lệ lấp đầy theo Suất chiếu tại Chi nhánh #1 ngày 04/11/2026 (TicketSalesReportRepository.findScreeningSales)
SELECT
    screening_id,
    screening_date,
    start_time,
    end_time,
    branch_name,
    room_name,
    room_type,
    movie_title,
    room_capacity,
    tickets_sold,
    available_seats,
    occupancy_rate_pct,
    sales_revenue,
    screening_status
FROM vw_screening_occupancy
WHERE screening_date BETWEEN '2026-11-04' AND '2026-11-04'
  AND branch_id = 1
ORDER BY start_time ASC, screening_id ASC;
