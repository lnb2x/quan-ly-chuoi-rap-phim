# Inventory database MySQL 8.4 — metadata đọc ngày 15/11/2026

Không có DDL/DML được thực thi. Chi tiết cột/kiểu/NULL/generated ở `columns.tsv`; DDL thực tế ở `*-show-create.tsv`. Metadata PRIMARY KEY của MySQL dùng tên `PRIMARY`.

## Cấu trúc từng bảng của cinema_chain

### booking

Dòng demo: **162**; dòng test: **160**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| FOREIGN KEY | `fk_booking_customer` | `customer_id` | `customer` |
| FOREIGN KEY | `fk_booking_employee` | `employee_id` | `employee` |
| FOREIGN KEY | `fk_booking_screening` | `screening_id` | `screening` |
| PRIMARY KEY | `PRIMARY` | `booking_id` | `` |
| UNIQUE | `uq_booking_code` | `booking_code` | `` |

Index vật lý (bao gồm PK/UQ):

- `idx_booking_booked_at` (booked_at); không duy nhất.
- `idx_booking_customer` (customer_id); không duy nhất.
- `idx_booking_employee` (employee_id); không duy nhất.
- `idx_booking_screening` (screening_id,booking_status); không duy nhất.
- `PRIMARY` (booking_id); UNIQUE.
- `uq_booking_code` (booking_code); UNIQUE.

CHECK:

- `chk_booking_status`: `(`booking_status` in (_utf8mb4\\'CONFIRMED\\',_utf8mb4\\'PENDING\\',_utf8mb4\\'CANCELLED\\'))`.

Hành vi FK:

- `fk_booking_customer` → `customer`: UPDATE CASCADE; DELETE SET NULL.
- `fk_booking_employee` → `employee`: UPDATE CASCADE; DELETE RESTRICT.
- `fk_booking_screening` → `screening`: UPDATE CASCADE; DELETE RESTRICT.

### cinema_branch

Dòng demo: **4**; dòng test: **4**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| PRIMARY KEY | `PRIMARY` | `branch_id` | `` |
| UNIQUE | `uq_branch_code` | `branch_code` | `` |
| UNIQUE | `uq_branch_name` | `branch_name` | `` |
| UNIQUE | `uq_branch_phone` | `phone` | `` |

Index vật lý (bao gồm PK/UQ):

- `PRIMARY` (branch_id); UNIQUE.
- `uq_branch_code` (branch_code); UNIQUE.
- `uq_branch_name` (branch_name); UNIQUE.
- `uq_branch_phone` (phone); UNIQUE.

CHECK:

- `chk_branch_active`: `(`active` in (0,1))`.

### customer

Dòng demo: **45**; dòng test: **45**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| PRIMARY KEY | `PRIMARY` | `customer_id` | `` |
| UNIQUE | `uq_customer_email` | `email` | `` |
| UNIQUE | `uq_customer_phone` | `phone` | `` |

Index vật lý (bao gồm PK/UQ):

- `PRIMARY` (customer_id); UNIQUE.
- `uq_customer_email` (email); UNIQUE.
- `uq_customer_phone` (phone); UNIQUE.

### employee

Dòng demo: **12**; dòng test: **12**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| FOREIGN KEY | `fk_employee_branch` | `branch_id` | `cinema_branch` |
| PRIMARY KEY | `PRIMARY` | `employee_id` | `` |
| UNIQUE | `uq_employee_code` | `employee_code` | `` |
| UNIQUE | `uq_employee_phone` | `phone` | `` |

Index vật lý (bao gồm PK/UQ):

- `idx_employee_branch` (branch_id); không duy nhất.
- `PRIMARY` (employee_id); UNIQUE.
- `uq_employee_code` (employee_code); UNIQUE.
- `uq_employee_phone` (phone); UNIQUE.

CHECK:

- `chk_employee_role`: `(`role` in (_utf8mb4\\'CASHIER\\',_utf8mb4\\'MANAGER\\',_utf8mb4\\'ACCOUNTANT\\'))`.

Hành vi FK:

- `fk_employee_branch` → `cinema_branch`: UPDATE CASCADE; DELETE RESTRICT.

### genre

Dòng demo: **8**; dòng test: **8**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| PRIMARY KEY | `PRIMARY` | `genre_id` | `` |
| UNIQUE | `uq_genre_name` | `genre_name` | `` |

Index vật lý (bao gồm PK/UQ):

- `PRIMARY` (genre_id); UNIQUE.
- `uq_genre_name` (genre_name); UNIQUE.

### movie

Dòng demo: **20**; dòng test: **20**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| PRIMARY KEY | `PRIMARY` | `movie_id` | `` |
| UNIQUE | `uq_movie_code` | `movie_code` | `` |

Index vật lý (bao gồm PK/UQ):

- `PRIMARY` (movie_id); UNIQUE.
- `uq_movie_code` (movie_code); UNIQUE.

CHECK:

- `chk_movie_dur`: `((`duration_minutes` >= 1) and (`duration_minutes` <= 400))`.
- `chk_movie_rating`: `(`age_rating` in (_utf8mb4\\'P\\',_utf8mb4\\'K\\',_utf8mb4\\'T13\\',_utf8mb4\\'T16\\',_utf8mb4\\'T18\\'))`.

### movie_genre

Dòng demo: **43**; dòng test: **43**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| FOREIGN KEY | `fk_mg_genre` | `genre_id` | `genre` |
| FOREIGN KEY | `fk_mg_movie` | `movie_id` | `movie` |
| PRIMARY KEY | `PRIMARY` | `movie_id,genre_id` | `` |

Index vật lý (bao gồm PK/UQ):

- `idx_mg_genre` (genre_id); không duy nhất.
- `PRIMARY` (movie_id,genre_id); UNIQUE.

Hành vi FK:

- `fk_mg_genre` → `genre`: UPDATE CASCADE; DELETE RESTRICT.
- `fk_mg_movie` → `movie`: UPDATE CASCADE; DELETE CASCADE.

### payment

Dòng demo: **177**; dòng test: **175**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| FOREIGN KEY | `fk_payment_booking` | `booking_id` | `booking` |
| PRIMARY KEY | `PRIMARY` | `payment_id` | `` |
| UNIQUE | `uq_payment_one_success` | `active_success_booking` | `` |

Index vật lý (bao gồm PK/UQ):

- `idx_payment_booking` (booking_id,payment_status); không duy nhất.
- `idx_payment_status_paid_at` (payment_status,paid_at); không duy nhất.
- `PRIMARY` (payment_id); UNIQUE.
- `uq_payment_one_success` (active_success_booking); UNIQUE.

CHECK:

- `chk_payment_amount`: `(`amount` >= 0)`.
- `chk_payment_method`: `(`payment_method` in (_utf8mb4\\'CASH\\',_utf8mb4\\'CARD\\',_utf8mb4\\'TRANSFER\\',_utf8mb4\\'EWALLET\\'))`.
- `chk_payment_status`: `(`payment_status` in (_utf8mb4\\'PENDING\\',_utf8mb4\\'SUCCESS\\',_utf8mb4\\'FAILED\\',_utf8mb4\\'REFUNDED\\'))`.

Hành vi FK:

- `fk_payment_booking` → `booking`: UPDATE RESTRICT; DELETE RESTRICT.

### screening

Dòng demo: **161**; dòng test: **160**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| FOREIGN KEY | `fk_screening_movie` | `movie_id` | `movie` |
| FOREIGN KEY | `fk_screening_room` | `room_id` | `screening_room` |
| PRIMARY KEY | `PRIMARY` | `screening_id` | `` |

Index vật lý (bao gồm PK/UQ):

- `idx_screening_movie_time` (movie_id,start_time); không duy nhất.
- `idx_screening_room_time` (room_id,start_time,end_time); không duy nhất.
- `PRIMARY` (screening_id); UNIQUE.

CHECK:

- `chk_screening_price`: `(`base_price` > 0)`.
- `chk_screening_stat`: `(`status` in (_utf8mb4\\'OPEN\\',_utf8mb4\\'CANCELLED\\'))`.
- `chk_screening_time`: `(`end_time` > `start_time`)`.

Hành vi FK:

- `fk_screening_movie` → `movie`: UPDATE CASCADE; DELETE RESTRICT.
- `fk_screening_room` → `screening_room`: UPDATE CASCADE; DELETE RESTRICT.

### screening_room

Dòng demo: **12**; dòng test: **12**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| FOREIGN KEY | `fk_room_branch` | `branch_id` | `cinema_branch` |
| PRIMARY KEY | `PRIMARY` | `room_id` | `` |
| UNIQUE | `uq_room_branch_name` | `branch_id,room_name` | `` |

Index vật lý (bao gồm PK/UQ):

- `PRIMARY` (room_id); UNIQUE.
- `uq_room_branch_name` (branch_id,room_name); UNIQUE.

CHECK:

- `chk_room_active`: `(`active` in (0,1))`.
- `chk_room_type`: `(`room_type` in (_utf8mb4\\'2D\\',_utf8mb4\\'3D\\',_utf8mb4\\'IMAX\\'))`.

Hành vi FK:

- `fk_room_branch` → `cinema_branch`: UPDATE CASCADE; DELETE RESTRICT.

### screening_seat_lock

Dòng demo: **329**; dòng test: **327**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| FOREIGN KEY | `fk_ssl_screening` | `screening_id` | `screening` |
| FOREIGN KEY | `fk_ssl_seat` | `seat_id` | `seat` |
| FOREIGN KEY | `fk_ssl_ticket` | `ticket_id` | `ticket` |
| PRIMARY KEY | `PRIMARY` | `screening_id,seat_id` | `` |
| UNIQUE | `uq_ssl_ticket` | `ticket_id` | `` |

Index vật lý (bao gồm PK/UQ):

- `idx_ssl_seat` (seat_id); không duy nhất.
- `PRIMARY` (screening_id,seat_id); UNIQUE.
- `uq_ssl_ticket` (ticket_id); UNIQUE.

Hành vi FK:

- `fk_ssl_screening` → `screening`: UPDATE CASCADE; DELETE RESTRICT.
- `fk_ssl_seat` → `seat`: UPDATE CASCADE; DELETE RESTRICT.
- `fk_ssl_ticket` → `ticket`: UPDATE CASCADE; DELETE CASCADE.

### seat

Dòng demo: **960**; dòng test: **960**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| FOREIGN KEY | `fk_seat_room` | `room_id` | `screening_room` |
| FOREIGN KEY | `fk_seat_type` | `seat_type_code` | `seat_type` |
| PRIMARY KEY | `PRIMARY` | `seat_id` | `` |
| UNIQUE | `uq_seat_position` | `room_id,seat_row,seat_number` | `` |

Index vật lý (bao gồm PK/UQ):

- `idx_seat_type` (seat_type_code); không duy nhất.
- `PRIMARY` (seat_id); UNIQUE.
- `uq_seat_position` (room_id,seat_row,seat_number); UNIQUE.

CHECK:

- `chk_seat_number`: `((`seat_number` >= 1) and (`seat_number` <= 50))`.
- `chk_seat_row`: `((`seat_row` >= _utf8mb4\\'A\\') and (`seat_row` <= _utf8mb4\\'Z\\'))`.

Hành vi FK:

- `fk_seat_room` → `screening_room`: UPDATE CASCADE; DELETE RESTRICT.
- `fk_seat_type` → `seat_type`: UPDATE CASCADE; DELETE RESTRICT.

### seat_type

Dòng demo: **3**; dòng test: **3**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| PRIMARY KEY | `PRIMARY` | `seat_type_code` | `` |
| UNIQUE | `uq_seat_type_name` | `type_name` | `` |

Index vật lý (bao gồm PK/UQ):

- `PRIMARY` (seat_type_code); UNIQUE.
- `uq_seat_type_name` (type_name); UNIQUE.

CHECK:

- `chk_seat_surcharge`: `(`surcharge` >= 0)`.
- `chk_seat_type_code`: `(`seat_type_code` in (_utf8mb4\\'STD\\',_utf8mb4\\'VIP\\',_utf8mb4\\'COUPLE\\'))`.

### ticket

Dòng demo: **376**; dòng test: **373**.

| Loại | Tên | Cột | Tham chiếu |
|---|---|---|---|
| FOREIGN KEY | `fk_ticket_booking` | `booking_id` | `booking` |
| FOREIGN KEY | `fk_ticket_seat` | `seat_id` | `seat` |
| PRIMARY KEY | `PRIMARY` | `ticket_id` | `` |
| UNIQUE | `uq_ticket_booking_seat` | `booking_id,seat_id` | `` |

Index vật lý (bao gồm PK/UQ):

- `idx_ticket_booking_status` (booking_id,ticket_status); không duy nhất.
- `idx_ticket_seat` (seat_id); không duy nhất.
- `PRIMARY` (ticket_id); UNIQUE.
- `uq_ticket_booking_seat` (booking_id,seat_id); UNIQUE.

CHECK:

- `chk_ticket_price`: `(`unit_price` >= 0)`.
- `chk_ticket_status`: `(`ticket_status` in (_utf8mb4\\'VALID\\',_utf8mb4\\'CANCELLED\\'))`.

Hành vi FK:

- `fk_ticket_booking` → `booking`: UPDATE CASCADE; DELETE RESTRICT.
- `fk_ticket_seat` → `seat`: UPDATE CASCADE; DELETE RESTRICT.

## Kiểm đếm index thực tế

- `cinema_chain`: 44 index = 14 PK + 16 UNIQUE + 14 index thường.
- `cinema_chain_test`: 44 index = 14 PK + 16 UNIQUE + 14 index thường.
