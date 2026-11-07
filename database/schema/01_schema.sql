-- =============================================================================
-- TỆP: 01_schema.sql
-- DỰ ÁN: HỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM
-- BƯỚC KHỞI TẠO: [Bước 1 - Tạo Bảng] Định nghĩa 14 bảng đạt chuẩn 3NF thực sự với
--                PRIMARY KEY, UNIQUE, CHECK, chỉ mục vật lý có điều kiện và INDEX.
-- THIẾT KẾ CHUẨN MỰC:
--   - KHÔNG sử dụng `DROP TABLE` hay `SET FOREIGN_KEY_CHECKS = 0` để tránh
--     xóa nhầm dữ liệu khi chạy ở chế độ khởi tạo không phá hủy.
--   - Bảng `ticket` đã loại bỏ hoàn toàn thuộc tính bắc cầu `screening_id`
--     (vì `ticket_id -> booking_id -> screening_id`), đảm bảo đạt chuẩn 3NF chặt chẽ.
--   - Bảng `screening_seat_lock` (quan hệ phân bổ ghế đang hiệu lực) bảo đảm
--     chống bán trùng ghế `(screening_id, seat_id)` ngay ở mức B-Tree PRIMARY KEY
--     của MySQL 8.4 InnoDB đồng thời cho phép bán lại ghế sau khi vé hủy (`CANCELLED`).
--   - Các ràng buộc Khóa ngoại (17 FOREIGN KEY) được tách sang `01b_foreign_keys.sql`.
-- TƯƠNG THÍCH: MySQL Community Server 8.4 LTS (InnoDB, utf8mb4_unicode_ci)
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. Bảng cinema_branch (Chi nhánh rạp chiếu phim)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cinema_branch (
    branch_id    INT          NOT NULL AUTO_INCREMENT,
    branch_code  VARCHAR(20)  NOT NULL,
    branch_name  VARCHAR(120) NOT NULL,
    address      VARCHAR(255) NOT NULL,
    city         VARCHAR(80)  NOT NULL,
    phone        VARCHAR(20)  NOT NULL,
    active       TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT pk_cinema_branch  PRIMARY KEY (branch_id),
    CONSTRAINT uq_branch_code    UNIQUE (branch_code),
    CONSTRAINT uq_branch_name    UNIQUE (branch_name),
    CONSTRAINT uq_branch_phone   UNIQUE (phone),
    CONSTRAINT chk_branch_active CHECK (active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. Bảng screening_room (Phòng chiếu phim thuộc chi nhánh)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS screening_room (
    room_id    INT         NOT NULL AUTO_INCREMENT,
    branch_id  INT         NOT NULL,
    room_name  VARCHAR(60) NOT NULL,
    room_type  VARCHAR(20) NOT NULL DEFAULT '2D',
    active     TINYINT(1)  NOT NULL DEFAULT 1,
    CONSTRAINT pk_screening_room   PRIMARY KEY (room_id),
    CONSTRAINT uq_room_branch_name UNIQUE (branch_id, room_name),
    CONSTRAINT chk_room_type       CHECK (room_type IN ('2D', '3D', 'IMAX')),
    CONSTRAINT chk_room_active     CHECK (active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. Bảng seat_type (Danh mục hạng ghế & mức phụ thu chuẩn)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seat_type (
    seat_type_code VARCHAR(16)   NOT NULL,
    type_name      VARCHAR(60)   NOT NULL,
    surcharge      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    CONSTRAINT pk_seat_type       PRIMARY KEY (seat_type_code),
    CONSTRAINT uq_seat_type_name  UNIQUE (type_name),
    CONSTRAINT chk_seat_type_code CHECK (seat_type_code IN ('STD', 'VIP', 'COUPLE')),
    CONSTRAINT chk_seat_surcharge CHECK (surcharge >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 4. Bảng seat (Ghế vật lý cố định trong từng phòng chiếu)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seat (
    seat_id        INT         NOT NULL AUTO_INCREMENT,
    room_id        INT         NOT NULL,
    seat_row       CHAR(1)     NOT NULL,
    seat_number    INT         NOT NULL,
    seat_type_code VARCHAR(16) NOT NULL DEFAULT 'STD',
    CONSTRAINT pk_seat          PRIMARY KEY (seat_id),
    CONSTRAINT uq_seat_position UNIQUE (room_id, seat_row, seat_number),
    CONSTRAINT chk_seat_row     CHECK (seat_row >= 'A' AND seat_row <= 'Z'),
    CONSTRAINT chk_seat_number  CHECK (seat_number >= 1 AND seat_number <= 50),
    INDEX idx_seat_type (seat_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 5. Bảng movie (Phim điện ảnh)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS movie (
    movie_id         INT          NOT NULL AUTO_INCREMENT,
    movie_code       VARCHAR(20)  NOT NULL,
    title            VARCHAR(180) NOT NULL,
    duration_minutes INT          NOT NULL,
    release_date     DATE         NOT NULL,
    age_rating       VARCHAR(10)  NOT NULL DEFAULT 'P',
    country          VARCHAR(80)  NOT NULL,
    description      TEXT         NULL,
    CONSTRAINT pk_movie         PRIMARY KEY (movie_id),
    CONSTRAINT uq_movie_code    UNIQUE (movie_code),
    CONSTRAINT chk_movie_dur    CHECK (duration_minutes >= 1 AND duration_minutes <= 400),
    CONSTRAINT chk_movie_rating CHECK (age_rating IN ('P', 'K', 'T13', 'T16', 'T18'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 6. Bảng genre (Thể loại phim)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS genre (
    genre_id    INT          NOT NULL AUTO_INCREMENT,
    genre_name  VARCHAR(80)  NOT NULL,
    description VARCHAR(255) NULL,
    CONSTRAINT pk_genre      PRIMARY KEY (genre_id),
    CONSTRAINT uq_genre_name UNIQUE (genre_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 7. Bảng movie_genre (Liên kết N-N giữa Phim và Thể loại)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS movie_genre (
    movie_id INT NOT NULL,
    genre_id INT NOT NULL,
    CONSTRAINT pk_movie_genre PRIMARY KEY (movie_id, genre_id),
    INDEX idx_mg_genre (genre_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 8. Bảng screening (Suất chiếu)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS screening (
    screening_id INT           NOT NULL AUTO_INCREMENT,
    movie_id     INT           NOT NULL,
    room_id      INT           NOT NULL,
    start_time   DATETIME      NOT NULL,
    end_time     DATETIME      NOT NULL,
    base_price   DECIMAL(12,2) NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    CONSTRAINT pk_screening        PRIMARY KEY (screening_id),
    CONSTRAINT chk_screening_time  CHECK (end_time > start_time),
    CONSTRAINT chk_screening_price CHECK (base_price > 0),
    CONSTRAINT chk_screening_stat  CHECK (status IN ('OPEN', 'CANCELLED')),
    INDEX idx_screening_room_time (room_id, start_time, end_time),
    INDEX idx_screening_movie_time (movie_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 9. Bảng customer (Khách hàng thành viên)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer (
    customer_id INT          NOT NULL AUTO_INCREMENT,
    full_name   VARCHAR(120) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    email       VARCHAR(120) NOT NULL,
    birth_date  DATE         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer       PRIMARY KEY (customer_id),
    CONSTRAINT uq_customer_phone UNIQUE (phone),
    CONSTRAINT uq_customer_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 10. Bảng employee (Nhân viên làm việc tại chi nhánh)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS employee (
    employee_id   INT          NOT NULL AUTO_INCREMENT,
    branch_id     INT          NOT NULL,
    employee_code VARCHAR(20)  NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'CASHIER',
    hired_date    DATE         NOT NULL,
    CONSTRAINT pk_employee       PRIMARY KEY (employee_id),
    CONSTRAINT uq_employee_code  UNIQUE (employee_code),
    CONSTRAINT uq_employee_phone UNIQUE (phone),
    CONSTRAINT chk_employee_role CHECK (role IN ('CASHIER', 'MANAGER', 'ACCOUNTANT')),
    INDEX idx_employee_branch (branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 11. Bảng booking (Đơn đặt vé; customer_id NULLable cho Khách vãng lai)
--     Mỗi booking gắn với đúng 1 screening_id (booking_id -> screening_id).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS booking (
    booking_id     INT         NOT NULL AUTO_INCREMENT,
    booking_code   VARCHAR(30) NOT NULL,
    screening_id   INT         NOT NULL,
    customer_id    INT         NULL,
    employee_id    INT         NOT NULL,
    booked_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    booking_status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    CONSTRAINT pk_booking         PRIMARY KEY (booking_id),
    CONSTRAINT uq_booking_code    UNIQUE (booking_code),
    CONSTRAINT chk_booking_status CHECK (booking_status IN ('CONFIRMED', 'PENDING', 'CANCELLED')),
    INDEX idx_booking_screening (screening_id, booking_status),
    INDEX idx_booking_customer (customer_id),
    INDEX idx_booking_employee (employee_id),
    INDEX idx_booking_booked_at (booked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 12. Bảng ticket (Vé xem phim chi tiết theo ghế — Đạt chuẩn 3NF / BCNF)
--     - Không lưu dư thừa `screening_id` (suy ra bảo toàn qua `booking.screening_id`).
--     - Khóa dự tuyển tối tiểu trên toàn bảng: `{ticket_id}` (PK) và `{booking_id, seat_id}` (UQ).
--     - Lưu giữ đầy đủ lịch sử vé hủy (`CANCELLED`) và đơn giá chốt lịch sử (`unit_price`).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ticket (
    ticket_id     INT           NOT NULL AUTO_INCREMENT,
    booking_id    INT           NOT NULL,
    seat_id       INT           NOT NULL,
    unit_price    DECIMAL(12,2) NOT NULL,
    ticket_status VARCHAR(20)   NOT NULL DEFAULT 'VALID',
    cancelled_at  DATETIME      NULL,
    CONSTRAINT pk_ticket              PRIMARY KEY (ticket_id),
    CONSTRAINT uq_ticket_booking_seat UNIQUE (booking_id, seat_id),
    CONSTRAINT chk_ticket_price       CHECK (unit_price >= 0),
    CONSTRAINT chk_ticket_status      CHECK (ticket_status IN ('VALID', 'CANCELLED')),
    INDEX idx_ticket_booking_status (booking_id, ticket_status),
    INDEX idx_ticket_seat (seat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 13. Bảng screening_seat_lock (Phân bổ khóa ghế đang hiệu lực theo suất chiếu — 3NF / BCNF)
--     - Biểu diễn tập ghế đang bị giữ bởi các vé có `ticket_status = 'VALID'`.
--     - Khóa dự tuyển tối tiểu trên toàn bảng: `{screening_id, seat_id}` (PK) và `{ticket_id}` (UQ).
--     - Đảm bảo chống bán trùng ghế ở mức chỉ mục B-Tree InnoDB cho cả giao dịch đồng thời,
--       đồng thời tự động giải phóng khóa ghế khi vé chuyển sang `CANCELLED` để bán lại ghế.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS screening_seat_lock (
    screening_id INT NOT NULL,
    seat_id      INT NOT NULL,
    ticket_id    INT NOT NULL,
    CONSTRAINT pk_screening_seat_lock PRIMARY KEY (screening_id, seat_id),
    CONSTRAINT uq_ssl_ticket          UNIQUE (ticket_id),
    INDEX idx_ssl_seat (seat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 14. Bảng payment (Giao dịch thanh toán)
--     - Khóa dự tuyển tối tiểu của quan hệ logic: duy nhất `{payment_id}` (PK).
--     - Cột sinh vật lý `active_success_booking` là cơ chế chỉ mục duy nhất có điều kiện
--       của MySQL 8.4 trên tập con `payment_status = 'SUCCESS'` (không phải khóa của toàn bảng),
--       nhằm ngăn thanh toán thành công trùng lặp cho cùng một `booking_id`.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment (
    payment_id             INT           NOT NULL AUTO_INCREMENT,
    booking_id             INT           NOT NULL,
    amount                 DECIMAL(12,2) NOT NULL,
    payment_method         VARCHAR(20)   NOT NULL DEFAULT 'CASH',
    payment_status         VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS',
    paid_at                DATETIME      NULL,
    transaction_ref        VARCHAR(60)   NULL,
    active_success_booking INT           GENERATED ALWAYS AS (
        CASE WHEN payment_status = 'SUCCESS' THEN booking_id ELSE NULL END
    ) STORED,
    CONSTRAINT pk_payment             PRIMARY KEY (payment_id),
    CONSTRAINT uq_payment_one_success UNIQUE (active_success_booking),
    CONSTRAINT chk_payment_amount     CHECK (amount >= 0),
    CONSTRAINT chk_payment_method     CHECK (payment_method IN ('CASH', 'CARD', 'TRANSFER', 'EWALLET')),
    CONSTRAINT chk_payment_status     CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    INDEX idx_payment_status_paid_at (payment_status, paid_at),
    INDEX idx_payment_booking (booking_id, payment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
