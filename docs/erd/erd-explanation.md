# MÔ HÌNH THỰC THỂ – LIÊN KẾT (ENTITY-RELATIONSHIP DIAGRAM)
**(Tài liệu đáp ứng Tiêu chí R2 — 2.5 điểm)**

---

## 1. Tổng quan Sơ đồ Thực thể – Liên kết (ERD) theo Bài giảng CSDL

Mô hình Thực thể – Liên kết của **Hệ thống Quản lý Chuỗi Rạp Chiếu Phim** được xây dựng bám sát quy trình thiết kế CSDL mức khái niệm (Chương 2, *Bài giảng Cơ sở dữ liệu* — TS. Nguyễn Quỳnh Chi), gồm **12 tập thực thể thông thường (thực thể khỏe)** và **02 tập thực thể kết hợp (`PHIM_THE_LOAI` / `movie_genre`, `KHOA_GHE_SUAT_CHIEU` / `screening_seat_lock`)**, ánh xạ sang **14 lược đồ quan hệ chuẩn 3NF (và BCNF)** với **17 ràng buộc khóa ngoại**.

Các nguyên tắc học thuật cốt lõi được tuân thủ nghiêm ngặt trên sơ đồ:
- **Tuyệt đối không đưa khóa ngoại lên Sơ đồ E–R mức khái niệm** *(Giáo trình tr. 44–45, Slide 5 tr. 26)*: Sơ đồ E–R Chen chỉ biểu diễn đúng **59 thuộc tính bản thân** của 12 thực thể khỏe; toàn bộ **17 cột khóa ngoại** chỉ xuất hiện ở bước ánh xạ sang Lược đồ Quan hệ thông qua các mối kết hợp và thực thể kết hợp.
- **Không lưu thuộc tính dẫn xuất và thuộc tính đa trị chưa chuẩn hóa** *(Slide 1 tr. 24, Slide 3 tr. 25, Slide 7 tr. 14, Giáo trình tr. 22 & 158)*: Không lưu sức chứa phòng (`capacity` suy từ `COUNT(seat)`), không lưu tổng tiền đơn (`total_amount` suy từ `SUM(ticket.unit_price)`), không lưu tuổi khách hàng (`age` suy từ `birth_date`); thuộc tính đa trị thể loại phim được mô hình hóa bằng thực thể kết hợp `PHIM_THE_LOAI`.
- **Triệt tiêu phụ thuộc hàm bắc cầu trên Vé (`ticket`)**: Thực thể `VE_XEM_PHIM` liên kết với `SUAT_CHIEU` thông qua `DON_DAT_VE`, **không** tạo liên kết dư thừa trực tiếp `SUAT_CHIEU -> VE_XEM_PHIM` nhằm loại bỏ hoàn toàn phụ thuộc bắc cầu $\text{ticket\_id} \to \text{booking\_id} \to \text{screening\_id}$.
- **Ký hiệu Thực thể kết hợp chuẩn bài giảng** *(Slide 5 tr. 18 & 32–33, Giáo trình tr. 66 & 71–72)*: Hai thực thể kết hợp `PHIM_THE_LOAI` và `KHOA_GHE_SUAT_CHIEU` hình thành từ mối quan hệ nhiều – nhiều (`M : N`), được vẽ bằng **hình chữ nhật viền đơn bao quanh biểu tượng hình thoi nội tiếp** và không gán định danh riêng trên E–R (nhận khóa chính ghép ngầm định khi ánh xạ sang mô hình quan hệ).

Hệ thống cung cấp đồng bộ các bản vẽ chỉnh sửa được:
1. **Sơ đồ Thực thể – Liên kết (ERD Chen):** Nguồn chỉnh sửa tại `docs/erd/erd-chen.drawio`, bản in khổ lớn vector `docs/erd/ERD_Chen_A1.pdf`, ảnh kết xuất `docs/erd/erd-chen.png` và `docs/erd/erd-chen.svg`. Gồm 12 hình chữ nhật thực thể khỏe, 02 thực thể kết hợp (chữ nhật lồng hình thoi), 13 hình thoi mối quan hệ hai ngôi, 59 hình elip thuộc tính bản thân (khóa chính in đậm gạch chân liền), 30 nhánh liên kết mang nhãn lực lượng `1` / `N` / `M`, đường nét đơn cho tham gia một phần và **13 đường nét đôi** cho tham gia đầy đủ.
2. **Sơ đồ Lược đồ Quan hệ (Relational Schema):** Nguồn chỉnh sửa tại `docs/erd/luoc-do-quan-he.drawio` (đồng bộ tại `docs/relational-model/luoc-do-quan-he.drawio` và `docs/erd/erd-logical.drawio`), bản in vector `docs/erd/Data_Diagram_A3.pdf` (`docs/relational-model/luoc-do-quan-he.pdf`), ảnh `docs/erd/luoc-do-quan-he.png` và `.svg`. Gồm 14 thanh ngang quan hệ với 76 ô thuộc tính: khóa chính (`PK`) gạch chân nét liền, khóa ngoại (`FK`) gạch chân nét đứt, và **17 mũi tên tham chiếu xuất phát từ ô khóa ngoại (`FK`) trỏ thẳng về ô khóa chính (`PK`) được tham chiếu** đúng quy ước Giáo trình tr. 67–79.
3. **Sơ đồ Mermaid ER & PlantUML đối chiếu cấu trúc DDL:** Mã nguồn tại `docs/erd/erd-chen.puml`, `docs/erd/erd-logical.puml` và sơ đồ Mermaid dưới đây.

```mermaid
erDiagram
    cinema_branch ||--o{ screening_room : "so_huu (1:N)"
    cinema_branch ||--o{ employee : "tuyen_dung (1:N)"
    screening_room ||--o{ seat : "chua_ghe (1:N)"
    seat_type ||--o{ seat : "phan_hang (1:N)"
    movie ||--o{ movie_genre : "co_the_loai (1:N)"
    genre ||--o{ movie_genre : "gom_phim (1:N)"
    movie ||--o{ screening : "xep_lich (1:N)"
    screening_room ||--o{ screening : "to_chuc_tai (1:N)"
    screening ||--o{ booking : "nhan_don_dat (1:N)"
    customer |o--o{ booking : "dat_mua (0..1:N)"
    employee ||--o{ booking : "xu_ly_don (1:N)"
    booking ||--o{ ticket : "bao_gom_ve (1:N)"
    seat ||--o{ ticket : "gan_cho_lich_su (1:N)"
    screening ||--o{ screening_seat_lock : "quan_ly_ghe_hieu_luc (1:N)"
    seat ||--o{ screening_seat_lock : "khoa_ghe_theo_suat (1:N)"
    ticket ||--o| screening_seat_lock : "giu_khoa_khi_VALID (1:0..1)"
    booking ||--o{ payment : "thanh_toan (1:N)"

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
        int booking_id FK "UQ ghep (booking_id, seat_id)"
        int seat_id FK "UQ ghep (booking_id, seat_id)"
        decimal unit_price
        varchar ticket_status
        datetime cancelled_at
    }
    screening_seat_lock {
        int screening_id PK,FK
        int seat_id PK,FK
        int ticket_id FK,UK
    }
    payment {
        int payment_id PK
        int booking_id FK
        decimal amount
        varchar payment_method
        varchar payment_status
        datetime paid_at
        varchar transaction_ref
    }
```

---

### Cách đọc ký hiệu theo Bài giảng & Phạm vi ràng buộc

- **Ký hiệu cơ bản (Slide 3 tr. 17–18, Giáo trình tr. 30–32):** Thực thể khỏe vẽ bằng hình chữ nhật viền đơn; thực thể kết hợp vẽ bằng hình chữ nhật viền đơn bao lấy hình thoi nội tiếp; mối quan hệ hai ngôi vẽ bằng hình thoi; thuộc tính vẽ bằng hình elip nối với thực thể (khóa chính gạch chân liền).
- **Lực lượng liên kết và Ràng buộc tham gia (Slide 3 tr. 29–35, Giáo trình tr. 35–37):** Nhãn `1`, `N`, `M` thể hiện ánh xạ lực lượng liên kết tối đa; **đường nét đôi** biểu diễn sự tham gia đầy đủ (bắt buộc mọi thực thể trong tập đều phải tham gia ít nhất một liên kết), **đường nét đơn** biểu diễn sự tham gia một phần (tùy chọn).
  - Ví dụ 1: `CHI_NHANH_RAP` (**1**, nét đơn) — `SO_HUU` === (**N**, nét đôi) `PHONG_CHIEU`: một chi nhánh mới thành lập có thể chưa tạo phòng chiếu (tham gia một phần), nhưng mỗi phòng chiếu bắt buộc thuộc đúng một chi nhánh xác định (tham gia đầy đủ).
  - Ví dụ 2 (`BR-08`): `DON_DAT_VE` (**1**, nét đôi) === `BAO_GOM_VE` === (**N**, nét đôi) `VE_XEM_PHIM`: cả hai phía đều tham gia đầy đủ vì một đơn mua hoàn chỉnh bắt buộc có ít nhất một vé và mỗi vé thuộc duy nhất một đơn (`BookingService` kiểm tra danh sách ghế khác rỗng và tạo đơn + vé nguyên tử trong cùng một giao dịch).
- **Quy tắc đặt khóa ngoại cho liên kết `1 : 1` tùy chọn – bắt buộc (Slide 5 tr. 30, Giáo trình tr. 70–71):** Trên liên kết `VE_XEM_PHIM` (**1**, nét đơn) — `GIU_KHOA_GHE` === (**1**, nét đôi) `KHOA_GHE_SUAT_CHIEU`, vé tham gia tùy chọn (khi `CANCELLED` không giữ khóa ghế), còn khóa ghế tham gia bắt buộc (mỗi khóa ghế phải thuộc đúng một vé `VALID`). Áp dụng đúng quy tắc bài giảng, khóa chính `MaVe` (`ticket_id`) của bên tùy chọn được đặt sang bên tham gia bắt buộc `KHOA_GHE_SUAT_CHIEU` làm khóa ngoại `NOT NULL UNIQUE`, loại bỏ hoàn toàn việc lưu giá trị rỗng `NULL`.
- **Phân tách mức khái niệm và mức vật lý (Slide 2 tr. 9–12, Giáo trình tr. 22–24):** Cột sinh vật lý `payment.active_success_booking` trong MySQL 8.4 chỉ phục vụ chỉ mục duy nhất có điều kiện cho tập con `payment_status = 'SUCCESS'` ở mức trong, không phải thuộc tính của thực thể trên E–R hay lược đồ quan hệ mức logic.
- Công cụ kiểm chứng tĩnh `python -X utf8 -B tools/verify_erd.py` kiểm tra tự động toàn bộ quy ước trên giữa `.drawio`, `.svg`, `.puml` và DDL SQL.

---

## 2. Thuyết minh Chi tiết 14 Tập Thực thể & Thực thể Kết hợp

*(Lưu ý: Khóa dự bị / khóa phụ `Alternate Keys` chỉ liệt kê các siêu khóa tối thiểu định danh duy nhất trên **mọi bộ** của quan hệ và không chứa `NULL`).*

| STT | Tên trên Sơ đồ (`ERD` / `Lược đồ QH`) | Phân loại trên E–R | Tên Bảng (`SQL`) | Khóa chính (`PK`) | Khóa dự bị khác (`AK`) | Giải thích Ý nghĩa Nghiệp vụ |
|---:|---|---|---|---|---|---|
| 1 | `CHI_NHANH_RAP` | Thực thể khỏe | `cinema_branch` | `MaCN` (`branch_id`) | `branch_code`, `branch_name`, `phone` | Cụm rạp chiếu phim thuộc chuỗi đặt tại một địa điểm/thành phố cụ thể (7 thuộc tính bản thân). |
| 2 | `PHONG_CHIEU` | Thực thể khỏe | `screening_room` | `MaPhong` (`room_id`) | `(branch_id, room_name)` | Không gian chiếu phim vật lý (`2D`, `3D`, `IMAX`); `MaCN` sinh ra khi ánh xạ liên kết `SO_HUU`. |
| 3 | `HANG_GHE` | Thực thể khỏe | `seat_type` | `MaHangGhe` (`seat_type_code`) | `type_name` | Danh mục phân hạng ghế (`STD`, `VIP`, `COUPLE`) và mức phụ thu chuẩn (`surcharge`). |
| 4 | `GHE` | Thực thể khỏe | `seat` | `MaGhe` (`seat_id`) | `(room_id, seat_row, seat_number)` | Vị trí chỗ ngồi cố định xác định bởi hàng ghế (`HangGhe`) và số ghế (`SoGhe`) trong phòng chiếu. |
| 5 | `PHIM` | Thực thể khỏe | `movie` | `MaPhim` (`movie_id`) | `movie_code` | Tác phẩm điện ảnh khai thác thương mại với thời lượng (`duration_minutes`) và phân loại tuổi (`age_rating`). |
| 6 | `THE_LOAI` | Thực thể khỏe | `genre` | `MaTL` (`genre_id`) | `genre_name` | Danh mục thể loại điện ảnh phục vụ phân loại và tra cứu phim. |
| 7 | `PHIM_THE_LOAI` | **Thực thể kết hợp** (`M:N`) | `movie_genre` | `(MaPhim, MaTL)` *(ngầm định)* | — | Thực thể kết hợp không gán định danh riêng giữa `PHIM` và `THE_LOAI`; nhận khóa ghép `(movie_id, genre_id)` khi ánh xạ. |
| 8 | `SUAT_CHIEU` | Thực thể khỏe | `screening` | `MaSC` (`screening_id`) | — | Lịch trình chiếu một bộ phim tại một phòng chiếu trong khoảng `[start_time, end_time)` với giá cơ sở `base_price`. |
| 9 | `KHACH_HANG` | Thực thể khỏe | `customer` | `MaKH` (`customer_id`) | `phone`, `email` | Khách hàng đăng ký thành viên của chuỗi rạp (lưu `birth_date`, không lưu tuổi dẫn xuất). |
| 10 | `NHAN_VIEN` | Thực thể khỏe | `employee` | `MaNV` (`employee_id`) | `employee_code`, `phone` | Nhân sự làm việc tại chi nhánh rạp (`CASHIER`, `MANAGER`, `ACCOUNTANT`). |
| 11 | `DON_DAT_VE` | Thực thể khỏe | `booking` | `MaDDV` (`booking_id`) | `booking_code` | Phiên giao dịch đặt mua một hoặc nhiều vé cho một suất chiếu xác định. |
| 12 | `VE_XEM_PHIM` | Thực thể khỏe | `ticket` | `MaVe` (`ticket_id`) | `(booking_id, seat_id)` | Tấm vé cụ thể gán ghế trong đơn đặt vé với đơn giá lịch sử `unit_price` và trạng thái `VALID` / `CANCELLED`. |
| 13 | `KHOA_GHE_SUAT_CHIEU` | **Thực thể kết hợp** (`M:N` + `1:1`) | `screening_seat_lock` | `(MaSC, MaGhe)` *(ngầm định)* | `MaVe` (`ticket_id`) | Thực thể kết hợp biểu diễn phân bổ ghế đang có hiệu lực (`VALID`) trên cặp `(screening_id, seat_id)` và liên kết `1:1` với vé giữ chỗ. |
| 14 | `THANH_TOAN` | Thực thể khỏe | `payment` | `MaTT` (`payment_id`) | — | Giao dịch thanh toán cho đơn đặt vé (`PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`). |

---

## 3. Thuyết minh Chi tiết 13 Mối Quan hệ Hai ngôi & 02 Liên kết Thực thể Kết hợp

1. **`CHI_NHANH_RAP` (1, nét đơn) — `SO_HUU` === (N, nét đôi) `PHONG_CHIEU`:** Chi nhánh mới mở có thể chưa cấu hình phòng chiếu; mỗi phòng chiếu bắt buộc thuộc duy nhất một chi nhánh (`screening_room.branch_id NOT NULL`).
2. **`CHI_NHANH_RAP` (1, nét đơn) — `QUAN_LY_NV` === (N, nét đôi) `NHAN_VIEN`:** Mỗi nhân viên bắt buộc thuộc biên chế một chi nhánh xác định (`employee.branch_id NOT NULL`).
3. **`PHONG_CHIEU` (1, nét đơn) — `CHUA_GHE` === (N, nét đôi) `GHE`:** Mỗi ghế vật lý bắt buộc gắn cố định trong một phòng chiếu (`seat.room_id NOT NULL`).
4. **`HANG_GHE` (1, nét đơn) — `PHAN_HANG` === (N, nét đôi) `GHE`:** Mỗi ghế vật lý bắt buộc được xếp vào đúng một hạng ghế (`seat.seat_type_code NOT NULL`).
5. **`PHIM` (M, nét đơn) — `[◇ PHIM_THE_LOAI]` — (N, nét đơn) `THE_LOAI`:** Thực thể kết hợp biểu diễn mối quan hệ nhiều – nhiều giữa Phim và Thể loại. Theo Bước 4a (Slide 5 tr. 33), khi ánh xạ sang mô hình quan hệ sinh ra bảng `movie_genre(movie_id, genre_id)` với khóa chính ghép gồm 2 khóa ngoại tham chiếu `movie` và `genre`.
6. **`PHIM` (1, nét đơn) — `XEP_LICH` === (N, nét đôi) `SUAT_CHIEU`:** Một phim được xếp nhiều suất chiếu; mỗi suất chiếu trình chiếu đúng một bộ phim (`screening.movie_id NOT NULL`).
7. **`PHONG_CHIEU` (1, nét đơn) — `TO_CHUC_TAI` === (N, nét đôi) `SUAT_CHIEU`:** Một phòng tổ chức nhiều suất chiếu không giao thời gian; mỗi suất chiếu diễn ra tại đúng một phòng (`screening.room_id NOT NULL`).
8. **`KHACH_HANG` (1, nét đơn) — `DAT_MUA` — (N, nét đơn) `DON_DAT_VE`:** Khách thành viên có thể đặt nhiều đơn; đơn đặt vé tại quầy của khách vãng lai không cần tài khoản thành viên (`booking.customer_id IS NULL`), nên cả hai phía đều tham gia một phần (nét đơn).
9. **`NHAN_VIEN` (1, nét đơn) — `LAP_DON` === (N, nét đôi) `DON_DAT_VE`:** Mỗi đơn đặt vé bắt buộc do một nhân viên xác định thực hiện (`booking.employee_id NOT NULL`).
10. **`SUAT_CHIEU` (1, nét đơn) — `CO_DON_DAT` === (N, nét đôi) `DON_DAT_VE`:** Mỗi đơn đặt vé bắt buộc đặt chỗ cho đúng một suất chiếu (`booking.screening_id NOT NULL`).
11. **`DON_DAT_VE` (1, nét đôi) === `BAO_GOM_VE` === (N, nét đôi) `VE_XEM_PHIM` & `GHE` (1, nét đơn) — `GAN_CHO_VE` === (N, nét đôi) `VE_XEM_PHIM`:** Theo `BR-08`, mỗi đơn hoàn chỉnh có ít nhất một vé và mỗi vé thuộc đúng một đơn (hai đầu nét đôi); mỗi vé gán đúng một ghế lịch sử (`ticket.seat_id NOT NULL`, `UNIQUE(booking_id, seat_id)`).
12. **`SUAT_CHIEU` (M, nét đơn) — `[◇ KHOA_GHE_SUAT_CHIEU]` — (N, nét đơn) `GHE` & `VE_XEM_PHIM` (1, nét đơn) — `GIU_KHOA_GHE` === (1, nét đôi) `[◇ KHOA_GHE_SUAT_CHIEU]`:**
    - Thực thể kết hợp `KHOA_GHE_SUAT_CHIEU` mô hình hóa sự phân bổ ghế hiệu lực giữa Suất chiếu và Ghế (ánh xạ Bước 4a tạo khóa chính ghép `(screening_id, seat_id)` gồm 2 FK).
    - Đồng thời, giống cơ chế *Tích hợp / Kết hợp* (Giáo trình tr. 53–55), thực thể kết hợp này tham gia bắt buộc (nét đôi, bản số `1`) vào mối quan hệ hai ngôi `1:1` `GIU_KHOA_GHE` với `VE_XEM_PHIM` (tham gia tùy chọn nét đơn, bản số `1`). Theo quy tắc ánh xạ `1:1` (Slide 5 tr. 30), `ticket_id` được đặt vào `screening_seat_lock` với ràng buộc `NOT NULL UNIQUE`. Khi vé hủy (`CANCELLED`), dòng khóa ghế được giải phóng để bán lại ghế mà vẫn giữ trọn lịch sử trong `ticket`.
13. **`DON_DAT_VE` (1, nét đơn) — `PHAT_SINH_TT` === (N, nét đôi) `THANH_TOAN`:** Một đơn có thể phát sinh nhiều lần thanh toán (ví dụ lần 1 `FAILED`, lần 2 `SUCCESS`); mỗi giao dịch thuộc đúng một đơn (`payment.booking_id NOT NULL`).
