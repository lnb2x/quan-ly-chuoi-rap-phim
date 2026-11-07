-- =============================================================================
-- TỆP: 01b_foreign_keys.sql
-- DỰ ÁN: HỆ THỐNG QUẢN LÝ CHUỖI RẠP CHIẾU PHIM
-- BƯỚC KHỞI TẠO: [Bước 2 - Tạo Khóa ngoại] Thiết lập toàn bộ 17 ràng buộc
--                tham chiếu FOREIGN KEY giữa 14 bảng chuẩn 3NF.
-- LƯU Ý TƯƠNG THÍCH MYSQL 8.4 LTS:
--   - Bảng `payment` sử dụng cột sinh vật lý (`GENERATED ALWAYS AS ... STORED`)
--     phục vụ chỉ mục duy nhất có điều kiện cho dòng `SUCCESS`. Theo quy định
--     của MySQL 8.4 InnoDB (mã lỗi 3108), cột cơ sở `booking_id` tham gia biểu thức
--     STORED GENERATED dùng `ON DELETE RESTRICT ON UPDATE RESTRICT`.
-- =============================================================================

SET NAMES utf8mb4;

-- 1. Phòng chiếu thuộc Chi nhánh rạp (1 - N)
ALTER TABLE screening_room
    ADD CONSTRAINT fk_room_branch FOREIGN KEY (branch_id)
        REFERENCES cinema_branch (branch_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- 2. Ghế vật lý thuộc Phòng chiếu & phân loại theo Hạng ghế
ALTER TABLE seat
    ADD CONSTRAINT fk_seat_room FOREIGN KEY (room_id)
        REFERENCES screening_room (room_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT fk_seat_type FOREIGN KEY (seat_type_code)
        REFERENCES seat_type (seat_type_code)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- 3. Bảng trung gian Phim - Thể loại (N - N)
ALTER TABLE movie_genre
    ADD CONSTRAINT fk_mg_movie FOREIGN KEY (movie_id)
        REFERENCES movie (movie_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_mg_genre FOREIGN KEY (genre_id)
        REFERENCES genre (genre_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- 4. Suất chiếu gắn với Phim và Phòng chiếu
ALTER TABLE screening
    ADD CONSTRAINT fk_screening_movie FOREIGN KEY (movie_id)
        REFERENCES movie (movie_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT fk_screening_room FOREIGN KEY (room_id)
        REFERENCES screening_room (room_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- 5. Nhân viên làm việc tại Chi nhánh rạp
ALTER TABLE employee
    ADD CONSTRAINT fk_employee_branch FOREIGN KEY (branch_id)
        REFERENCES cinema_branch (branch_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- 6. Đơn đặt vé gắn với Suất chiếu, Khách hàng (NULLable cho khách vãng lai) & Nhân viên
ALTER TABLE booking
    ADD CONSTRAINT fk_booking_screening FOREIGN KEY (screening_id)
        REFERENCES screening (screening_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT fk_booking_customer FOREIGN KEY (customer_id)
        REFERENCES customer (customer_id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    ADD CONSTRAINT fk_booking_employee FOREIGN KEY (employee_id)
        REFERENCES employee (employee_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- 7. Vé xem phim chuẩn 3NF gắn với Đơn đặt vé & Ghế vật lý
ALTER TABLE ticket
    ADD CONSTRAINT fk_ticket_booking FOREIGN KEY (booking_id)
        REFERENCES booking (booking_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT fk_ticket_seat FOREIGN KEY (seat_id)
        REFERENCES seat (seat_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- 8. Khóa phân bổ ghế hiệu lực gắn với Suất chiếu, Ghế vật lý & Vé hợp lệ
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

-- 9. Giao dịch thanh toán gắn với Đơn đặt vé
ALTER TABLE payment
    ADD CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id)
        REFERENCES booking (booking_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;
