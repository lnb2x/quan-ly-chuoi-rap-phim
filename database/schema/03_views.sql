-- =============================================================================
-- TỆP: 03_views.sql
-- DỰ ÁN: HỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM
-- BƯỚC KHỞI TẠO: [Bước 4 - Tạo View] Định nghĩa 04 khung nhìn (Views) trên
--                lược đồ 14 bảng chuẩn 3NF phục vụ Tra cứu Suất chiếu,
--                Báo cáo Doanh thu và Báo cáo Tình hình Bán vé.
-- TƯƠNG THÍCH MYSQL 8.4 LTS:
--   - Sử dụng `CREATE OR REPLACE VIEW` chuẩn của MySQL 8.4, tuân thủ nghiêm ngặt
--     chế độ `ONLY_FULL_GROUP_BY`.
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. Khung nhìn vw_screening_search (Tra cứu suất chiếu & ghế trống trên lược đồ 3NF)
--    Ghế đang bị giữ được đếm trực tiếp từ `screening_seat_lock` (mọi vé VALID
--    của cả đơn CONFIRMED lẫn PENDING đều đang giữ chỗ vật lý trên sơ đồ ghế).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_screening_search AS
SELECT
    s.screening_id,
    s.movie_id,
    m.movie_code,
    m.title AS movie_title,
    m.duration_minutes,
    m.age_rating,
    s.room_id,
    r.room_name,
    r.room_type,
    r.branch_id,
    b.branch_code,
    b.branch_name,
    b.city,
    s.start_time,
    s.end_time,
    DATE(s.start_time) AS screening_date,
    s.base_price,
    s.status AS screening_status,
    (SELECT COUNT(*) FROM seat st WHERE st.room_id = s.room_id) AS total_seats,
    (SELECT COUNT(*) FROM screening_seat_lock sl WHERE sl.screening_id = s.screening_id) AS sold_seats,
    (
        (SELECT COUNT(*) FROM seat st WHERE st.room_id = s.room_id) -
        (SELECT COUNT(*) FROM screening_seat_lock sl WHERE sl.screening_id = s.screening_id)
    ) AS available_seats
FROM screening s
JOIN movie m          ON m.movie_id = s.movie_id
JOIN screening_room r ON r.room_id = s.room_id
JOIN cinema_branch b  ON b.branch_id = r.branch_id;

-- -----------------------------------------------------------------------------
-- 2. Khung nhìn vw_verified_booking_revenue (Đơn đặt vé đủ điều kiện doanh thu)
--    Quy tắc BR-14 & BR-15:
--    - Chỉ tính đơn có booking_status = 'CONFIRMED'
--    - Có đúng 1 giao dịch payment_status = 'SUCCESS'
--    - Chỉ cộng các vé có ticket_status = 'VALID'
--    - Đơn PENDING, CANCELLED hoặc thanh toán FAILED / REFUNDED bị loại trừ 100%
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_verified_booking_revenue AS
SELECT
    bk.booking_id,
    bk.booking_code,
    bk.screening_id,
    sc.movie_id,
    mv.title AS movie_title,
    sc.room_id,
    rm.room_name,
    rm.branch_id,
    br.branch_code,
    br.branch_name,
    bk.customer_id,
    COALESCE(cu.full_name, 'Khách vãng lai') AS customer_name,
    bk.employee_id,
    em.full_name AS employee_name,
    bk.booked_at,
    pm.payment_id,
    pm.payment_method,
    pm.paid_at,
    DATE(pm.paid_at) AS revenue_date,
    COUNT(tk.ticket_id) AS valid_ticket_count,
    COALESCE(SUM(tk.unit_price), 0.00) AS recognized_revenue
FROM booking bk
JOIN screening sc      ON sc.screening_id = bk.screening_id
JOIN movie mv          ON mv.movie_id = sc.movie_id
JOIN screening_room rm ON rm.room_id = sc.room_id
JOIN cinema_branch br  ON br.branch_id = rm.branch_id
LEFT JOIN customer cu  ON cu.customer_id = bk.customer_id
JOIN employee em       ON em.employee_id = bk.employee_id
JOIN payment pm        ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
JOIN ticket tk         ON tk.booking_id = bk.booking_id AND tk.ticket_status = 'VALID'
WHERE bk.booking_status = 'CONFIRMED'
GROUP BY
    bk.booking_id,
    bk.booking_code,
    bk.screening_id,
    sc.movie_id,
    mv.title,
    sc.room_id,
    rm.room_name,
    rm.branch_id,
    br.branch_code,
    br.branch_name,
    bk.customer_id,
    cu.full_name,
    bk.employee_id,
    em.full_name,
    bk.booked_at,
    pm.payment_id,
    pm.payment_method,
    pm.paid_at;

-- -----------------------------------------------------------------------------
-- 3. Khung nhìn vw_branch_daily_revenue (Doanh thu theo ngày thanh toán & chi nhánh)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_branch_daily_revenue AS
SELECT
    revenue_date,
    branch_id,
    branch_code,
    branch_name,
    COUNT(DISTINCT booking_id) AS paid_booking_count,
    SUM(valid_ticket_count)    AS paid_ticket_count,
    SUM(recognized_revenue)    AS total_paid_revenue
FROM vw_verified_booking_revenue
GROUP BY
    revenue_date,
    branch_id,
    branch_code,
    branch_name;

-- -----------------------------------------------------------------------------
-- 4. Khung nhìn vw_screening_occupancy (Báo cáo bán vé & tỷ lệ lấp đầy suất chiếu)
--    - `tickets_sold`, `occupancy_rate_pct`, `sales_revenue`: chỉ tính vé VALID
--      thuộc đơn CONFIRMED đã có thanh toán SUCCESS.
--    - `available_seats`: số ghế trống thực tế trên phòng (trừ mọi ghế đang giữ chỗ
--      trong `screening_seat_lock`, bao gồm cả đơn PENDING đang chờ thanh toán).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_screening_occupancy AS
SELECT
    s.screening_id,
    DATE(s.start_time) AS screening_date,
    s.start_time,
    s.end_time,
    s.status AS screening_status,
    s.base_price,
    m.movie_id,
    m.movie_code,
    m.title AS movie_title,
    r.room_id,
    r.room_name,
    r.room_type,
    b.branch_id,
    b.branch_code,
    b.branch_name,
    (SELECT COUNT(*) FROM seat st WHERE st.room_id = s.room_id) AS room_capacity,
    (
        SELECT COUNT(*)
        FROM ticket tk
        JOIN booking bk ON bk.booking_id = tk.booking_id
        JOIN payment pm ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
        WHERE bk.screening_id = s.screening_id
          AND tk.ticket_status = 'VALID'
          AND bk.booking_status = 'CONFIRMED'
    ) AS tickets_sold,
    (
        (SELECT COUNT(*) FROM seat st WHERE st.room_id = s.room_id) -
        (SELECT COUNT(*) FROM screening_seat_lock sl WHERE sl.screening_id = s.screening_id)
    ) AS available_seats,
    ROUND(
        100.0 * (
            SELECT COUNT(*)
            FROM ticket tk
            JOIN booking bk ON bk.booking_id = tk.booking_id
            JOIN payment pm ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
            WHERE bk.screening_id = s.screening_id
              AND tk.ticket_status = 'VALID'
              AND bk.booking_status = 'CONFIRMED'
        ) / NULLIF((SELECT COUNT(*) FROM seat st WHERE st.room_id = s.room_id), 0),
        2
    ) AS occupancy_rate_pct,
    COALESCE((
        SELECT SUM(tk.unit_price)
        FROM ticket tk
        JOIN booking bk ON bk.booking_id = tk.booking_id
        JOIN payment pm ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
        WHERE bk.screening_id = s.screening_id
          AND tk.ticket_status = 'VALID'
          AND bk.booking_status = 'CONFIRMED'
    ), 0.00) AS sales_revenue
FROM screening s
JOIN movie m          ON m.movie_id = s.movie_id
JOIN screening_room r ON r.room_id = s.room_id
JOIN cinema_branch b  ON b.branch_id = r.branch_id;
