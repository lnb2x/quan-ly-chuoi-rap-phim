# THIẾT KẾ LƯỢC ĐỒ CƠ SỞ DỮ LIỆU QUAN HỆ VÀ TỪ ĐIỂN DỮ LIỆU
**(Tài liệu đáp ứng Tiêu chí R3 — 2.0 điểm)**

---

## 1. Tổng hợp Danh mục 14 Lược đồ Quan hệ Mức Logic (Chuẩn 3NF)

Quy ước ký hiệu trong biểu diễn lược đồ quan hệ mức logic:
- **Gạch chân nét liền (`PK`)**: Thuộc tính thuộc Khóa chính (Primary Key).
- *Chữ nghiêng kèm dấu `#` (`FK`)*: Thuộc tính đóng vai trò Khóa ngoại (Foreign Key).
- `AK`: Khóa dự tuyển khác trên **toàn bộ các bộ của quan hệ** (Alternate Key — mọi thuộc tính thành phần đều `NOT NULL` và `UNIQUE` trên toàn bảng).

1. **`cinema_branch`** (<u>`branch_id`</u>, `branch_code` [AK], `branch_name` [AK], `address`, `city`, `phone` [AK], `active`)
2. **`screening_room`** (<u>`room_id`</u>, *`#branch_id`*, `room_name`, `room_type`, `active`) — với `AK(branch_id, room_name)`
3. **`seat_type`** (<u>`seat_type_code`</u>, `type_name` [AK], `surcharge`)
4. **`seat`** (<u>`seat_id`</u>, *`#room_id`*, `seat_row`, `seat_number`, *`#seat_type_code`*) — với `AK(room_id, seat_row, seat_number)`
5. **`movie`** (<u>`movie_id`</u>, `movie_code` [AK], `title`, `duration_minutes`, `release_date`, `age_rating`, `country`, `description`)
6. **`genre`** (<u>`genre_id`</u>, `genre_name` [AK], `description`)
7. **`movie_genre`** (<u>*`#movie_id`*, *`#genre_id`*</u>)
8. **`screening`** (<u>`screening_id`</u>, *`#movie_id`*, *`#room_id`*, `start_time`, `end_time`, `base_price`, `status`)
9. **`customer`** (<u>`customer_id`</u>, `full_name`, `phone` [AK], `email` [AK], `birth_date`, `created_at`)
10. **`employee`** (<u>`employee_id`</u>, *`#branch_id`*, `employee_code` [AK], `full_name`, `phone` [AK], `role`, `hired_date`)
11. **`booking`** (<u>`booking_id`</u>, `booking_code` [AK], *`#screening_id`*, *`#customer_id`* [NULLable], *`#employee_id`*, `booked_at`, `booking_status`)
12. **`ticket`** (<u>`ticket_id`</u>, *`#booking_id`*, *`#seat_id`*, `unit_price`, `ticket_status`, `cancelled_at`) — với `AK(booking_id, seat_id)`
13. **`screening_seat_lock`** (<u>*`#screening_id`*, *`#seat_id`*</u>, *`#ticket_id`* [AK])
14. **`payment`** (<u>`payment_id`</u>, *`#booking_id`*, `amount`, `payment_method`, `payment_status`, `paid_at`, `transaction_ref`)

### Phân tách Rõ Mức Logic Quan hệ và Mức Cài đặt Vật lý MySQL 8.4
- **Ở mức mô hình quan hệ logic (Relational Model):** Bảng `payment` chỉ gồm 7 thuộc tính nghiệp vụ nêu trên và có duy nhất một khóa dự tuyển trên toàn bảng là `PK(payment_id)`.
- **Ở mức cài đặt vật lý (Physical DDL trên MySQL 8.4 InnoDB):** Do MySQL 8.4 không hỗ trợ cú pháp `CREATE UNIQUE INDEX ... WHERE payment_status = 'SUCCESS'` như PostgreSQL, tệp `01_schema.sql` cài đặt thêm một cột biểu thức vật lý `active_success_booking INT GENERATED ALWAYS AS (CASE WHEN payment_status = 'SUCCESS' THEN booking_id ELSE NULL END) STORED` kèm `UNIQUE KEY uq_payment_one_success`. Cột này mang giá trị `NULL` đối với các dòng `PENDING`, `FAILED`, `REFUNDED`, nên **tuyệt đối không phải là thuộc tính logic hay khóa dự tuyển của quan hệ `payment`**, mà chỉ là cấu trúc chỉ mục vật lý để cưỡng chế ràng buộc duy nhất trên tập con các giao dịch thành công.
- Đối với nghiệp vụ vé và giữ ghế: Bảng `ticket` đã loại bỏ hoàn toàn cột dư thừa `screening_id` và biểu thức `active_seat_key`; việc chống bán trùng ghế đang hợp lệ (`VALID`) được mô hình hóa tường minh bằng quan hệ chuẩn 3NF `screening_seat_lock(screening_id, seat_id, ticket_id)`.

---

## 2. Đặc tả Chi tiết 14 Bảng Quan hệ (Data Dictionary — Khớp 100% `01_schema.sql`)

### Bảng 1: `cinema_branch` — Chi nhánh Rạp chiếu phim
Lưu trữ danh sách các cụm rạp thuộc chuỗi trên toàn quốc.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `branch_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_cinema_branch)` | Mã định danh tự tăng của chi nhánh rạp. |
| `branch_code` | `VARCHAR(20)` | `NOT NULL` | — | `UNIQUE (uq_branch_code)` | Mã nghiệp vụ của chi nhánh (VD: `CN-HN01`, `CN-HCM01`). |
| `branch_name` | `VARCHAR(120)` | `NOT NULL` | — | `UNIQUE (uq_branch_name)` | Tên giao dịch chính thức của chi nhánh rạp. |
| `address` | `VARCHAR(255)` | `NOT NULL` | — | — | Địa chỉ chi tiết của cụm rạp. |
| `city` | `VARCHAR(80)` | `NOT NULL` | — | — | Tỉnh / Thành phố nơi đặt chi nhánh. |
| `phone` | `VARCHAR(20)` | `NOT NULL` | — | `UNIQUE (uq_branch_phone)` | Số điện thoại đường dây nóng của chi nhánh. |
| `active` | `TINYINT(1)` | `NOT NULL` | `1` | `CHECK (active IN (0, 1))` | Trạng thái hoạt động (`1`: Đang hoạt động, `0`: Tạm ngưng). |

---

### Bảng 2: `screening_room` — Phòng chiếu phim
Lưu thông tin các phòng chiếu thuộc từng chi nhánh rạp.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `room_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_screening_room)` | Mã định danh tự tăng của phòng chiếu. |
| `branch_id` | `INT` | `NOT NULL` | — | `FK -> cinema_branch(branch_id)`, `UQ(branch_id, room_name)` | Mã chi nhánh rạp sở hữu phòng chiếu này. |
| `room_name` | `VARCHAR(60)` | `NOT NULL` | — | `UQ(branch_id, room_name)` | Tên phòng chiếu trong chi nhánh (VD: `Phòng 01 - 2D`). |
| `room_type` | `VARCHAR(20)` | `NOT NULL` | `'2D'` | `CHECK (room_type IN ('2D', '3D', 'IMAX'))` | Công nghệ phòng chiếu (`2D`, `3D`, `IMAX`). |
| `active` | `TINYINT(1)` | `NOT NULL` | `1` | `CHECK (active IN (0, 1))` | Trạng thái vận hành của phòng chiếu. |

---

### Bảng 3: `seat_type` — Danh mục Hạng ghế
Lưu cấu hình các loại ghế và mức phụ thu cộng thêm vào giá vé cơ sở của suất chiếu.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `seat_type_code` | `VARCHAR(16)` | `NOT NULL` | — | `PRIMARY KEY (pk_seat_type)`, `CHECK (seat_type_code IN ('STD', 'VIP', 'COUPLE'))` | Mã hạng ghế (`STD`: Thường, `VIP`: VIP, `COUPLE`: Ghế đôi). |
| `type_name` | `VARCHAR(60)` | `NOT NULL` | — | `UNIQUE (uq_seat_type_name)` | Tên hiển thị tiếng Việt của hạng ghế. |
| `surcharge` | `DECIMAL(12,2)` | `NOT NULL` | `0.00` | `CHECK (surcharge >= 0)` | Phụ thu hạng ghế (VNĐ) cộng vào `base_price` của suất chiếu. |

---

### Bảng 4: `seat` — Ghế vật lý trong Phòng chiếu
Lưu tọa độ từng ghế vật lý thuộc một phòng chiếu xác định.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `seat_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_seat)` | Mã định danh tự tăng của ghế vật lý. |
| `room_id` | `INT` | `NOT NULL` | — | `FK -> screening_room(room_id)`, `UQ(room_id, seat_row, seat_number)` | Mã phòng chiếu chứa ghế này. |
| `seat_row` | `CHAR(1)` | `NOT NULL` | — | `UQ(room_id, seat_row, seat_number)`, `CHECK (seat_row >= 'A' AND seat_row <= 'Z')` | Ký hiệu hàng ghế theo bảng chữ cái (`A`, `B`, ..., `H`). |
| `seat_number` | `INT` | `NOT NULL` | — | `UQ(room_id, seat_row, seat_number)`, `CHECK (seat_number >= 1 AND seat_number <= 50)` | Số thứ tự ghế trong hàng (`1`, `2`, ..., `10`). |
| `seat_type_code` | `VARCHAR(16)` | `NOT NULL` | `'STD'` | `FK -> seat_type(seat_type_code)` | Mã hạng ghế áp dụng cho vị trí ghế này. |

---

### Bảng 5: `movie` — Phim điện ảnh
Lưu danh mục phim được chiếu tại chuỗi rạp.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `movie_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_movie)` | Mã định danh tự tăng của bộ phim. |
| `movie_code` | `VARCHAR(20)` | `NOT NULL` | — | `UNIQUE (uq_movie_code)` | Mã nghiệp vụ của bộ phim (VD: `MV-001`). |
| `title` | `VARCHAR(180)` | `NOT NULL` | — | — | Tựa đề chính thức của bộ phim (tiếng Việt). |
| `duration_minutes` | `INT` | `NOT NULL` | — | `CHECK (duration_minutes >= 1 AND duration_minutes <= 400)` | Thời lượng chiếu thực tế của bộ phim (tính bằng phút). |
| `release_date` | `DATE` | `NOT NULL` | — | — | Ngày khởi chiếu chính thức. |
| `age_rating` | `VARCHAR(10)` | `NOT NULL` | `'P'` | `CHECK (age_rating IN ('P', 'K', 'T13', 'T16', 'T18'))` | Phân loại độ tuổi theo quy định Cục Điện ảnh (`P`, `K`, `T13`, `T16`, `T18`). |
| `country` | `VARCHAR(80)` | `NOT NULL` | — | — | Quốc gia sản xuất phim. |
| `description` | `TEXT` | `NULL` | `NULL` | — | Tóm tắt nội dung phim. |

---

### Bảng 6: `genre` — Thể loại Phim
Lưu danh mục thể loại điện ảnh.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `genre_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_genre)` | Mã định danh thể loại phim. |
| `genre_name` | `VARCHAR(80)` | `NOT NULL` | — | `UNIQUE (uq_genre_name)` | Tên thể loại phim (Hành động, Tâm lý, Kinh dị...). |
| `description` | `VARCHAR(255)` | `NULL` | `NULL` | — | Mô tả chi tiết thể loại. |

---

### Bảng 7: `movie_genre` — Liên kết Phim và Thể loại
Bảng trung gian biểu diễn quan hệ nhiều – nhiều ($M:N$) giữa `movie` và `genre`.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `movie_id` | `INT` | `NOT NULL` | — | `PK(movie_id, genre_id)`, `FK -> movie(movie_id)` | Mã bộ phim. |
| `genre_id` | `INT` | `NOT NULL` | — | `PK(movie_id, genre_id)`, `FK -> genre(genre_id)` | Mã thể loại gắn cho bộ phim. |

---

### Bảng 8: `screening` — Suất chiếu (Lịch chiếu)
Lưu lịch trình chiếu một bộ phim tại một phòng chiếu xác định.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `screening_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_screening)` | Mã định danh suất chiếu. |
| `movie_id` | `INT` | `NOT NULL` | — | `FK -> movie(movie_id)` | Mã bộ phim được trình chiếu. |
| `room_id` | `INT` | `NOT NULL` | — | `FK -> screening_room(room_id)` | Mã phòng chiếu tổ chức suất chiếu. |
| `start_time` | `DATETIME` | `NOT NULL` | — | `INDEX idx_screening_room_time (room_id, start_time, end_time)` | Thời điểm bắt đầu suất chiếu. |
| `end_time` | `DATETIME` | `NOT NULL` | — | `CHECK (end_time > start_time)` + Trigger kiểm tra $\ge$ `start_time + duration_minutes` và không chồng lấn lịch phòng | Thời điểm kết thúc suất chiếu (đã gồm thời lượng phim và thời gian nghỉ/vệ sinh phòng). |
| `base_price` | `DECIMAL(12,2)` | `NOT NULL` | — | `CHECK (base_price > 0)` | Giá vé cơ sở của suất chiếu (VNĐ) áp dụng cho hạng ghế chuẩn `STD`. |
| `status` | `VARCHAR(20)` | `NOT NULL` | `'OPEN'` | `CHECK (status IN ('OPEN', 'CANCELLED'))` | Trạng thái suất chiếu (`OPEN`: Đang mở bán, `CANCELLED`: Đã hủy suất). |

---

### Bảng 9: `customer` — Khách hàng thành viên
Lưu thông tin khách hàng đăng ký thành viên tại chuỗi rạp.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `customer_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_customer)` | Mã định danh khách hàng thành viên. |
| `full_name` | `VARCHAR(120)` | `NOT NULL` | — | — | Họ và tên đầy đủ của khách hàng. |
| `phone` | `VARCHAR(20)` | `NOT NULL` | — | `UNIQUE (uq_customer_phone)` | Số điện thoại liên hệ và định danh thành viên tại quầy. |
| `email` | `VARCHAR(120)` | `NOT NULL` | — | `UNIQUE (uq_customer_email)` | Địa chỉ thư điện tử nhận vé của khách hàng. |
| `birth_date` | `DATE` | `NULL` | `NULL` | — | Ngày sinh khách hàng (phục vụ kiểm tra độ tuổi phim). |
| `created_at` | `DATETIME` | `NOT NULL` | `CURRENT_TIMESTAMP` | — | Thời điểm đăng ký tài khoản thành viên. |

---

### Bảng 10: `employee` — Nhân viên Rạp
Lưu thông tin nhân viên làm việc tại các chi nhánh của chuỗi rạp.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `employee_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_employee)` | Mã định danh tự tăng của nhân viên. |
| `branch_id` | `INT` | `NOT NULL` | — | `FK -> cinema_branch(branch_id)` | Mã chi nhánh nơi nhân viên làm việc. |
| `employee_code` | `VARCHAR(20)` | `NOT NULL` | — | `UNIQUE (uq_employee_code)` | Mã nhân sự (VD: `NV-HN01`, `KT-001`). |
| `full_name` | `VARCHAR(120)` | `NOT NULL` | — | — | Họ và tên nhân viên. |
| `phone` | `VARCHAR(20)` | `NOT NULL` | — | `UNIQUE (uq_employee_phone)` | Số điện thoại liên hệ của nhân viên. |
| `role` | `VARCHAR(20)` | `NOT NULL` | `'CASHIER'` | `CHECK (role IN ('CASHIER', 'MANAGER', 'ACCOUNTANT'))` | Vai trò công việc (`CASHIER`: Thu ngân, `MANAGER`: Quản lý chi nhánh, `ACCOUNTANT`: Kế toán). |
| `hired_date` | `DATE` | `NOT NULL` | — | — | Ngày chính thức nhận việc. |

---

### Bảng 11: `booking` — Đơn đặt vé
Lưu thông tin chung của một lần giao dịch đặt vé xem phim (mỗi đơn thuộc duy nhất 1 suất chiếu).

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `booking_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_booking)` | Mã định danh tự tăng của đơn đặt vé. |
| `booking_code` | `VARCHAR(30)` | `NOT NULL` | — | `UNIQUE (uq_booking_code)` | Mã đơn đặt vé in trên hóa đơn (VD: `BK-20261101-0001`). |
| `screening_id` | `INT` | `NOT NULL` | — | `FK -> screening(screening_id)` | Mã suất chiếu mà đơn đặt vé này mua chỗ. |
| `customer_id` | `INT` | `NULL` | `NULL` | `FK -> customer(customer_id)` | Mã khách hàng thành viên; nhận giá trị `NULL` khi là **Khách vãng lai mua trực tiếp tại quầy**. |
| `employee_id` | `INT` | `NOT NULL` | — | `FK -> employee(employee_id)` | Mã nhân viên thực hiện giao dịch đặt vé. |
| `booked_at` | `DATETIME` | `NOT NULL` | `CURRENT_TIMESTAMP` | — | Thời điểm khởi tạo đơn đặt vé. |
| `booking_status` | `VARCHAR(20)` | `NOT NULL` | `'CONFIRMED'` | `CHECK (booking_status IN ('CONFIRMED', 'PENDING', 'CANCELLED'))` | Trạng thái đơn (`CONFIRMED`: Đã xác nhận, `PENDING`: Chờ thanh toán, `CANCELLED`: Đã hủy). |

---

### Bảng 12: `ticket` — Vé xem phim chi tiết theo ghế (Chuẩn 3NF)
Lưu từng vé gắn với từng ghế cụ thể trong một đơn đặt vé, bao gồm cả lịch sử các vé đã hủy (`CANCELLED`). Suất chiếu của vé được suy diễn bảo toàn qua `booking.screening_id` (không lưu dư thừa `screening_id` để loại bỏ phụ thuộc hàm bắc cầu).

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `ticket_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_ticket)` | Mã định danh tự tăng của tấm vé. |
| `booking_id` | `INT` | `NOT NULL` | — | `FK -> booking(booking_id)`, `UQ(booking_id, seat_id)` | Mã đơn đặt vé chứa vé này. |
| `seat_id` | `INT` | `NOT NULL` | — | `FK -> seat(seat_id)`, `UQ(booking_id, seat_id)` | Mã ghế vật lý được gán cho vé (Trigger đảm bảo `seat.room_id = screening.room_id`). |
| `unit_price` | `DECIMAL(12,2)` | `NOT NULL` | — | `CHECK (unit_price >= 0)` | Đơn giá bán thực tế chốt tại thời điểm phát hành vé (`base_price + surcharge`). |
| `ticket_status` | `VARCHAR(20)` | `NOT NULL` | `'VALID'` | `CHECK (ticket_status IN ('VALID', 'CANCELLED'))` | Trạng thái hiệu lực của vé (`VALID`: Hợp lệ, `CANCELLED`: Đã hủy). |
| `cancelled_at` | `DATETIME` | `NULL` | `NULL` | — | Thời điểm vé bị hủy (`NULL` nếu vé đang `VALID`). |

---

### Bảng 13: `screening_seat_lock` — Khóa phân bổ ghế hiệu lực theo suất chiếu (Chuẩn 3NF)
Lưu sự cấp phát ghế đang có hiệu lực (`VALID`) của một tấm vé trong một suất chiếu xác định. Được đồng bộ tự động qua Trigger khi `ticket` thêm mới, hủy (`CANCELLED`) hoặc xóa, đảm bảo chống bán trùng ghế ở mức khóa chính B-Tree InnoDB mà không làm bẩn chuẩn 3NF của `ticket`.

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `screening_id` | `INT` | `NOT NULL` | — | `PK(screening_id, seat_id)`, `FK -> screening(screening_id)` | Mã suất chiếu đang có ghế được giữ hợp lệ. |
| `seat_id` | `INT` | `NOT NULL` | — | `PK(screening_id, seat_id)`, `FK -> seat(seat_id)` | Mã ghế vật lý đang được giữ trong suất chiếu đó. |
| `ticket_id` | `INT` | `NOT NULL` | — | `UNIQUE (uq_ssl_ticket)`, `FK -> ticket(ticket_id)` | Mã tấm vé đang ở trạng thái `VALID` nắm giữ khóa ghế này. |

---

### Bảng 14: `payment` — Giao dịch Thanh toán
Lưu lịch sử các lần thanh toán của đơn đặt vé.

| Tên cột | Phân loại | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|---|
| `payment_id` | Thuộc tính logic | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_payment)` | Mã định danh giao dịch thanh toán (khóa dự tuyển duy nhất của bảng). |
| `booking_id` | Thuộc tính logic | `INT` | `NOT NULL` | — | `FK -> booking(booking_id)` | Mã đơn đặt vé được thanh toán. |
| `amount` | Thuộc tính logic | `DECIMAL(12,2)` | `NOT NULL` | — | `CHECK (amount >= 0)` | Số tiền của giao dịch thanh toán (VNĐ). |
| `payment_method` | Thuộc tính logic | `VARCHAR(20)` | `NOT NULL` | `'CASH'` | `CHECK (payment_method IN ('CASH', 'CARD', 'TRANSFER', 'EWALLET'))` | Phương thức thanh toán (`CASH`, `CARD`, `TRANSFER`, `EWALLET`). |
| `payment_status` | Thuộc tính logic | `VARCHAR(20)` | `NOT NULL` | `'SUCCESS'` | `CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'))` | Trạng thái thanh toán (`PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`). |
| `paid_at` | Thuộc tính logic | `DATETIME` | `NULL` | `NULL` | — | Thời điểm thanh toán hoàn tất (ghi nhận thời điểm tính doanh thu khi `SUCCESS`). |
| `transaction_ref` | Thuộc tính logic | `VARCHAR(60)` | `NULL` | `NULL` | — | Mã tham chiếu biên lai / ngân hàng / cổng thanh toán. |
| `active_success_booking` | *Chỉ mục vật lý MySQL 8.4* | `INT` | `NULL` | Generated | `GENERATED ALWAYS AS (CASE WHEN payment_status = 'SUCCESS' THEN booking_id ELSE NULL END) STORED`, `UNIQUE (uq_payment_one_success)` | *Cột biểu thức vật lý phục vụ chỉ mục duy nhất có điều kiện trên tập con `payment_status = 'SUCCESS'` (không phải thuộc tính logic hay khóa dự tuyển của toàn bảng).* |

---

## 3. Tổng hợp 17 Ràng buộc Tham chiếu Khóa ngoại (`01b_foreign_keys.sql`)

| STT | Tên Ràng buộc FK | Bảng Con (Child Table) | Cột FK | Bảng Cha (Parent Table) | Cột PK Tham chiếu | Quy tắc `ON DELETE` / `ON UPDATE` |
|---:|---|---|---|---|---|---|
| 1 | `fk_room_branch` | `screening_room` | `branch_id` | `cinema_branch` | `branch_id` | `RESTRICT` / `CASCADE` |
| 2 | `fk_seat_room` | `seat` | `room_id` | `screening_room` | `room_id` | `RESTRICT` / `CASCADE` |
| 3 | `fk_seat_type` | `seat` | `seat_type_code` | `seat_type` | `seat_type_code` | `RESTRICT` / `CASCADE` |
| 4 | `fk_mg_movie` | `movie_genre` | `movie_id` | `movie` | `movie_id` | `CASCADE` / `CASCADE` |
| 5 | `fk_mg_genre` | `movie_genre` | `genre_id` | `genre` | `genre_id` | `RESTRICT` / `CASCADE` |
| 6 | `fk_screening_movie` | `screening` | `movie_id` | `movie` | `movie_id` | `RESTRICT` / `CASCADE` |
| 7 | `fk_screening_room` | `screening` | `room_id` | `screening_room` | `room_id` | `RESTRICT` / `CASCADE` |
| 8 | `fk_employee_branch` | `employee` | `branch_id` | `cinema_branch` | `branch_id` | `RESTRICT` / `CASCADE` |
| 9 | `fk_booking_screening` | `booking` | `screening_id` | `screening` | `screening_id` | `RESTRICT` / `CASCADE` |
| 10 | `fk_booking_customer` | `booking` | `customer_id` | `customer` | `customer_id` | `SET NULL` / `CASCADE` |
| 11 | `fk_booking_employee` | `booking` | `employee_id` | `employee` | `employee_id` | `RESTRICT` / `CASCADE` |
| 12 | `fk_ticket_booking` | `ticket` | `booking_id` | `booking` | `booking_id` | `RESTRICT` / `CASCADE` |
| 13 | `fk_ticket_seat` | `ticket` | `seat_id` | `seat` | `seat_id` | `RESTRICT` / `CASCADE` |
| 14 | `fk_ssl_screening` | `screening_seat_lock` | `screening_id` | `screening` | `screening_id` | `RESTRICT` / `CASCADE` |
| 15 | `fk_ssl_seat` | `screening_seat_lock` | `seat_id` | `seat` | `seat_id` | `RESTRICT` / `CASCADE` |
| 16 | `fk_ssl_ticket` | `screening_seat_lock` | `ticket_id` | `ticket` | `ticket_id` | `CASCADE` / `CASCADE` |
| 17 | `fk_payment_booking` | `payment` | `booking_id` | `booking` | `booking_id` | `RESTRICT` / `RESTRICT` |
