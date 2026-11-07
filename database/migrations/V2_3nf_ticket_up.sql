-- =============================================================================
-- TỆP: V2_3nf_ticket_up.sql
-- MỤC ĐÍCH: Chuyển đổi tại chỗ (In-place Non-Destructive Forward Migration)
--           từ lược đồ 13 bảng sang lược đồ 14 bảng đạt chuẩn 3NF thực sự:
--           - Bảo toàn 100% lịch sử đơn đặt vé, vé hợp lệ, vé hủy, giá lịch sử và thanh toán.
--           - Tách khóa ghế hiệu lực sang `screening_seat_lock(screening_id, seat_id, ticket_id)`.
--           - Loại bỏ thuộc tính bắc cầu `ticket.screening_id` và `active_seat_key`.
--           - Cập nhật đồng bộ Khóa ngoại, tiếp nối bởi 02_triggers.sql & 03_views.sql.
-- =============================================================================

SET NAMES utf8mb4;

-- Bước 1: Xóa các Trigger cũ trên bảng ticket để quá trình chuyển đổi cấu trúc an toàn
DROP TRIGGER IF EXISTS trg_screening_validate_ins;
DROP TRIGGER IF EXISTS trg_screening_validate_upd;
DROP TRIGGER IF EXISTS trg_booking_validate_upd;
DROP TRIGGER IF EXISTS trg_ticket_validate_ins;
DROP TRIGGER IF EXISTS trg_ticket_validate_upd;
DROP TRIGGER IF EXISTS trg_ticket_lock_after_ins;
DROP TRIGGER IF EXISTS trg_ticket_lock_after_upd;
DROP TRIGGER IF EXISTS trg_ssl_validate_ins;
DROP TRIGGER IF EXISTS trg_ssl_validate_upd;
DROP TRIGGER IF EXISTS trg_ssl_validate_del;
DROP TRIGGER IF EXISTS trg_payment_validate_ins;
DROP TRIGGER IF EXISTS trg_payment_validate_upd;

-- Bước 2: Tạo bảng khóa phân bổ ghế đang hiệu lực `screening_seat_lock` (3NF / BCNF)
CREATE TABLE IF NOT EXISTS screening_seat_lock (
    screening_id INT NOT NULL,
    seat_id      INT NOT NULL,
    ticket_id    INT NOT NULL,
    CONSTRAINT pk_screening_seat_lock PRIMARY KEY (screening_id, seat_id),
    CONSTRAINT uq_ssl_ticket          UNIQUE (ticket_id),
    INDEX idx_ssl_seat (seat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Bước 3: Chuyển đổi toàn bộ các vé đang ở trạng thái VALID sang `screening_seat_lock`
INSERT INTO screening_seat_lock (screening_id, seat_id, ticket_id)
SELECT bk.screening_id, tk.seat_id, tk.ticket_id
FROM ticket tk
JOIN booking bk ON bk.booking_id = tk.booking_id
WHERE tk.ticket_status = 'VALID';

-- Bước 4: Thiết lập Khóa ngoại cho `screening_seat_lock`
ALTER TABLE screening_seat_lock
    ADD CONSTRAINT fk_ssl_screening FOREIGN KEY (screening_id)
        REFERENCES screening (screening_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT fk_ssl_seat FOREIGN KEY (seat_id)
        REFERENCES seat (seat_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT fk_ssl_ticket FOREIGN KEY (ticket_id)
        REFERENCES ticket (ticket_id)
        ON DELETE CASCADE ON UPDATE CASCADE;

-- Bước 5: Tháo các ràng buộc tham chiếu cũ phụ thuộc vào `ticket.screening_id` và `active_seat_key`
ALTER TABLE ticket
    DROP FOREIGN KEY fk_ticket_booking_scr,
    DROP FOREIGN KEY fk_ticket_screening,
    DROP FOREIGN KEY fk_ticket_booking,
    DROP FOREIGN KEY fk_ticket_seat;

ALTER TABLE ticket
    DROP INDEX uq_ticket_active_seat,
    DROP INDEX idx_ticket_screening_status,
    DROP INDEX idx_ticket_booking_scr,
    DROP COLUMN active_seat_key,
    DROP COLUMN screening_id,
    ADD CONSTRAINT uq_ticket_booking_seat UNIQUE (booking_id, seat_id);

ALTER TABLE ticket
    ADD CONSTRAINT fk_ticket_booking FOREIGN KEY (booking_id)
        REFERENCES booking (booking_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT fk_ticket_seat FOREIGN KEY (seat_id)
        REFERENCES seat (seat_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- Bước 6: Loại bỏ siêu khóa không tối tiểu `uq_booking_id_screening` trên bảng `booking`
ALTER TABLE booking
    DROP INDEX uq_booking_id_screening;
