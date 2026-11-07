-- =============================================================================
-- TỆP: verify_constraints.sql
-- MỤC ĐÍCH: Kiểm chứng toàn vẹn dữ liệu CSDL trên lược đồ 14 bảng chuẩn 3NF
-- =============================================================================

SET NAMES utf8mb4;

-- 1. Thống kê quy mô dữ liệu khởi tạo trên cả 14 bảng (Seed Data Scale Check)
SELECT '01_cinema_branch'       AS table_name, COUNT(*) AS row_count FROM cinema_branch
UNION ALL
SELECT '02_screening_room'      AS table_name, COUNT(*) AS row_count FROM screening_room
UNION ALL
SELECT '03_seat_type'           AS table_name, COUNT(*) AS row_count FROM seat_type
UNION ALL
SELECT '04_seat'                AS table_name, COUNT(*) AS row_count FROM seat
UNION ALL
SELECT '05_movie'               AS table_name, COUNT(*) AS row_count FROM movie
UNION ALL
SELECT '06_genre'               AS table_name, COUNT(*) AS row_count FROM genre
UNION ALL
SELECT '07_movie_genre'         AS table_name, COUNT(*) AS row_count FROM movie_genre
UNION ALL
SELECT '08_screening'           AS table_name, COUNT(*) AS row_count FROM screening
UNION ALL
SELECT '09_customer'            AS table_name, COUNT(*) AS row_count FROM customer
UNION ALL
SELECT '10_employee'            AS table_name, COUNT(*) AS row_count FROM employee
UNION ALL
SELECT '11_booking'             AS table_name, COUNT(*) AS row_count FROM booking
UNION ALL
SELECT '12_ticket'              AS table_name, COUNT(*) AS row_count FROM ticket
UNION ALL
SELECT '13_screening_seat_lock' AS table_name, COUNT(*) AS row_count FROM screening_seat_lock
UNION ALL
SELECT '14_payment'             AS table_name, COUNT(*) AS row_count FROM payment;

-- 2. Kiểm tra cấu trúc bảng ticket chuẩn 3NF: không còn cột dư thừa screening_id hay active_seat_key (Kỳ vọng: 0)
SELECT COUNT(*) AS forbidden_non_3nf_ticket_columns
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ticket'
  AND COLUMN_NAME IN ('screening_id', 'active_seat_key');

-- 3. Kiểm tra vi phạm chồng lấn lịch chiếu trong cùng phòng (Kỳ vọng: 0)
SELECT COUNT(*) AS overlapping_open_screenings
FROM screening s1
JOIN screening s2
  ON s1.room_id = s2.room_id
 AND s1.screening_id < s2.screening_id
 AND s1.status = 'OPEN'
 AND s2.status = 'OPEN'
 AND s1.start_time < s2.end_time
 AND s1.end_time > s2.start_time;

-- 4. Kiểm tra ghế trên vé không thuộc phòng chiếu của suất chiếu (Kỳ vọng: 0)
SELECT COUNT(*) AS wrong_room_tickets
FROM ticket tk
JOIN booking bk   ON bk.booking_id = tk.booking_id
JOIN screening sc ON sc.screening_id = bk.screening_id
JOIN seat st      ON st.seat_id = tk.seat_id
WHERE sc.room_id <> st.room_id;

-- 5. Kiểm tra vé VALID bị bán trùng cùng suất chiếu và cùng ghế qua kết nối bảo toàn ticket ⋈ booking (Kỳ vọng: 0)
SELECT COUNT(*) AS duplicate_valid_seats
FROM (
    SELECT bk.screening_id, tk.seat_id, COUNT(*) AS cnt
    FROM ticket tk
    JOIN booking bk ON bk.booking_id = tk.booking_id
    WHERE tk.ticket_status = 'VALID'
    GROUP BY bk.screening_id, tk.seat_id
    HAVING COUNT(*) > 1
) dup;

-- 6. Kiểm tra đồng bộ 1-1 giữa vé VALID và bảng khóa ghế screening_seat_lock (Kỳ vọng: 0 sai lệch)
SELECT
    (SELECT COUNT(*) FROM ticket WHERE ticket_status = 'VALID') AS valid_tickets,
    (SELECT COUNT(*) FROM screening_seat_lock) AS active_seat_locks,
    (
        SELECT COUNT(*)
        FROM ticket tk
        JOIN booking bk ON bk.booking_id = tk.booking_id
        LEFT JOIN screening_seat_lock sl
          ON sl.ticket_id = tk.ticket_id
         AND sl.screening_id = bk.screening_id
         AND sl.seat_id = tk.seat_id
        WHERE tk.ticket_status = 'VALID' AND sl.ticket_id IS NULL
    ) + (
        SELECT COUNT(*)
        FROM screening_seat_lock sl
        LEFT JOIN ticket tk
          ON tk.ticket_id = sl.ticket_id AND tk.ticket_status = 'VALID'
        WHERE tk.ticket_id IS NULL
    ) AS lock_sync_mismatch_count;

-- 7. Kiểm tra đơn đặt vé có nhiều hơn 1 thanh toán SUCCESS (Kỳ vọng: 0)
SELECT COUNT(*) AS multi_success_payment_bookings
FROM (
    SELECT booking_id, COUNT(*) AS cnt
    FROM payment
    WHERE payment_status = 'SUCCESS'
    GROUP BY booking_id
    HAVING COUNT(*) > 1
) mp;

-- 8. Kiểm chứng nghiệp vụ Hủy vé & Bán lại ghế (Seat Resale after Cancellation):
--    Đếm số cặp (screening_id, seat_id) từng có vé CANCELLED và sau đó bán lại thành vé VALID
SELECT COUNT(*) AS resold_seats_after_cancellation
FROM ticket t_cancel
JOIN booking b_cancel ON b_cancel.booking_id = t_cancel.booking_id
JOIN ticket t_resold  ON t_resold.seat_id = t_cancel.seat_id
JOIN booking b_resold ON b_resold.booking_id = t_resold.booking_id
                     AND b_resold.screening_id = b_cancel.screening_id
WHERE t_cancel.ticket_status = 'CANCELLED'
  AND t_resold.ticket_status = 'VALID'
  AND t_cancel.ticket_id < t_resold.ticket_id;

-- 9. Kiểm chứng các đơn có thử lại thanh toán (1 lần FAILED + 1 lần SUCCESS)
SELECT COUNT(*) AS bookings_with_retry_payment
FROM (
    SELECT booking_id
    FROM payment
    GROUP BY booking_id
    HAVING COUNT(*) > 1
       AND SUM(CASE WHEN payment_status = 'FAILED' THEN 1 ELSE 0 END) >= 1
       AND SUM(CASE WHEN payment_status = 'SUCCESS' THEN 1 ELSE 0 END) = 1
) retry_bk;

-- 10. Đối soát tổng doanh thu độc lập (Kỳ vọng: revenue_difference = 0.00)
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
