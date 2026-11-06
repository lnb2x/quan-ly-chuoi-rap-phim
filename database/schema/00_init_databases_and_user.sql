-- =============================================================================
-- TỆP: 00_init_databases_and_user.sql
-- MỤC ĐÍCH: Khởi tạo CSDL dự án (cinema_chain & cinema_chain_test) và tạo tài
--           khoản ứng dụng phân quyền tối thiểu (cinema_app).
-- LƯU Ý AN TOÀN: Không chạm tới bất kỳ CSDL sẵn có nào khác của người dùng.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS cinema_chain
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS cinema_chain_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Tạo tài khoản phân quyền tối thiểu cho ứng dụng Desktop (Least-Privilege User)
CREATE USER IF NOT EXISTS 'cinema_app'@'localhost' IDENTIFIED BY 'CinemaApp@2026!';
ALTER USER 'cinema_app'@'localhost' IDENTIFIED BY 'CinemaApp@2026!';

CREATE USER IF NOT EXISTS 'cinema_app'@'127.0.0.1' IDENTIFIED BY 'CinemaApp@2026!';
ALTER USER 'cinema_app'@'127.0.0.1' IDENTIFIED BY 'CinemaApp@2026!';

-- Chỉ cấp quyền thao tác dữ liệu (DML / SELECT / SHOW VIEW / EXECUTE) trên 2 CSDL dự án
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE ON cinema_chain.* TO 'cinema_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE ON cinema_chain.* TO 'cinema_app'@'127.0.0.1';

GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE, CREATE, DROP, ALTER, INDEX, TRIGGER, CREATE VIEW
    ON cinema_chain_test.* TO 'cinema_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE, CREATE, DROP, ALTER, INDEX, TRIGGER, CREATE VIEW
    ON cinema_chain_test.* TO 'cinema_app'@'127.0.0.1';

FLUSH PRIVILEGES;
