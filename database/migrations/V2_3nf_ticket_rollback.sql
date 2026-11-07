-- =============================================================================
-- TỆP: V2_3nf_ticket_rollback.sql
-- MỤC ĐÍCH: Khôi phục tại chỗ (In-place Rollback) từ lược đồ 14 bảng về lại
--           lược đồ 13 bảng ban đầu mà không làm mất bất kỳ bản ghi nào
--           (nhờ tính chất phân rã kết nối không mất mát `ticket ⋈ booking`).
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

ALTER TABLE booking
    ADD CONSTRAINT uq_booking_id_screening UNIQUE (booking_id, screening_id);

ALTER TABLE ticket
    DROP FOREIGN KEY fk_ticket_booking,
    DROP FOREIGN KEY fk_ticket_seat,
    DROP INDEX uq_ticket_booking_seat,
    ADD COLUMN screening_id INT NULL AFTER booking_id;

UPDATE ticket tk
JOIN booking bk ON bk.booking_id = tk.booking_id
SET tk.screening_id = bk.screening_id;

ALTER TABLE ticket
    MODIFY COLUMN screening_id INT NOT NULL,
    ADD COLUMN active_seat_key VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN ticket_status = 'VALID' THEN CONCAT(screening_id, '-', seat_id) ELSE NULL END
    ) STORED AFTER cancelled_at,
    ADD CONSTRAINT uq_ticket_active_seat UNIQUE (active_seat_key),
    ADD INDEX idx_ticket_screening_status (screening_id, ticket_status),
    ADD INDEX idx_ticket_booking_scr (booking_id, screening_id);

ALTER TABLE ticket
    ADD CONSTRAINT fk_ticket_booking FOREIGN KEY (booking_id)
        REFERENCES booking (booking_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_ticket_booking_scr FOREIGN KEY (booking_id, screening_id)
        REFERENCES booking (booking_id, screening_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_ticket_screening FOREIGN KEY (screening_id)
        REFERENCES screening (screening_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_ticket_seat FOREIGN KEY (seat_id)
        REFERENCES seat (seat_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

DROP TABLE IF EXISTS screening_seat_lock;

DELIMITER $$

CREATE TRIGGER trg_screening_validate_ins
BEFORE INSERT ON screening
FOR EACH ROW
BEGIN
    DECLARE v_duration INT DEFAULT 0;
    DECLARE v_room_active TINYINT DEFAULT 0;
    DECLARE v_overlap_count INT DEFAULT 0;

    IF NEW.end_time <= NEW.start_time THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-05]: Thoi gian ket thuc phai lon hon thoi gian bat dau.';
    END IF;
    IF NEW.base_price <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-04]: Gia ve co so cua suat chieu phai lon hon 0.';
    END IF;
    SELECT duration_minutes INTO v_duration FROM movie WHERE movie_id = NEW.movie_id;
    IF TIMESTAMPDIFF(MINUTE, NEW.start_time, NEW.end_time) < v_duration THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-05]: Khoang thoi gian suat chieu nho hon thoi luong phim.';
    END IF;
    SELECT active INTO v_room_active FROM screening_room WHERE room_id = NEW.room_id;
    IF v_room_active = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-01]: Phong chieu dang tam ngung hoat dong.';
    END IF;
    IF NEW.status = 'OPEN' THEN
        SELECT COUNT(*) INTO v_overlap_count FROM screening s
        WHERE s.room_id = NEW.room_id AND s.status = 'OPEN'
          AND s.start_time < NEW.end_time AND s.end_time > NEW.start_time;
        IF v_overlap_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-06]: Trung lich chieu voi mot suat chieu khac trong cung phong chieu.';
        END IF;
    END IF;
END$$

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
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-05]: Thoi gian ket thuc phai lon hon thoi gian bat dau.';
    END IF;
    IF NEW.base_price <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-04]: Gia ve co so cua suat chieu phai lon hon 0.';
    END IF;
    SELECT active INTO v_room_active FROM screening_room WHERE room_id = NEW.room_id;
    IF v_room_active = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-01]: Phong chieu dang tam ngung hoat dong.';
    END IF;
    IF NEW.room_id <> OLD.room_id THEN
        SELECT COUNT(*) INTO v_ticket_count FROM ticket WHERE screening_id = OLD.screening_id;
        IF v_ticket_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-09]: Khong the doi phong chieu cho suat chieu da phat sinh ve.';
        END IF;
    END IF;
    IF OLD.status = 'OPEN' AND NEW.status = 'CANCELLED' THEN
        SELECT COUNT(*) INTO v_valid_ticket_count FROM ticket WHERE screening_id = OLD.screening_id AND ticket_status = 'VALID';
        IF v_valid_ticket_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the huy suat chieu dang co ve hop le chua duoc hoan huy.';
        END IF;
    END IF;
    SELECT duration_minutes INTO v_duration FROM movie WHERE movie_id = NEW.movie_id;
    IF TIMESTAMPDIFF(MINUTE, NEW.start_time, NEW.end_time) < v_duration THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-05]: Khoang thoi gian suat chieu nho hon thoi luong phim.';
    END IF;
    IF NEW.status = 'OPEN' THEN
        SELECT COUNT(*) INTO v_overlap_count FROM screening s
        WHERE s.room_id = NEW.room_id AND s.screening_id <> NEW.screening_id AND s.status = 'OPEN'
          AND s.start_time < NEW.end_time AND s.end_time > NEW.start_time;
        IF v_overlap_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-06]: Trung lich chieu voi mot suat chieu khac trong cung phong chieu.';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_ticket_validate_ins
BEFORE INSERT ON ticket
FOR EACH ROW
BEGIN
    DECLARE v_scr_room_id INT DEFAULT NULL;
    DECLARE v_scr_status VARCHAR(20) DEFAULT NULL;
    DECLARE v_seat_room_id INT DEFAULT NULL;
    DECLARE v_booking_scr_id INT DEFAULT NULL;

    SELECT room_id, status INTO v_scr_room_id, v_scr_status FROM screening WHERE screening_id = NEW.screening_id;
    SELECT room_id INTO v_seat_room_id FROM seat WHERE seat_id = NEW.seat_id;
    SELECT screening_id INTO v_booking_scr_id FROM booking WHERE booking_id = NEW.booking_id;

    IF v_booking_scr_id IS NULL OR v_booking_scr_id <> NEW.screening_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Suat chieu tren ve khong khop voi suat chieu cua don dat ve.';
    END IF;
    IF v_scr_room_id IS NULL OR v_seat_room_id IS NULL OR v_scr_room_id <> v_seat_room_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-09]: Ghe duoc chon khong thuoc phong chieu cua suat chieu nay.';
    END IF;
    IF NEW.ticket_status = 'VALID' AND v_scr_status <> 'OPEN' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the phat hanh ve hop le cho suat chieu da bi huy.';
    END IF;
    IF NEW.ticket_status = 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
        SET NEW.cancelled_at = CURRENT_TIMESTAMP;
    END IF;
END$$

CREATE TRIGGER trg_ticket_validate_upd
BEFORE UPDATE ON ticket
FOR EACH ROW
BEGIN
    DECLARE v_scr_room_id INT DEFAULT NULL;
    DECLARE v_seat_room_id INT DEFAULT NULL;

    SELECT room_id INTO v_scr_room_id FROM screening WHERE screening_id = NEW.screening_id;
    SELECT room_id INTO v_seat_room_id FROM seat WHERE seat_id = NEW.seat_id;
    IF v_scr_room_id <> v_seat_room_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-09]: Ghe duoc chon khong thuoc phong chieu cua suat chieu nay.';
    END IF;
    IF NEW.ticket_status = 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
        SET NEW.cancelled_at = CURRENT_TIMESTAMP;
    END IF;
END$$

CREATE TRIGGER trg_payment_validate_ins
BEFORE INSERT ON payment
FOR EACH ROW
BEGIN
    IF NEW.amount <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: So tien thanh toan phai lon hon 0.';
    END IF;
    IF NEW.payment_status = 'SUCCESS' AND NEW.paid_at IS NULL THEN
        SET NEW.paid_at = CURRENT_TIMESTAMP;
    END IF;
END$$

CREATE TRIGGER trg_payment_validate_upd
BEFORE UPDATE ON payment
FOR EACH ROW
BEGIN
    IF NEW.amount <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: So tien thanh toan phai lon hon 0.';
    END IF;
    IF NEW.payment_status = 'SUCCESS' AND NEW.paid_at IS NULL THEN
        SET NEW.paid_at = CURRENT_TIMESTAMP;
    END IF;
END$$

DELIMITER ;

CREATE OR REPLACE VIEW vw_screening_search AS
SELECT
    s.screening_id, s.movie_id, m.movie_code, m.title AS movie_title, m.duration_minutes, m.age_rating,
    s.room_id, r.room_name, r.room_type, r.branch_id, b.branch_code, b.branch_name, b.city,
    s.start_time, s.end_time, DATE(s.start_time) AS screening_date, s.base_price, s.status AS screening_status,
    (SELECT COUNT(*) FROM seat st WHERE st.room_id = s.room_id) AS total_seats,
    (SELECT COUNT(*) FROM ticket tk WHERE tk.screening_id = s.screening_id AND tk.ticket_status = 'VALID') AS sold_seats,
    ((SELECT COUNT(*) FROM seat st WHERE st.room_id = s.room_id) -
     (SELECT COUNT(*) FROM ticket tk WHERE tk.screening_id = s.screening_id AND tk.ticket_status = 'VALID')) AS available_seats
FROM screening s
JOIN movie m          ON m.movie_id = s.movie_id
JOIN screening_room r ON r.room_id = s.room_id
JOIN cinema_branch b  ON b.branch_id = r.branch_id;

CREATE OR REPLACE VIEW vw_screening_occupancy AS
SELECT
    s.screening_id, DATE(s.start_time) AS screening_date, s.start_time, s.end_time, s.status AS screening_status,
    s.base_price, m.movie_id, m.movie_code, m.title AS movie_title, r.room_id, r.room_name, r.room_type,
    b.branch_id, b.branch_code, b.branch_name,
    (SELECT COUNT(*) FROM seat st WHERE st.room_id = s.room_id) AS room_capacity,
    (SELECT COUNT(*) FROM ticket tk JOIN booking bk ON bk.booking_id = tk.booking_id
     JOIN payment pm ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
     WHERE tk.screening_id = s.screening_id AND tk.ticket_status = 'VALID' AND bk.booking_status = 'CONFIRMED') AS tickets_sold,
    ((SELECT COUNT(*) FROM seat st WHERE st.room_id = s.room_id) -
     (SELECT COUNT(*) FROM ticket tk WHERE tk.screening_id = s.screening_id AND tk.ticket_status = 'VALID')) AS available_seats,
    ROUND(100.0 * (SELECT COUNT(*) FROM ticket tk JOIN booking bk ON bk.booking_id = tk.booking_id
     JOIN payment pm ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
     WHERE tk.screening_id = s.screening_id AND tk.ticket_status = 'VALID' AND bk.booking_status = 'CONFIRMED')
     / NULLIF((SELECT COUNT(*) FROM seat st WHERE st.room_id = s.room_id), 0), 2) AS occupancy_rate_pct,
    COALESCE((SELECT SUM(tk.unit_price) FROM ticket tk JOIN booking bk ON bk.booking_id = tk.booking_id
     JOIN payment pm ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
     WHERE tk.screening_id = s.screening_id AND tk.ticket_status = 'VALID' AND bk.booking_status = 'CONFIRMED'), 0.00) AS sales_revenue
FROM screening s
JOIN movie m          ON m.movie_id = s.movie_id
JOIN screening_room r ON r.room_id = s.room_id
JOIN cinema_branch b  ON b.branch_id = r.branch_id;
