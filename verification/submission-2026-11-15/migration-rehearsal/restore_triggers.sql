-- Khôi phục riêng trigger cũ; không sửa dữ liệu/bảng/view. Chỉ chạy trong cửa sổ bảo trì.
DELIMITER __CINEMA_END__
DROP TRIGGER IF EXISTS `trg_booking_validate_upd`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_booking_validate_upd` BEFORE UPDATE ON `booking` FOR EACH ROW BEGIN
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
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_payment_validate_ins`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_payment_validate_ins` BEFORE INSERT ON `payment` FOR EACH ROW BEGIN
    IF NEW.amount <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: So tien thanh toan phai lon hon 0.';
    END IF;

    IF NEW.payment_status = 'SUCCESS' AND NEW.paid_at IS NULL THEN
        SET NEW.paid_at = CURRENT_TIMESTAMP;
    END IF;
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_payment_validate_upd`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_payment_validate_upd` BEFORE UPDATE ON `payment` FOR EACH ROW BEGIN
    IF NEW.amount <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: So tien thanh toan phai lon hon 0.';
    END IF;

    IF NEW.payment_status = 'SUCCESS' AND NEW.paid_at IS NULL THEN
        SET NEW.paid_at = CURRENT_TIMESTAMP;
    END IF;
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_screening_validate_ins`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_screening_validate_ins` BEFORE INSERT ON `screening` FOR EACH ROW BEGIN
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
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_screening_validate_upd`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_screening_validate_upd` BEFORE UPDATE ON `screening` FOR EACH ROW BEGIN
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
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_ssl_validate_del`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_ssl_validate_del` BEFORE DELETE ON `screening_seat_lock` FOR EACH ROW BEGIN
    DECLARE v_tk_status VARCHAR(20) DEFAULT NULL;

    SELECT ticket_status INTO v_tk_status
    FROM ticket
    WHERE ticket_id = OLD.ticket_id;

    IF v_tk_status = 'VALID' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-10]: Khong the xoa khoa ghe khi ve van dang o trang thai VALID.';
    END IF;
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_ssl_validate_ins`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_ssl_validate_ins` BEFORE INSERT ON `screening_seat_lock` FOR EACH ROW BEGIN
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
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_ssl_validate_upd`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_ssl_validate_upd` BEFORE UPDATE ON `screening_seat_lock` FOR EACH ROW BEGIN
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
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_ticket_lock_after_ins`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_ticket_lock_after_ins` AFTER INSERT ON `ticket` FOR EACH ROW BEGIN
    DECLARE v_booking_scr_id INT DEFAULT NULL;

    IF NEW.ticket_status = 'VALID' THEN
        SELECT screening_id INTO v_booking_scr_id
        FROM booking
        WHERE booking_id = NEW.booking_id;

        INSERT INTO screening_seat_lock (screening_id, seat_id, ticket_id)
        VALUES (v_booking_scr_id, NEW.seat_id, NEW.ticket_id);
    END IF;
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_ticket_lock_after_upd`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_ticket_lock_after_upd` AFTER UPDATE ON `ticket` FOR EACH ROW BEGIN
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
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_ticket_validate_ins`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_ticket_validate_ins` BEFORE INSERT ON `ticket` FOR EACH ROW BEGIN
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
END__CINEMA_END__
DROP TRIGGER IF EXISTS `trg_ticket_validate_upd`__CINEMA_END__
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci__CINEMA_END__
SET SESSION sql_mode='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'__CINEMA_END__
CREATE DEFINER=`root`@`localhost` TRIGGER `trg_ticket_validate_upd` BEFORE UPDATE ON `ticket` FOR EACH ROW BEGIN
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
END__CINEMA_END__
DELIMITER ;
