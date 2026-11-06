-- =============================================================================
-- TỆP: 02_triggers.sql
-- MỤC ĐÍCH: Các Trigger bảo vệ quy tắc nghiệp vụ ở tầng Hệ quản trị CSDL:
--   1. Chống trùng lịch chiếu trong cùng phòng & đảm bảo thời lượng tối thiểu.
--   2. Đảm bảo ghế trên vé thuộc đúng phòng chiếu của suất chiếu & đúng đơn.
--   3. Đảm bảo tính nhất quán của thời điểm hủy vé và thời điểm thanh toán.
-- =============================================================================

DROP TRIGGER IF EXISTS trg_screening_validate_ins;
DROP TRIGGER IF EXISTS trg_screening_validate_upd;
DROP TRIGGER IF EXISTS trg_ticket_validate_ins;
DROP TRIGGER IF EXISTS trg_ticket_validate_upd;
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
-- 3. Trigger kiểm tra khi THÊM MỚI vé xem phim (ticket)
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_ticket_validate_ins
BEFORE INSERT ON ticket
FOR EACH ROW
BEGIN
    DECLARE v_scr_room_id INT DEFAULT NULL;
    DECLARE v_scr_status VARCHAR(20) DEFAULT NULL;
    DECLARE v_seat_room_id INT DEFAULT NULL;
    DECLARE v_booking_scr_id INT DEFAULT NULL;

    SELECT room_id, status INTO v_scr_room_id, v_scr_status
    FROM screening
    WHERE screening_id = NEW.screening_id;

    SELECT room_id INTO v_seat_room_id
    FROM seat
    WHERE seat_id = NEW.seat_id;

    SELECT screening_id INTO v_booking_scr_id
    FROM booking
    WHERE booking_id = NEW.booking_id;

    IF v_booking_scr_id IS NULL OR v_booking_scr_id <> NEW.screening_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Suat chieu tren ve khong khop voi suat chieu cua don dat ve.';
    END IF;

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
-- 4. Trigger kiểm tra khi CẬP NHẬT vé xem phim (ticket)
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_ticket_validate_upd
BEFORE UPDATE ON ticket
FOR EACH ROW
BEGIN
    DECLARE v_scr_room_id INT DEFAULT NULL;
    DECLARE v_seat_room_id INT DEFAULT NULL;

    SELECT room_id INTO v_scr_room_id
    FROM screening
    WHERE screening_id = NEW.screening_id;

    SELECT room_id INTO v_seat_room_id
    FROM seat
    WHERE seat_id = NEW.seat_id;

    IF v_scr_room_id <> v_seat_room_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-09]: Ghe duoc chon khong thuoc phong chieu cua suat chieu nay.';
    END IF;

    IF NEW.ticket_status = 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
        SET NEW.cancelled_at = CURRENT_TIMESTAMP;
    END IF;
END$$

-- -----------------------------------------------------------------------------
-- 5. Trigger kiểm tra khi THÊM MỚI giao dịch thanh toán (payment)
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
-- 6. Trigger kiểm tra khi CẬP NHẬT giao dịch thanh toán (payment)
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
