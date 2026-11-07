-- =============================================================================
-- TỆP: 00_init_databases_and_user.sql
-- MỤC ĐÍCH: Khởi tạo 02 cơ sở dữ liệu dự án (cinema_chain & cinema_chain_test)
--           trên MySQL Community Server 8.4 LTS với bộ mã utf8mb4_unicode_ci.
-- BẢO MẬT: Tài khoản phân quyền tối thiểu (cinema_app) và mật khẩu được cấp phát
--          động bởi database/setup.ps1 từ cấu hình cục bộ (config/application.properties)
--          hoặc biến môi trường CINEMA_DB_PASSWORD (không lưu mật khẩu cứng trong Git).
-- LƯU Ý AN TOÀN: Không chạm tới bất kỳ CSDL sẵn có nào khác của người dùng.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS cinema_chain
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS cinema_chain_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
