# MÔ HÌNH THỰC THỂ – LIÊN KẾT (ENTITY-RELATIONSHIP DIAGRAM)
**(Tài liệu đáp ứng Tiêu chí R2 — 2.5 điểm)**

---

## 1. Tổng quan Sơ đồ Thực thể – Liên kết (ERD)

Mô hình Thực thể – Liên kết của **Hệ thống Quản lý Chuỗi Rạp Chiếu Phim** được xây dựng gồm **12 tập thực thể chính** và **01 quan hệ kết hợp nhiều – nhiều (`movie_genre`)**, tương ứng với **13 bảng quan hệ chuẩn 3NF** khi ánh xạ sang mô hình quan hệ.

Hệ thống cung cấp đồng thời:
1. **Sơ đồ ERD mức khái niệm (Chen Notation):** Mã nguồn tại `docs/erd/erd-chen.puml`, ảnh kết xuất tại `docs/erd/erd-chen.png` và `docs/erd/erd-chen.svg`.
2. **Sơ đồ ERD mức logic (Crow's Foot Notation):** Mã nguồn tại `docs/erd/erd-logical.puml`, ảnh kết xuất tại `docs/erd/erd-logical.png` và `docs/erd/erd-logical.svg`.
3. **Sơ đồ Mermaid ER trực quan:** Hiển thị trực tiếp dưới đây.

```mermaid
erDiagram
    cinema_branch ||--|{ screening_room : "so_huu (1:N)"
    cinema_branch ||--|{ employee : "tuyen_dung (1:N)"
    screening_room ||--|{ seat : "chua_ghe (1:N)"
    seat_type ||--o{ seat : "phan_hang (1:N)"
    movie ||--|{ movie_genre : "co_the_loai (1:N)"
    genre ||--o{ movie_genre : "gom_phim (1:N)"
    movie ||--o{ screening : "xep_lich (1:N)"
    screening_room ||--o{ screening : "to_chuc_tai (1:N)"
    screening ||--o{ booking : "nhan_don_dat (1:N)"
    customer |o--o{ booking : "dat_mua (0..1:N)"
    employee ||--o{ booking : "xu_ly_don (1:N)"
    booking ||--|{ ticket : "bao_gom_ve (1:N)"
    screening ||--o{ ticket : "cap_ve_suat (1:N)"
    seat ||--o{ ticket : "gan_cho_ngoi (1:N)"
    booking ||--|{ payment : "thanh_toan (1:N)"

    cinema_branch {
        int branch_id PK
        varchar branch_code UK
        varchar branch_name UK
        varchar address
        varchar city
        varchar phone UK
        tinyint active
    }
    screening_room {
        int room_id PK
        int branch_id FK
        varchar room_name
        varchar room_type
        tinyint active
    }
    seat_type {
        varchar seat_type_code PK
        varchar type_name UK
        decimal surcharge
    }
    seat {
        int seat_id PK
        int room_id FK
        char seat_row
        int seat_number
        varchar seat_type_code FK
    }
    movie {
        int movie_id PK
        varchar movie_code UK
        varchar title
        int duration_minutes
        date release_date
        varchar age_rating
        varchar country
        text description
    }
    genre {
        int genre_id PK
        varchar genre_name UK
        varchar description
    }
    movie_genre {
        int movie_id PK,FK
        int genre_id PK,FK
    }
    screening {
        int screening_id PK
        int movie_id FK
        int room_id FK
        datetime start_time
        datetime end_time
        decimal base_price
        varchar status
    }
    customer {
        int customer_id PK
        varchar full_name
        varchar phone UK
        varchar email UK
        date birth_date
        datetime created_at
    }
    employee {
        int employee_id PK
        int branch_id FK
        varchar employee_code UK
        varchar full_name
        varchar phone UK
        varchar role
        date hired_date
    }
    booking {
        int booking_id PK
        varchar booking_code UK
        int screening_id FK
        int customer_id FK
        int employee_id FK
        datetime booked_at
        varchar booking_status
    }
    ticket {
        int ticket_id PK
        int booking_id FK
        int screening_id FK
        int seat_id FK
        decimal unit_price
        varchar ticket_status
        datetime cancelled_at
        varchar active_seat_key UK
    }
    payment {
        int payment_id PK
        int booking_id FK
        decimal amount
        varchar payment_method
        varchar payment_status
        datetime paid_at
        varchar transaction_ref
        int active_success_booking UK
    }
```

---

## 2. Thuyết minh Chi tiết Từng Tập Thực thể (Entities)

| STT | Tên Thực thể (Code / SQL) | Tên Tiếng Việt | Khóa chính (PK) | Khóa dự tuyển / Duy nhất (UQ) | Giải thích Ý nghĩa Nghiệp vụ |
|---:|---|---|---|---|---|
| 1 | `CinemaBranch` (`cinema_branch`) | Chi nhánh rạp | `branch_id` | `branch_code`, `branch_name`, `phone` | Đại diện cho một cụm rạp chiếu phim thuộc chuỗi rạp đặt tại một địa điểm/thành phố cụ thể. |
| 2 | `ScreeningRoom` (`screening_room`) | Phòng chiếu | `room_id` | `(branch_id, room_name)` | Không gian chiếu phim vật lý thuộc một chi nhánh rạp, phân loại theo công nghệ `2D`, `3D`, `IMAX`. |
| 3 | `SeatType` (`seat_type`) | Hạng ghế | `seat_type_code` | `type_name` | Danh mục phân hạng ghế (`STD` – Ghế Thường, `VIP` – Ghế VIP, `COUPLE` – Ghế Đôi) và phụ thu chuẩn (`surcharge`). |
| 4 | `Seat` (`seat`) | Ghế vật lý | `seat_id` | `(room_id, seat_row, seat_number)` | Vị trí chỗ ngồi cố định trong một phòng chiếu, xác định bởi hàng ghế (`A`..`H`) và số thứ tự ghế. |
| 5 | `Movie` (`movie`) | Phim điện ảnh | `movie_id` | `movie_code` | Tác phẩm điện ảnh được đưa vào khai thác thương mại với thời lượng (`duration_minutes`) và độ tuổi giới hạn (`age_rating`). |
| 6 | `Genre` (`genre`) | Thể loại phim | `genre_id` | `genre_name` | Danh mục thể loại điện ảnh phục vụ phân loại và tìm kiếm phim. |
| 7 | `MovieGenre` (`movie_genre`) | Phim – Thể loại | `(movie_id, genre_id)` | — | Thực thể kết hợp giải quyết mối quan hệ $M:N$ giữa Phim và Thể loại. |
| 8 | `Screening` (`screening`) | Suất chiếu | `screening_id` | — | Một lịch trình chiếu cụ thể của một bộ phim tại một phòng chiếu trong khoảng thời gian `[start_time, end_time)` với mức giá cơ sở `base_price`. |
| 9 | `Customer` (`customer`) | Khách hàng | `customer_id` | `phone`, `email` | Khách hàng đăng ký thành viên của chuỗi rạp để lưu lịch sử mua vé và nhận thông tin. |
| 10 | `Employee` (`employee`) | Nhân viên | `employee_id` | `employee_code`, `phone` | Nhân sự làm việc tại chi nhánh rạp gồm Thu ngân (`CASHIER`), Quản lý chi nhánh (`MANAGER`) và Kế toán (`ACCOUNTANT`). |
| 11 | `Booking` (`booking`) | Đơn đặt vé | `booking_id` | `booking_code` | Phiên giao dịch đặt mua một hoặc nhiều vé cho một suất chiếu xác định (do khách thành viên hoặc khách vãng lai thực hiện). |
| 12 | `Ticket` (`ticket`) | Vé xem phim | `ticket_id` | `active_seat_key` *(cho các vé VALID)* | Tấm vé cụ thể gán một chỗ ngồi (`seat_id`) trong một suất chiếu (`screening_id`) thuộc một đơn đặt vé (`booking_id`) với đơn giá lịch sử `unit_price`. |
| 13 | `Payment` (`payment`) | Giao dịch thanh toán | `payment_id` | `active_success_booking` *(cho thanh toán SUCCESS)* | Bản ghi thanh toán cho đơn đặt vé, lưu số tiền, phương thức và trạng thái (`PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`). |

---

## 3. Thuyết minh Chi tiết Các Mối Quan hệ và Bản số (Relationships & Cardinalities)

1. **`cinema_branch` (1) — Sở hữu — (1..N) `screening_room`:**
   - Một chi nhánh rạp phải có ít nhất một phòng chiếu và có thể sở hữu nhiều phòng chiếu. Mỗi phòng chiếu thuộc về duy nhất một chi nhánh (`screening_room.branch_id NOT NULL`).
2. **`cinema_branch` (1) — Tuyển dụng — (1..N) `employee`:**
   - Một chi nhánh quản lý nhiều nhân viên; mỗi nhân viên biên chế tại một chi nhánh xác định (`employee.branch_id NOT NULL`).
3. **`screening_room` (1) — Chứa ghế — (1..N) `seat`:**
   - Mỗi phòng chiếu có nhiều ghế vật lý được lắp đặt cố định; mỗi ghế vật lý thuộc duy nhất một phòng chiếu (`seat.room_id NOT NULL`).
4. **`seat_type` (1) — Phân hạng — (0..N) `seat`:**
   - Một hạng ghế (`STD`, `VIP`, `COUPLE`) áp dụng cho nhiều ghế; mỗi ghế vật lý có đúng một hạng ghế (`seat.seat_type_code NOT NULL`).
5. **`movie` (0..N) — Thuộc thể loại (`movie_genre`) — (1..N) `genre`:**
   - Một bộ phim có ít nhất một hoặc nhiều thể loại; một thể loại có thể gắn cho nhiều bộ phim. Biểu diễn qua bảng `movie_genre(movie_id, genre_id)`.
6. **`movie` (1) — Được xếp lịch — (0..N) `screening`:**
   - Một bộ phim có thể có không hoặc nhiều suất chiếu; mỗi suất chiếu trình chiếu đúng một bộ phim (`screening.movie_id NOT NULL`).
7. **`screening_room` (1) — Tổ chức tại — (0..N) `screening`:**
   - Một phòng chiếu tổ chức nhiều suất chiếu theo các khung giờ không giao nhau; mỗi suất chiếu diễn ra tại duy nhất một phòng chiếu (`screening.room_id NOT NULL`).
8. **`customer` (0..1) — Đặt mua — (0..N) `booking`:**
   - Một khách hàng thành viên có thể đặt nhiều đơn vé. Ngược lại, một đơn đặt vé có thể thuộc về một khách hàng thành viên (`customer_id` khác `NULL`) hoặc là **Khách vãng lai mua tại quầy** (`customer_id IS NULL`), do đó bản số phía `customer` là `0..1`.
9. **`employee` (1) — Thực hiện — (0..N) `booking`:**
   - Một nhân viên thu ngân lập nhiều đơn đặt vé; mỗi đơn đặt vé được ghi nhận bởi đúng một nhân viên phụ trách (`booking.employee_id NOT NULL`).
10. **`screening` (1) — Có đơn đặt — (0..N) `booking`:**
    - Một suất chiếu có thể có nhiều đơn đặt vé; mỗi đơn đặt vé đặt chỗ cho một suất chiếu xác định (`booking.screening_id NOT NULL`).
11. **`booking` (1) — Bao gồm — (1..N) `ticket`:**
    - Một đơn đặt vé bao gồm từ một đến nhiều vé xem phim (mỗi vé ứng với một ghế); mỗi vé thuộc về duy nhất một đơn đặt vé (`ticket.booking_id NOT NULL`).
12. **`screening` (1) & `seat` (1) — Phát hành vé & Gán chỗ ngồi — (0..N) `ticket`:**
    - Mỗi vé tham chiếu tới suất chiếu (`ticket.screening_id`) và ghế vật lý (`ticket.seat_id`). Ràng buộc duy nhất trên khóa sinh `active_seat_key` đảm bảo tại mọi thời điểm chỉ có tối đa **01 vé hợp lệ (`VALID`)** cho mỗi cặp `(screening_id, seat_id)`, trong khi các vé đã hủy (`CANCELLED`) vẫn được lưu lịch sử.
13. **`booking` (1) — Thanh toán — (1..N) `payment`:**
    - Một đơn đặt vé có thể có nhiều bản ghi giao dịch thanh toán (ví dụ: lần 1 quẹt thẻ `FAILED`, lần 2 chuyển khoản `SUCCESS`), nhưng ràng buộc duy nhất trên `active_success_booking` đảm bảo mỗi đơn chỉ có tối đa **01 giao dịch `SUCCESS`**.
