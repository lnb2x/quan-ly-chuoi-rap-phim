-- =============================================================================
-- TỆP: V2_3nf_ticket_precheck.sql
-- MỤC ĐÍCH: Kiểm tra điều kiện tiên quyết trên dữ liệu hiện có trước khi chuyển đổi
--           không phá hủy từ lược đồ 13 bảng sang lược đồ 14 bảng chuẩn 3NF.
-- YÊU CẦU: Tất cả các chỉ số lỗi (mismatch / duplicate / wrong_room) phải bằng 0.
-- =============================================================================

SET NAMES utf8mb4;

SELECT
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE') AS current_table_count,
    (SELECT COUNT(*) FROM booking) AS booking_count,
    (SELECT COUNT(*) FROM ticket) AS ticket_count,
    (SELECT COUNT(*) FROM ticket WHERE ticket_status = 'VALID') AS valid_ticket_count,
    (SELECT COUNT(*) FROM payment) AS payment_count,
    (SELECT COALESCE(SUM(total_paid_revenue), 0.00) FROM vw_branch_daily_revenue) AS current_recognized_revenue;

-- 1. Kiểm tra không có dòng ticket nào lệch screening_id so với booking (Kỳ vọng: 0)
SELECT COUNT(*) AS ticket_booking_screening_mismatch
FROM ticket tk
JOIN booking bk ON bk.booking_id = tk.booking_id
WHERE tk.screening_id <> bk.screening_id;

-- 2. Kiểm tra không có cặp (booking_id, seat_id) trùng lặp trong ticket (Kỳ vọng: 0)
SELECT COUNT(*) AS duplicate_booking_seat_pairs
FROM (
    SELECT booking_id, seat_id, COUNT(*) AS c
    FROM ticket
    GROUP BY booking_id, seat_id
    HAVING COUNT(*) > 1
) d1;

-- 3. Kiểm tra không có cặp (screening_id, seat_id) trùng lặp ở trạng thái VALID (Kỳ vọng: 0)
SELECT COUNT(*) AS duplicate_valid_screening_seats
FROM (
    SELECT bk.screening_id, tk.seat_id, COUNT(*) AS c
    FROM ticket tk
    JOIN booking bk ON bk.booking_id = tk.booking_id
    WHERE tk.ticket_status = 'VALID'
    GROUP BY bk.screening_id, tk.seat_id
    HAVING COUNT(*) > 1
) d2;

-- 4. Kiểm tra không có vé nào gắn ghế sai phòng chiếu (Kỳ vọng: 0)
SELECT COUNT(*) AS wrong_room_ticket_count
FROM ticket tk
JOIN booking bk   ON bk.booking_id = tk.booking_id
JOIN screening sc ON sc.screening_id = bk.screening_id
JOIN seat st      ON st.seat_id = tk.seat_id
WHERE sc.room_id <> st.room_id;
