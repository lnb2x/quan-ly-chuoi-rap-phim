# Thiết kế lược đồ cơ sở dữ liệu quan hệ và từ điển dữ liệu

---

## 1. Quy trình Ánh xạ E–R sang 14 Lược đồ Quan hệ Mức Logic theo Bài giảng

Toàn bộ 14 lược đồ quan hệ và 17 khóa ngoại của hệ thống được chuyển đổi trực tiếp từ Sơ đồ E–R (`docs/erd/ER.drawio`) theo đúng **quy trình 7 bước ánh xạ mô hình E–R sang mô hình quan hệ** trong Chương 2 Phần 3 (*Slide 5 tr. 19–43; Giáo trình CSDL tr. 65–76 — TS. Nguyễn Quỳnh Chi*):

1. **Bước 1 — Ánh xạ các thực thể thông thường / thực thể khỏe (Slide 5 tr. 19–22, Giáo trình tr. 65–67):**
   - Ánh xạ 12 tập thực thể khỏe (`CHI_NHANH_RAP`, `PHONG_CHIEU`, `HANG_GHE`, `GHE`, `PHIM`, `THE_LOAI`, `SUAT_CHIEU`, `KHACH_HANG`, `NHAN_VIEN`, `DON_DAT_VE`, `VE_XEM_PHIM`, `THANH_TOAN`) thành 12 lược đồ quan hệ tương ứng, giữ nguyên **59 thuộc tính đơn bản thân** và khóa chính định danh của từng thực thể.
   - Không có thuộc tính ghép cần tách thành phần con; thuộc tính đa trị thể loại của phim được chuyển sang quan hệ liên kết `movie_genre` thay vì lưu danh sách phân cách bằng dấu phẩy.
2. **Bước 2 — Xem xét thực thể yếu vs thực thể mạnh (Slide 5 tr. 24, Giáo trình tr. 46–47 & 67–68):**
   - Khác với ví dụ `payment-number` đánh số lại từ `1` theo từng khoản vay trong giáo trình tr. 46, các biên lai `payment` và tấm vé `ticket` của chuỗi rạp cần mã định danh toàn cục độc lập (`payment_id`, `ticket_id`) để đối soát cổng thanh toán, quét mã vé tại cổng soát vé và tra cứu kiểm toán toàn chuỗi, nên được thiết kế là các thực thể khỏe có khóa chính riêng.
3. **Bước 3 — Ánh xạ các mối quan hệ hai ngôi (Slide 5 tr. 26–31, Giáo trình tr. 68–71):**
   - **12 mối quan hệ `1 - N` hai ngôi (`SO_HUU`, `QUAN_LY_NV`, `CHUA_GHE`, `PHAN_HANG`, `XEP_LICH`, `TO_CHUC_TAI`, `CO_DON_DAT`, `DAT_MUA`, `LAP_DON`, `BAO_GOM_VE`, `GAN_CHO_VE`, `PHAT_SINH_TT`):** Đưa khóa chính của quan hệ phía `1` sang làm khóa ngoại (`FK`) trong quan hệ phía `N` — sinh ra **12 khóa ngoại** (`branch_id` trong `screening_room` và `employee`; `room_id`, `seat_type_code` trong `seat`; `movie_id`, `room_id` trong `screening`; `screening_id`, `customer_id`, `employee_id` trong `booking`; `booking_id`, `seat_id` trong `ticket`; `booking_id` trong `payment`). Riêng `booking.customer_id` cho phép `NULL` vì `DAT_MUA` có sự tham gia một phần ở cả hai phía (khách vãng lai).
   - **01 mối quan hệ `1 - 1` hai ngôi (`GIU_KHOA_GHE` giữa `VE_XEM_PHIM` tham gia tùy chọn và `KHOA_GHE_SUAT_CHIEU` tham gia bắt buộc):** Áp dụng đúng quy tắc ở **Slide 5 tr. 30–31 / Giáo trình tr. 70–71** (*ví dụ `nurses` — `in-charge` === `care center`*), đưa khóa chính `ticket_id` (`MaVe`) của bên tham gia tùy chọn vào quan hệ của bên tham gia bắt buộc (`screening_seat_lock`) làm khóa ngoại `NOT NULL UNIQUE` nhằm tránh lưu trữ giá trị rỗng `NULL` — sinh ra **khóa ngoại thứ 13**.
4. **Bước 4 & Bước 6 — Ánh xạ các thực thể kết hợp (Slide 5 tr. 32–34 & 40–42, Giáo trình tr. 70–75):**
   - **Trường hợp không gán định danh riêng (Slide 5 tr. 33):** Hai thực thể kết hợp `PHIM_THE_LOAI` (`movie_genre`) và `KHOA_GHE_SUAT_CHIEU` (`screening_seat_lock`) tạo thành 2 lược đồ quan hệ kết hợp với khóa chính ngầm định là hợp khóa chính của các thực thể tham gia: `(movie_id, genre_id)` và `(screening_id, seat_id)`, đồng thời đóng vai trò là **04 khóa ngoại** tham chiếu về `movie`, `genre`, `screening`, `seat` — hoàn tất đủ **17 khóa ngoại** ($59 + 17 = 76$ thuộc tính logic trên 14 bảng).
   - *(Ghi chú liên hệ trường hợp có gán định danh riêng — Slide 5 tr. 34, ví dụ `shipment`):* Giải thích lý do `screening` dùng khóa chính riêng `screening_id` và đưa `(movie_id, room_id)` xuống làm khóa ngoại vì cùng một bộ phim có thể chiếu lặp lại nhiều suất ở cùng một phòng chiếu theo thời gian.
5. **Bước 5 & Bước 7 (Quan hệ đệ quy & Lớp cha/lớp con — Slide 5 tr. 35–38 & 44–50):** Phạm vi bài toán không phát sinh phân cấp kế thừa thuộc tính riêng giữa các vai trò nhân viên nên biểu diễn gọn bằng thuộc tính phân loại `employee.role`.

### Sơ đồ Lược đồ Quan hệ theo Quy ước Bài giảng (Thanh ngang & Mũi tên tham chiếu `FK → PK`)

- **Bản vẽ gốc chỉnh sửa được (draw.io):** [`docs/erd/luoc-do-quan-he.drawio`](../erd/luoc-do-quan-he.drawio)
- **Bản xuất Vector & Hình ảnh:** [`docs/erd/luoc-do-quan-he.drawio.pdf`](../erd/luoc-do-quan-he.drawio.pdf) • [`docs/erd/luoc-do-quan-he.drawio.png`](../erd/luoc-do-quan-he.drawio.png)

Theo đúng ký hiệu minh họa trong **Slide 5 tr. 27 & tr. 33** (*Giáo trình tr. 67–79*): mỗi quan hệ là một thanh ngang chia ô thuộc tính; thuộc tính khóa chính (`PK`, bao gồm cả khóa chính ghép kiêm khóa ngoại của hai quan hệ kết hợp `PHIM_THE_LOAI`, `KHOA_GHE_SUAT_CHIEU` theo mẫu `ORDER-LINE` trên Slide 5 tr. 33) gạch chân nét liền; thuộc tính khóa ngoại đơn (`FK`) gạch chân nét đứt; và **17 mũi tên cong đều xuất phát từ thuộc tính khóa ngoại (`FK`) trỏ đầu mũi tên về thuộc tính khóa chính (`PK`) được tham chiếu**.

**Biểu diễn 14 lược đồ quan hệ theo tên tiếng Việt không dấu (trên sơ đồ `luoc-do-quan-he.drawio`; gạch chân liền = `PK`, `#` = `FK`):**
1. **`CHI_NHANH_RAP`** (<u>`MaCN`</u>, `MaCodeCN`, `TenChiNhanh`, `DiaChi`, `ThanhPho`, `SoDienThoai`, `DangHoatDong`)
2. **`PHONG_CHIEU`** (<u>`MaPhong`</u>, *`#MaCN`*, `TenPhong`, `LoaiPhong`, `DangHoatDong`)
3. **`HANG_GHE`** (<u>`MaHangGhe`</u>, `TenHangGhe`, `PhuThu`)
4. **`GHE`** (<u>`MaGhe`</u>, *`#MaPhong`*, `HangGhe`, `SoGhe`, *`#MaHangGhe`*)
5. **`PHIM`** (<u>`MaPhim`</u>, `MaCodePhim`, `TuaDe`, `ThoiLuongPhut`, `NgayKhoiChieu`, `PhanLoaiTuoi`, `QuocGia`, `MoTa`)
6. **`THE_LOAI`** (<u>`MaTL`</u>, `TenTheLoai`, `MoTa`)
7. **`PHIM_THE_LOAI`** (<u>*`#MaPhim`*</u>, <u>*`#MaTL`*</u>)
8. **`SUAT_CHIEU`** (<u>`MaSC`</u>, *`#MaPhim`*, *`#MaPhong`*, `ThoiGianBatDau`, `ThoiGianKetThuc`, `GiaCoSo`, `TrangThai`)
9. **`KHACH_HANG`** (<u>`MaKH`</u>, `HoTen`, `SoDienThoai`, `Email`, `NgaySinh`, `NgayTao`)
10. **`NHAN_VIEN`** (<u>`MaNV`</u>, *`#MaCN`*, `MaCodeNV`, `HoTen`, `SoDienThoai`, `ChucVu`, `NgayVaoLam`)
11. **`DON_DAT_VE`** (<u>`MaDDV`</u>, `MaCodeDDV`, *`#MaSC`*, *`#MaKH`*, *`#MaNV`*, `ThoiGianDat`, `TrangThai`)
12. **`VE_XEM_PHIM`** (<u>`MaVe`</u>, *`#MaDDV`*, *`#MaGhe`*, `DonGia`, `TrangThai`, `ThoiGianHuy`)
13. **`KHOA_GHE_SUAT_CHIEU`** (<u>*`#MaSC`*</u>, <u>*`#MaGhe`*</u>, *`#MaVe`*)
14. **`THANH_TOAN`** (<u>`MaTT`</u>, *`#MaDDV`*, `SoTien`, `PhuongThuc`, `TrangThai`, `NgayThanhToan`, `MaGiaoDich`)

**Biểu diễn ánh xạ sang tên bảng/cột vật lý SQL** *(gạch chân liền = `PK`; chữ nghiêng `#` = `FK`; `AK` = Khóa dự bị / khóa phụ trên toàn bảng)*:
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

### Đối chiếu Kiến trúc Trừu tượng 3 Lớp (Chương 1 — Slide 2 tr. 9–22, Giáo trình tr. 22–26)
- **Mức bên ngoài (External / View Level):** Gồm 04 khung nhìn (`vw_screening_search`, `vw_verified_booking_revenue`, `vw_branch_daily_revenue`, `vw_screening_occupancy` trong `03_views.sql`) tính toán động các dữ liệu dẫn xuất (sức chứa phòng, ghế trống, tỷ lệ lấp đầy, doanh thu thực thu) và định dạng ngày `dd/MM/yyyy` trên giao diện độc lập với mức lưu trữ (độc lập dữ liệu mức logic).
- **Mức khái niệm (Conceptual / Logical Level):** Gồm đúng 14 lược đồ quan hệ chuẩn 3NF với 76 thuộc tính logic và 17 khóa ngoại nêu trên, không chứa thuộc tính dẫn xuất (thỏa định nghĩa 1NF tại Slide 7 tr. 14).
- **Mức bên trong (Internal / Physical Level trên MySQL 8.4 InnoDB):** Do MySQL 8.4 không có cú pháp `CREATE UNIQUE INDEX ... WHERE payment_status = 'SUCCESS'`, tệp `01_schema.sql` bổ sung cấu trúc chỉ mục vật lý `active_success_booking INT GENERATED ALWAYS AS (CASE WHEN payment_status = 'SUCCESS' THEN booking_id ELSE NULL END) STORED` kèm `UNIQUE KEY uq_payment_one_success` (khai thác tính chất `UNIQUE` cho phép nhiều giá trị `NULL` nêu tại Slide 9 tr. 25). Sự thay đổi cơ chế chỉ mục mức trong này hoàn toàn trong suốt đối với mức khái niệm và mức ngoài (độc lập dữ liệu mức vật lý).

---

## 2. Đặc tả chi tiết 14 bảng quan hệ (Từ điển dữ liệu theo `01_schema.sql`)

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

### Bảng 7: `movie_genre` — Quan hệ kết hợp Phim và Thể loại
Quan hệ kết hợp ánh xạ từ thực thể kết hợp `PHIM_THE_LOAI` ($M:N$ giữa `movie` và `genre`).

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
| `birth_date` | `DATE` | `NULL` | `NULL` | — | Ngày sinh khách hàng (không lưu tuổi dẫn xuất theo Slide 2 tr. 11). |
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
| `customer_id` | `INT` | `NULL` | `NULL` | `FK -> customer(customer_id)` | Mã khách hàng thành viên; nhận `NULL` khi là **Khách vãng lai mua trực tiếp tại quầy**. |
| `employee_id` | `INT` | `NOT NULL` | — | `FK -> employee(employee_id)` | Mã nhân viên thực hiện giao dịch đặt vé. |
| `booked_at` | `DATETIME` | `NOT NULL` | `CURRENT_TIMESTAMP` | — | Thời điểm khởi tạo đơn đặt vé. |
| `booking_status` | `VARCHAR(20)` | `NOT NULL` | `'CONFIRMED'` | `CHECK (booking_status IN ('CONFIRMED', 'PENDING', 'CANCELLED'))` | Trạng thái đơn (`CONFIRMED`: Đã xác nhận, `PENDING`: Chờ thanh toán, `CANCELLED`: Đã hủy). |

---

### Bảng 12: `ticket` — Vé xem phim chi tiết theo ghế (Chuẩn 3NF)
Lưu từng vé gắn với từng ghế cụ thể trong một đơn đặt vé, bao gồm cả lịch sử các vé đã hủy (`CANCELLED`). Suất chiếu của vé được suy diễn bảo toàn qua `booking.screening_id` (loại bỏ `screening_id` để khử phụ thuộc hàm bắc cầu).

| Tên cột | Kiểu dữ liệu | NULL? | Mặc định | Ràng buộc (PK/FK/UQ/CHECK) | Mô tả ý nghĩa (Tiếng Việt) |
|---|---|---|---|---|---|
| `ticket_id` | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_ticket)` | Mã định danh tự tăng của tấm vé. |
| `booking_id` | `INT` | `NOT NULL` | — | `FK -> booking(booking_id)`, `UQ(booking_id, seat_id)` | Mã đơn đặt vé chứa vé này. |
| `seat_id` | `INT` | `NOT NULL` | — | `FK -> seat(seat_id)`, `UQ(booking_id, seat_id)` | Mã ghế vật lý được gán cho vé (Trigger đảm bảo `seat.room_id = screening.room_id`). |
| `unit_price` | `DECIMAL(12,2)` | `NOT NULL` | — | `CHECK (unit_price >= 0)` | Đơn giá bán thực tế chốt tại thời điểm phát hành vé (`base_price + surcharge`). |
| `ticket_status` | `VARCHAR(20)` | `NOT NULL` | `'VALID'` | `CHECK (ticket_status IN ('VALID', 'CANCELLED'))` | Trạng thái hiệu lực của vé (`VALID`: Hợp lệ, `CANCELLED`: Đã hủy). |
| `cancelled_at` | `DATETIME` | `NULL` | `NULL` | — | Thời điểm vé bị hủy (`NULL` nếu vé đang `VALID`). |

---

### Bảng 13: `screening_seat_lock` — Quan hệ kết hợp phân bổ ghế hiệu lực theo suất chiếu (Chuẩn 3NF)
Ánh xạ từ thực thể kết hợp `KHOA_GHE_SUAT_CHIEU` và mối quan hệ `1:1` `GIU_KHOA_GHE` (Slide 5 tr. 30 & 33). Đồng bộ tự động qua Trigger khi `ticket` thêm mới, hủy (`CANCELLED`) hoặc xóa, đảm bảo chống bán trùng ghế ở mức khóa chính B-Tree InnoDB.

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
| `payment_id` | Thuộc tính logic | `INT AUTO_INCREMENT` | `NOT NULL` | Auto | `PRIMARY KEY (pk_payment)` | Mã định danh giao dịch thanh toán (khóa dự bị duy nhất của bảng). |
| `booking_id` | Thuộc tính logic | `INT` | `NOT NULL` | — | `FK -> booking(booking_id)` | Mã đơn đặt vé được thanh toán. |
| `amount` | Thuộc tính logic | `DECIMAL(12,2)` | `NOT NULL` | — | `CHECK (amount >= 0)` + Trigger `trg_payment_validate_ins/upd` yêu cầu `amount > 0` (`BR-13`) | Số tiền của giao dịch thanh toán (VNĐ, thực tế bắt buộc dương). |
| `payment_method` | Thuộc tính logic | `VARCHAR(20)` | `NOT NULL` | `'CASH'` | `CHECK (payment_method IN ('CASH', 'CARD', 'TRANSFER', 'EWALLET'))` | Phương thức thanh toán (`CASH`, `CARD`, `TRANSFER`, `EWALLET`). |
| `payment_status` | Thuộc tính logic | `VARCHAR(20)` | `NOT NULL` | `'SUCCESS'` | `CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'))` | Trạng thái thanh toán (`PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`). |
| `paid_at` | Thuộc tính logic | `DATETIME` | `NULL` | `NULL` | — | Thời điểm thanh toán hoàn tất (ghi nhận ngày tính doanh thu khi `SUCCESS`). |
| `transaction_ref` | Thuộc tính logic | `VARCHAR(60)` | `NULL` | `NULL` | — | Mã tham chiếu biên lai / ngân hàng / cổng thanh toán. |
| `active_success_booking` | *Chỉ mục vật lý mức trong* | `INT` | `NULL` | Generated | `GENERATED ALWAYS AS (CASE WHEN payment_status = 'SUCCESS' THEN booking_id ELSE NULL END) STORED`, `UNIQUE (uq_payment_one_success)` | *Cột biểu thức vật lý phục vụ chỉ mục duy nhất có điều kiện trên tập con `payment_status = 'SUCCESS'` (không thuộc lược đồ quan hệ mức logic).* |

---

## 3. Tổng hợp 17 Ràng buộc Toàn vẹn Tham chiếu (`01b_foreign_keys.sql`)

Theo đúng nguyên tắc toàn vẹn tham chiếu trong bài giảng (*Slide 4 tr. 55, Slide 9 tr. 20, Giáo trình tr. 58 & 105*): `ON DELETE RESTRICT` được áp dụng cho các thực thể hạ tầng và giao dịch lịch sử độc lập để ngăn xóa mất dấu vết kiểm toán; `ON DELETE CASCADE` áp dụng cho các quan hệ kết hợp phụ thuộc tồn tại (`movie_genre.movie_id` và `screening_seat_lock.ticket_id`); `ON DELETE SET NULL` áp dụng cho `booking.customer_id` để giữ hóa đơn vãng lai nếu xóa hồ sơ thành viên.

| STT | Tên Ràng buộc (`CONSTRAINT`) | Bảng Tham chiếu (`FK`) | Cột `FK` | Bảng Được Tham chiếu (`PK`) | Cột `PK` | Quy tắc `ON DELETE` / `ON UPDATE` |
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
