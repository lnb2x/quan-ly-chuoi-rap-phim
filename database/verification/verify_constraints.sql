-- =============================================================================
-- TỆP: verify_constraints.sql
-- MỤC ĐÍCH: Kiểm chứng toàn vẹn dữ liệu CSDL (Gate C & Gate E) trên dữ liệu thật
-- =============================================================================

SET NAMES utf8mb4;

-- 1. Thống kê quy mô dữ liệu khởi tạo (Seed Data Scale Check)
SELECT '01_cinema_branch'  AS table_name, COUNT(*) AS row_count FROM cinema_branch
UNION ALL
SELECT '02_screening_room' AS table_name, COUNT(*) AS row_count FROM screening_room
UNION ALL
SELECT '03_seat_type'      AS table_name, COUNT(*) AS row_count FROM seat_type
UNION ALL
SELECT '04_seat'           AS table_name, COUNT(*) AS row_count FROM seat
UNION ALL
SELECT '05_movie'          AS table_name, COUNT(*) AS row_count FROM movie
UNION ALL
SELECT '06_genre'          AS table_name, COUNT(*) AS row_count FROM genre
UNION ALL
SELECT '07_movie_genre'    AS table_name, COUNT(*) AS row_count FROM movie_genre
UNION ALL
SELECT '08_screening'      AS table_name, COUNT(*) AS row_count FROM screening
UNION ALL
SELECT '09_customer'       AS table_name, COUNT(*) AS row_count FROM customer
UNION ALL
SELECT '10_employee'       AS table_name, COUNT(*) AS row_count FROM employee
UNION ALL
SELECT '11_booking'        AS table_name, COUNT(*) AS row_count FROM booking
UNION ALL
SELECT '12_ticket'         AS table_name, COUNT(*) AS row_count FROM ticket
UNION ALL
SELECT '13_payment'        AS table_name, COUNT(*) AS row_count FROM payment;

-- 2. Kiểm tra vi phạm chồng lấn lịch chiếu trong cùng phòng (Kỳ vọng: 0)
SELECT COUNT(*) AS overlapping_open_screenings
FROM screening s1
JOIN screening s2
  ON s1.room_id = s2.room_id
 AND s1.screening_id < s2.screening_id
 AND s1.status = 'OPEN'
 AND s2.status = 'OPEN'
 AND s1.start_time < s2.end_time
 AND s1.end_time > s2.start_time;

-- 3. Kiểm tra ghế trên vé không thuộc phòng chiếu của suất chiếu (Kỳ vọng: 0)
SELECT COUNT(*) AS wrong_room_tickets
FROM ticket tk
JOIN screening sc ON sc.screening_id = tk.screening_id
JOIN seat st      ON st.seat_id = tk.seat_id
WHERE sc.room_id <> st.room_id;

-- 4. Kiểm tra vé VALID bị bán trùng cùng suất chiếu và cùng ghế (Kỳ vọng: 0)
SELECT COUNT(*) AS duplicate_valid_seats
FROM (
    SELECT screening_id, seat_id, COUNT(*) AS cnt
    FROM ticket
    WHERE ticket_status = 'VALID'
    GROUP BY screening_id, seat_id
    HAVING COUNT(*) > 1
) dup;

-- 5. Kiểm tra đơn đặt vé có nhiều hơn 1 thanh toán SUCCESS (Kỳ vọng: 0)
SELECT COUNT(*) AS multi_success_payment_bookings
FROM (
    SELECT booking_id, COUNT(*) AS cnt
    FROM payment
    WHERE payment_status = 'SUCCESS'
    GROUP BY booking_id
    HAVING COUNT(*) > 1
) mp;

-- 6. Kiểm chứng nghiệp vụ Hủy vé & Bán lại ghế (Seat Resale after Cancellation):
--    Đếm số cặp ghế đã từng có vé CANCELLED và sau đó được bán lại thành vé VALID
SELECT COUNT(*) AS resold_seats_after_cancellation
FROM ticket t_cancel
JOIN ticket t_resold
  ON t_cancel.screening_id = t_resold.screening_id
 AND t_cancel.seat_id = t_resold.seat_id
 AND t_cancel.ticket_status = 'CANCELLED'
 AND t_resold.ticket_status = 'VALID'
 AND t_cancel.ticket_id < t_resold.ticket_id;

-- 7. Kiểm chứng các đơn có thử lại thanh toán (1 lần FAILED + 1 lần SUCCESS)
SELECT COUNT(*) AS bookings_with_retry_payment
FROM (
    SELECT booking_id
    FROM payment
    GROUP BY booking_id
    HAVING COUNT(*) > 1
       AND SUM(CASE WHEN payment_status = 'FAILED' THEN 1 ELSE 0 END) >= 1
       AND SUM(CASE WHEN payment_status = 'SUCCESS' THEN 1 ELSE 0 END) = 1
) retry_bk;

-- 8. Đối soát tổng doanh thu độc lập (Kỳ vọng: difference = 0.00)
SELECT
    ind.independent_revenue,
    vw.view_revenue,
    (ind.independent_revenue - vw.view_revenue) AS revenue_difference
FROM (
    SELECT COALESCE(SUM(tk.unit_price), 0.00) AS independent_revenue
    FROM ticket tk
    JOIN booking bk ON bk.booking_id = tk.booking_id
    WHERE tk.ticket_status = 'VALID'
      AND bk.booking_status = 'CONFIRMED'
      AND EXISTS (
          SELECT 1 FROM payment pm
          WHERE pm.booking_id = bk.booking_id
            AND pm.payment_status = 'SUCCESS'
      )
) ind
CROSS JOIN (
    SELECT COALESCE(SUM(total_paid_revenue), 0.00) AS view_revenue
    FROM vw_branch_daily_revenue
) vw;
