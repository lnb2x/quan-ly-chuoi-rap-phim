-- =============================================================================
-- TỆP: 02_triggers.sql
-- DỰ ÁN: HỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM
-- BƯỚC KHỞI TẠO: [Bước 3 - Tạo Trigger] Thiết lập 12 Trigger bảo vệ toàn vẹn
--                nghiệp vụ liên bảng trực tiếp ở tầng Hệ quản trị MySQL 8.4 LTS:
--   1. Chống trùng lịch chiếu trong cùng phòng & đảm bảo thời lượng tối thiểu.
--   2. Khóa không cho đổi `booking.screening_id` khi đơn đặt vé đã phát sinh vé.
--   3. Đảm bảo ghế trên vé thuộc đúng phòng chiếu của suất chiếu (qua `booking`).
--   4. Tự động đồng bộ bảng khóa ghế `screening_seat_lock` khi thêm/hủy vé và
--      ngăn chặn mọi thao tác can thiệp sai lệch trực tiếp vào `screening_seat_lock`.
--   5. Đảm bảo tính nhất quán của thời điểm hủy vé và thời điểm thanh toán.
-- =============================================================================

SET NAMES utf8mb4;

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

DELIMITER $$

-- -----------------------------------------------------------------------------
-- 1. Trigger kiểm tra khi THÊM MỚI suất chiếu (screening)
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_screening_validate_ins
BEFORE INSERT ON screening
FOR EACH ROW
BEGIN
    DECLARE v_duration INT DEFAULT 0;
    DECLARE v_room_active TINYINT DEFAULT 0;
    DECLARE v_overlap_count INT DEFAULT 0;

    IF NEW.end_time <= NEW.start_time THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-05]: Thoi gian ket thuc phai lon hon thoi gian bat dau.';
    END IF;

    IF NEW.base_price <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-04]: Gia ve co so cua suat chieu phai lon hon 0.';
    END IF;

    SELECT duration_minutes INTO v_duration
    FROM movie
    WHERE movie_id = NEW.movie_id;

    IF TIMESTAMPDIFF(MINUTE, NEW.start_time, NEW.end_time) < v_duration THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-05]: Khoang thoi gian suat chieu nho hon thoi luong phim.';
    END IF;

    SELECT active INTO v_room_active
    FROM screening_room
    WHERE room_id = NEW.room_id;

    IF v_room_active = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-01]: Phong chieu dang tam ngung hoat dong.';
    END IF;

    IF NEW.status = 'OPEN' THEN
        SELECT COUNT(*) INTO v_overlap_count
        FROM screening s
        WHERE s.room_id = NEW.room_id
          AND s.status = 'OPEN'
          AND s.start_time < NEW.end_time
          AND s.end_time > NEW.start_time;

        IF v_overlap_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-06]: Trung lich chieu voi mot suat chieu khac trong cung phong chieu.';
        END IF;
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 2. Trigger kiểm tra khi CẬP NHẬT suất chiếu (screening)
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_screening_validate_upd
BEFORE UPDATE ON screening
FOR EACH ROW
BEGIN
    DECLARE v_duration INT DEFAULT 0;
    DECLARE v_room_active TINYINT DEFAULT 0;
    DECLARE v_overlap_count INT DEFAULT 0;
    DECLARE v_ticket_count INT DEFAULT 0;
    DECLARE v_valid_ticket_count INT DEFAULT 0;

    IF NEW.end_time <= NEW.start_time THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-05]: Thoi gian ket thuc phai lon hon thoi gian bat dau.';
    END IF;

    IF NEW.base_price <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-04]: Gia ve co so cua suat chieu phai lon hon 0.';
    END IF;

    SELECT active INTO v_room_active
    FROM screening_room
    WHERE room_id = NEW.room_id;

    IF v_room_active = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-01]: Phong chieu dang tam ngung hoat dong.';
    END IF;

    IF NEW.room_id <> OLD.room_id THEN
        SELECT COUNT(*) INTO v_ticket_count
        FROM ticket tk
        JOIN booking bk ON bk.booking_id = tk.booking_id
        WHERE bk.screening_id = OLD.screening_id;

        IF v_ticket_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-09]: Khong the doi phong chieu cho suat chieu da phat sinh ve.';
        END IF;
    END IF;

    IF OLD.status = 'OPEN' AND NEW.status = 'CANCELLED' THEN
        SELECT COUNT(*) INTO v_valid_ticket_count
        FROM ticket tk
        JOIN booking bk ON bk.booking_id = tk.booking_id
        WHERE bk.screening_id = OLD.screening_id
          AND tk.ticket_status = 'VALID';

        IF v_valid_ticket_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the huy suat chieu dang co ve hop le chua duoc hoan huy.';
        END IF;
    END IF;

    SELECT duration_minutes INTO v_duration
    FROM movie
    WHERE movie_id = NEW.movie_id;

    IF TIMESTAMPDIFF(MINUTE, NEW.start_time, NEW.end_time) < v_duration THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-05]: Khoang thoi gian suat chieu nho hon thoi luong phim.';
    END IF;

    IF NEW.status = 'OPEN' THEN
        SELECT COUNT(*) INTO v_overlap_count
        FROM screening s
        WHERE s.room_id = NEW.room_id
          AND s.screening_id <> NEW.screening_id
          AND s.status = 'OPEN'
          AND s.start_time < NEW.end_time
          AND s.end_time > NEW.start_time;

        IF v_overlap_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-06]: Trung lich chieu voi mot suat chieu khac trong cung phong chieu.';
        END IF;
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 3. Trigger bảo vệ bất biến đơn đặt vé khi CẬP NHẬT booking
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_booking_validate_upd
BEFORE UPDATE ON booking
FOR EACH ROW
BEGIN
    DECLARE v_ticket_count INT DEFAULT 0;

    IF NEW.screening_id <> OLD.screening_id THEN
        SELECT COUNT(*) INTO v_ticket_count
        FROM ticket
        WHERE booking_id = OLD.booking_id;

        IF v_ticket_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the doi suat chieu cua don dat ve da phat sinh ve.';
        END IF;
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 4. Trigger kiểm tra khi THÊM MỚI vé xem phim (ticket — 3NF)
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_ticket_validate_ins
BEFORE INSERT ON ticket
FOR EACH ROW
BEGIN
    DECLARE v_booking_scr_id INT DEFAULT NULL;
    DECLARE v_scr_room_id INT DEFAULT NULL;
    DECLARE v_scr_status VARCHAR(20) DEFAULT NULL;
    DECLARE v_seat_room_id INT DEFAULT NULL;

    SELECT screening_id INTO v_booking_scr_id
    FROM booking
    WHERE booking_id = NEW.booking_id;

    IF v_booking_scr_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Don dat ve khong ton tai.';
    END IF;

    SELECT room_id, status INTO v_scr_room_id, v_scr_status
    FROM screening
    WHERE screening_id = v_booking_scr_id;

    SELECT room_id INTO v_seat_room_id
    FROM seat
    WHERE seat_id = NEW.seat_id;

    IF v_scr_room_id IS NULL OR v_seat_room_id IS NULL OR v_scr_room_id <> v_seat_room_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-09]: Ghe duoc chon khong thuoc phong chieu cua suat chieu nay.';
    END IF;

    IF NEW.ticket_status = 'VALID' AND v_scr_status <> 'OPEN' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the phat hanh ve hop le cho suat chieu da bi huy.';
    END IF;

    IF NEW.ticket_status = 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
        SET NEW.cancelled_at = CURRENT_TIMESTAMP;
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 5. Trigger kiểm tra khi CẬP NHẬT vé xem phim (ticket — 3NF)
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_ticket_validate_upd
BEFORE UPDATE ON ticket
FOR EACH ROW
BEGIN
    DECLARE v_booking_scr_id INT DEFAULT NULL;
    DECLARE v_scr_room_id INT DEFAULT NULL;
    DECLARE v_scr_status VARCHAR(20) DEFAULT NULL;
    DECLARE v_seat_room_id INT DEFAULT NULL;

    SELECT screening_id INTO v_booking_scr_id
    FROM booking
    WHERE booking_id = NEW.booking_id;

    SELECT room_id, status INTO v_scr_room_id, v_scr_status
    FROM screening
    WHERE screening_id = v_booking_scr_id;

    SELECT room_id INTO v_seat_room_id
    FROM seat
    WHERE seat_id = NEW.seat_id;

    IF v_scr_room_id IS NULL OR v_seat_room_id IS NULL OR v_scr_room_id <> v_seat_room_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-09]: Ghe duoc chon khong thuoc phong chieu cua suat chieu nay.';
    END IF;

    IF NEW.ticket_status = 'VALID' AND v_scr_status <> 'OPEN' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the khoi phuc ve hop le cho suat chieu da bi huy.';
    END IF;

    IF NEW.ticket_status = 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
        SET NEW.cancelled_at = CURRENT_TIMESTAMP;
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 6. Trigger đồng bộ khóa ghế sau khi THÊM MỚI vé hợp lệ (ticket -> screening_seat_lock)
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_ticket_lock_after_ins
AFTER INSERT ON ticket
FOR EACH ROW
BEGIN
    DECLARE v_booking_scr_id INT DEFAULT NULL;

    IF NEW.ticket_status = 'VALID' THEN
        SELECT screening_id INTO v_booking_scr_id
        FROM booking
        WHERE booking_id = NEW.booking_id;

        INSERT INTO screening_seat_lock (screening_id, seat_id, ticket_id)
        VALUES (v_booking_scr_id, NEW.seat_id, NEW.ticket_id);
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 7. Trigger đồng bộ khóa ghế sau khi CẬP NHẬT vé (giải phóng ghế khi CANCELLED)
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_ticket_lock_after_upd
AFTER UPDATE ON ticket
FOR EACH ROW
BEGIN
    DECLARE v_booking_scr_id INT DEFAULT NULL;

    IF OLD.ticket_status = 'VALID' AND NEW.ticket_status = 'CANCELLED' THEN
        DELETE FROM screening_seat_lock
        WHERE ticket_id = OLD.ticket_id;
    ELSEIF OLD.ticket_status = 'CANCELLED' AND NEW.ticket_status = 'VALID' THEN
        SELECT screening_id INTO v_booking_scr_id
        FROM booking
        WHERE booking_id = NEW.booking_id;

        INSERT INTO screening_seat_lock (screening_id, seat_id, ticket_id)
        VALUES (v_booking_scr_id, NEW.seat_id, NEW.ticket_id);
    ELSEIF OLD.ticket_status = 'VALID' AND NEW.ticket_status = 'VALID'
       AND (OLD.seat_id <> NEW.seat_id OR OLD.booking_id <> NEW.booking_id) THEN
        SELECT screening_id INTO v_booking_scr_id
        FROM booking
        WHERE booking_id = NEW.booking_id;

        UPDATE screening_seat_lock
        SET screening_id = v_booking_scr_id,
            seat_id = NEW.seat_id
        WHERE ticket_id = OLD.ticket_id;
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 8. Trigger bảo vệ toàn vẹn khi THÊM MỚI trực tiếp vào screening_seat_lock
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_ssl_validate_ins
BEFORE INSERT ON screening_seat_lock
FOR EACH ROW
BEGIN
    DECLARE v_tk_seat_id INT DEFAULT NULL;
    DECLARE v_tk_status VARCHAR(20) DEFAULT NULL;
    DECLARE v_bk_scr_id INT DEFAULT NULL;

    SELECT tk.seat_id, tk.ticket_status, bk.screening_id
    INTO v_tk_seat_id, v_tk_status, v_bk_scr_id
    FROM ticket tk
    JOIN booking bk ON bk.booking_id = tk.booking_id
    WHERE tk.ticket_id = NEW.ticket_id;

    IF v_tk_status IS NULL OR v_tk_status <> 'VALID' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-10]: Chi duoc phep khoa ghe cho ve dang o trang thai VALID.';
    END IF;

    IF v_tk_seat_id <> NEW.seat_id OR v_bk_scr_id <> NEW.screening_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-10]: Thong tin khoa ghe khong khop voi suat chieu va ghe cua ve.';
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 9. Trigger bảo vệ toàn vẹn khi CẬP NHẬT trực tiếp vào screening_seat_lock
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_ssl_validate_upd
BEFORE UPDATE ON screening_seat_lock
FOR EACH ROW
BEGIN
    DECLARE v_tk_seat_id INT DEFAULT NULL;
    DECLARE v_tk_status VARCHAR(20) DEFAULT NULL;
    DECLARE v_bk_scr_id INT DEFAULT NULL;

    SELECT tk.seat_id, tk.ticket_status, bk.screening_id
    INTO v_tk_seat_id, v_tk_status, v_bk_scr_id
    FROM ticket tk
    JOIN booking bk ON bk.booking_id = tk.booking_id
    WHERE tk.ticket_id = NEW.ticket_id;

    IF v_tk_status IS NULL OR v_tk_status <> 'VALID' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-10]: Chi duoc phep khoa ghe cho ve dang o trang thai VALID.';
    END IF;

    IF v_tk_seat_id <> NEW.seat_id OR v_bk_scr_id <> NEW.screening_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-10]: Thong tin khoa ghe khong khop voi suat chieu va ghe cua ve.';
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 10. Trigger chặn xóa trái phép khóa ghế khi vé vẫn đang VALID
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_ssl_validate_del
BEFORE DELETE ON screening_seat_lock
FOR EACH ROW
BEGIN
    DECLARE v_tk_status VARCHAR(20) DEFAULT NULL;

    SELECT ticket_status INTO v_tk_status
    FROM ticket
    WHERE ticket_id = OLD.ticket_id;

    IF v_tk_status = 'VALID' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-10]: Khong the xoa khoa ghe khi ve van dang o trang thai VALID.';
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 11. Trigger kiểm tra khi THÊM MỚI giao dịch thanh toán (payment)
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_payment_validate_ins
BEFORE INSERT ON payment
FOR EACH ROW
BEGIN
    IF NEW.amount <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: So tien thanh toan phai lon hon 0.';
    END IF;

    IF NEW.payment_status = 'SUCCESS' AND NEW.paid_at IS NULL THEN
        SET NEW.paid_at = CURRENT_TIMESTAMP;
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 12. Trigger kiểm tra khi CẬP NHẬT giao dịch thanh toán (payment)
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_payment_validate_upd
BEFORE UPDATE ON payment
FOR EACH ROW
BEGIN
    IF NEW.amount <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: So tien thanh toan phai lon hon 0.';
    END IF;

    IF NEW.payment_status = 'SUCCESS' AND NEW.paid_at IS NULL THEN
        SET NEW.paid_at = CURRENT_TIMESTAMP;
    END IF;
END$$

DELIMITER ;
