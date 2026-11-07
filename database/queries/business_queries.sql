-- =============================================================================
-- TỆP: business_queries.sql
-- MỤC ĐÍCH: Bộ câu lệnh SQL mẫu chạy được trực tiếp trên `cinema_chain_test`
--           tương ứng 100% với 5 chức năng nghiệp vụ R5, R6, R7 (Phụ lục R8).
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- Q1. Kiểm tra xung đột lịch chiếu trong cùng một phòng chiếu (Form 1 - R5)
-- -----------------------------------------------------------------------------
SELECT
    s.screening_id,
    m.title AS movie_title,
    s.start_time,
    s.end_time,
    s.status
FROM screening s
JOIN movie m ON m.movie_id = s.movie_id
WHERE s.room_id = 1
  AND s.status = 'OPEN'
  AND s.start_time < '2026-11-04 11:30:00'
  AND s.end_time > '2026-11-04 09:00:00';

-- -----------------------------------------------------------------------------
-- Q2. Truy vấn sơ đồ ghế và trạng thái ghế theo suất chiếu qua bảng khóa 3NF
--     `screening_seat_lock` (Form 2 - R5)
-- -----------------------------------------------------------------------------
SELECT
    st.seat_id,
    st.seat_row,
    st.seat_number,
    CONCAT(st.seat_row, LPAD(st.seat_number, 2, '0')) AS seat_label,
    st.seat_type_code,
    tp.type_name,
    tp.surcharge,
    (sc.base_price + tp.surcharge) AS final_ticket_price,
    CASE
        WHEN sl.ticket_id IS NOT NULL THEN 'OCCUPIED'
        ELSE 'AVAILABLE'
    END AS seat_availability
FROM screening sc
JOIN seat st                   ON st.room_id = sc.room_id
JOIN seat_type tp              ON tp.seat_type_code = st.seat_type_code
LEFT JOIN screening_seat_lock sl
       ON sl.screening_id = sc.screening_id
      AND sl.seat_id = st.seat_id
WHERE sc.screening_id = 1
ORDER BY st.seat_row, st.seat_number
LIMIT 20;

-- -----------------------------------------------------------------------------
-- Q3. Đếm tổng số kết quả và phân trang tra cứu suất chiếu (Màn hình 3 - R6)
-- -----------------------------------------------------------------------------
SELECT COUNT(*) AS total_matching_screenings
FROM vw_screening_search v
WHERE v.screening_status = 'OPEN'
  AND v.screening_date = '2026-11-04';

SELECT
    v.screening_id,
    v.movie_code,
    v.movie_title,
    v.duration_minutes,
    v.age_rating,
    v.branch_name,
    v.room_name,
    v.room_type,
    v.start_time,
    v.end_time,
    v.base_price,
    v.total_seats,
    v.sold_seats,
    v.available_seats,
    v.screening_status
FROM vw_screening_search v
WHERE v.screening_status = 'OPEN'
  AND v.screening_date = '2026-11-04'
ORDER BY v.start_time ASC, v.branch_name ASC, v.room_name ASC
LIMIT 15 OFFSET 0;

-- -----------------------------------------------------------------------------
-- Q4. Báo cáo Doanh thu theo Chi nhánh và Khoảng thời gian thanh toán (Báo cáo 1 - R7)
--     Người lập: Kế toán | Người nhận: Giám đốc Kinh doanh
--     Chỉ ghi nhận đơn CONFIRMED có thanh toán SUCCESS và vé VALID;
--     loại trừ hoàn toàn đơn PENDING, CANCELLED, thanh toán FAILED và REFUNDED.
-- -----------------------------------------------------------------------------
SELECT
    br.branch_id,
    br.branch_code,
    br.branch_name,
    br.city,
    COUNT(DISTINCT vr.booking_id)               AS total_paid_bookings,
    COALESCE(SUM(vr.valid_ticket_count), 0)     AS total_valid_tickets,
    COALESCE(SUM(vr.recognized_revenue), 0.00)  AS total_paid_revenue
FROM cinema_branch br
LEFT JOIN vw_verified_booking_revenue vr
    ON vr.branch_id = br.branch_id
   AND vr.revenue_date BETWEEN '2026-11-01' AND '2026-11-15'
GROUP BY br.branch_id, br.branch_code, br.branch_name, br.city
ORDER BY total_paid_revenue DESC, br.branch_id ASC;

-- -----------------------------------------------------------------------------
-- Q5. Chi tiết doanh thu theo ngày thanh toán và chi nhánh (Báo cáo 1 - R7)
-- -----------------------------------------------------------------------------
SELECT
    revenue_date,
    branch_code,
    branch_name,
    paid_booking_count,
    paid_ticket_count,
    total_paid_revenue
FROM vw_branch_daily_revenue
WHERE revenue_date BETWEEN '2026-11-01' AND '2026-11-15'
ORDER BY revenue_date ASC, branch_code ASC;

-- -----------------------------------------------------------------------------
-- Q6. Báo cáo Tình hình Bán vé & Tỷ lệ Lấp đầy theo Suất chiếu (Báo cáo 2 - R7)
--     Người lập: Quản lý Chi nhánh | Người nhận: Ban Điều hành / Quản lý Kinh doanh
-- -----------------------------------------------------------------------------
SELECT
    screening_id,
    screening_date,
    TIME(start_time) AS start_clock,
    TIME(end_time)   AS end_clock,
    branch_name,
    room_name,
    room_type,
    movie_title,
    room_capacity,
    tickets_sold,
    available_seats,
    occupancy_rate_pct,
    sales_revenue
FROM vw_screening_occupancy
WHERE screening_date BETWEEN '2026-11-04' AND '2026-11-10'
ORDER BY screening_date ASC, start_time ASC, branch_id ASC
LIMIT 20;

-- -----------------------------------------------------------------------------
-- Q7. Đối soát toàn vẹn Doanh thu Độc lập (Kiểm chứng chống nhân bản doanh thu):
--     Chứng minh tổng doanh thu từ bảng ticket (thuộc đơn CONFIRMED có payment SUCCESS)
--     khớp tuyệt đối với tổng doanh thu trên View báo cáo.
-- -----------------------------------------------------------------------------
SELECT
    (SELECT SUM(tk.unit_price)
     FROM ticket tk
     JOIN booking bk ON bk.booking_id = tk.booking_id
     WHERE tk.ticket_status = 'VALID'
       AND bk.booking_status = 'CONFIRMED'
       AND EXISTS (
           SELECT 1 FROM payment pm
           WHERE pm.booking_id = bk.booking_id
             AND pm.payment_status = 'SUCCESS'
       )
    ) AS independent_ticket_revenue_sum,
    (SELECT SUM(total_paid_revenue)
     FROM vw_branch_daily_revenue
    ) AS view_report_revenue_sum;
