-- =============================================================================
-- TỆP: 00_clean_reset_cinema_chain.sql
-- CHẾ ĐỘ SỬ DỤNG: CHỈ DÙNG TRONG CHẾ ĐỘ CLEAN RESET (sau khi người dùng xác nhận 'YES')
-- MỤC ĐÍCH: Xóa duy nhất cơ sở dữ liệu dự án `cinema_chain` và tạo mới lại từ đầu
--           với bộ mã chuẩn tiếng Việt utf8mb4_unicode_ci trên MySQL 8.4 LTS.
-- CAM KẾT AN TOÀN:
--   1. Chỉ tác động duy nhất tới `cinema_chain`.
--   2. Tuyệt đối KHÔNG chạm tới `mysql`, `sys`, `information_schema`,
--      `performance_schema`, `cinema_chain_test` hoặc bất kỳ CSDL nào khác.
--   3. Nhờ xóa sạch ở cấp độ SCHEMA (`cinema_chain`), các tệp 01_schema.sql,
--      01b_foreign_keys.sql, 03_views.sql và 10_seed.sql hoàn toàn không cần
--      dùng `DROP TABLE`, `TRUNCATE TABLE` hay `SET FOREIGN_KEY_CHECKS = 0`.
-- =============================================================================

DROP DATABASE IF EXISTS cinema_chain;

CREATE DATABASE cinema_chain
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
