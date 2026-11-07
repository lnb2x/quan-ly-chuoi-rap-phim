-- =============================================================================
-- TỆP: 00_clean_reset_test_db.sql
-- CHẾ ĐỘ SỬ DỤNG: CHỈ DÙNG TRONG CHẾ ĐỘ RESET CSDL KIỂM THỬ (`cinema_chain_test`)
-- MỤC ĐÍCH: Xóa duy nhất cơ sở dữ liệu kiểm thử `cinema_chain_test` và tạo mới lại
--           từ đầu với bộ mã chuẩn tiếng Việt utf8mb4_unicode_ci trên MySQL 8.4 LTS.
-- CAM KẾT AN TOÀN TUYỆT ĐỐI:
--   1. Chỉ tác động duy nhất tới `cinema_chain_test`.
--   2. Tuyệt đối KHÔNG chạm tới `cinema_chain` (CSDL demo/vận hành chính),
--      `mysql`, `sys`, `information_schema`, `performance_schema`, hoặc XAMPP.
-- =============================================================================

DROP DATABASE IF EXISTS cinema_chain_test;

CREATE DATABASE cinema_chain_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
