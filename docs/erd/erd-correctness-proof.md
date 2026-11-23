# Lập luận chứng minh tính đúng đắn của thiết kế ERD & Lược đồ Quan hệ

Ngày đối chiếu cập nhật: **18/11/2026**. Đối tượng: Sơ đồ E–R mức khái niệm chuẩn Chen (`erd-chen.*`, `ERD_Chen_A1.pdf`), Sơ đồ Lược đồ Quan hệ 14 bảng (`luoc-do-quan-he.*`, `erd-logical.*`, `Data_Diagram_A3.pdf`), Crow's Foot (`erd-logical.puml`), DDL/FK/Trigger và trọn bộ 12 tập bài giảng PowerPoint (540 slide) cùng giáo trình *Bài giảng DBMS v1.1* (178 trang) của **TS. Nguyễn Quỳnh Chi**.

**Kết luận có điều kiện:** Thiết kế E–R mức khái niệm và Lược đồ Quan hệ mức logic nhất quán 100% với ký hiệu bài giảng của TS. Nguyễn Quỳnh Chi và khớp với 14 bảng, 76 cột logic, 17 khóa ngoại trong DDL qua bộ kiểm chứng tĩnh `tools/verify_erd.py` (bao gồm 9 phép thử đột biến âm tính). Phép phân tách vé cũ $R_{\text{old}}$ thành $D = \{R_{\text{ticket}}, R_{\text{bs}}\}$ được chứng minh đạt chuẩn 3NF, kết nối không mất thông tin (`Lossless`) và bảo toàn phụ thuộc hàm (`Preserve`) theo đúng hệ thống thuật toán Chương 4 của giáo trình. Chưa tuyên bố mọi quy tắc nghiệp vụ đều được cưỡng chế thuần túy bởi DBMS trong mọi đường ghi SQL trực tiếp ngoài ứng dụng; các ranh giới tại Mục 7 là một phần bắt buộc của kết luận này.

## 1. Phải chứng minh điều gì?

Tính đúng của ERD và Lược đồ Quan hệ được đánh giá so với **yêu cầu nghiệp vụ, tập phụ thuộc hàm và quy tắc ánh xạ chính thống**, không suy ra chỉ từ số lượng hộp hay một bộ dữ liệu mẫu:

1. **Tách biệt mức trừu tượng (Slide 2, trang 9–12):** Sơ đồ E–R mức khái niệm chỉ biểu diễn **59 thuộc tính nội tại** của thực thể (không vẽ 17 cột khóa ngoại làm elip thuộc tính theo cảnh báo tại PDF trang 45 & Slide 5 trang 26; không vẽ cột vật lý `GENERATED` `active_success_booking`; không vẽ thuộc tính đa trị hay dẫn xuất theo Slide 7 trang 14).
2. **Đúng ký hiệu thực thể kết hợp & lực lượng tham gia (Slide 3 trang 17–35, Slide 5 trang 18 & 26–34, PDF trang 66–75):** Hai thực thể kết hợp `PHIM_THE_LOAI` (`movie_genre`) và `KHOA_GHE_SUAT_CHIEU` (`screening_seat_lock`) dùng đúng ký hiệu **hình chữ nhật đơn chứa hình thoi nội tiếp**, nối trực tiếp `M` — `N` với hai thực thể gốc và nhận khóa chính ghép ngầm định từ hai phía.
3. **Đúng chiều mũi tên tham chiếu trên Lược đồ Quan hệ (PDF trang 67–79, 90):** Toàn bộ **17 mũi tên khóa ngoại** xuất phát từ ô `FK` (gạch chân nét đứt) của quan hệ con và trỏ đầu mũi tên vào ô `PK` (gạch chân nét liền) của quan hệ cha (`FK → PK`).
4. **Chuẩn hóa 3NF & phân tách bảo toàn thông tin (Slide 6–7 & PDF trang 145–178):** Cả 14 quan hệ đạt 1NF, 2NF, 3NF (và BCNF) trên tập FD nghiệp vụ đã công bố; phép tách vé cũ thỏa `MinCover`, `3NF.2–3`, `Lossless` (bảng ma trận $T$) và `Preserve`.
5. **Phân định rõ tầng bảo vệ:** Phân biệt rành mạch phần do ERD/PK/FK/UNIQUE/CHECK cưỡng chế, phần do Trigger đồng bộ và phần do giao dịch ứng dụng (`TransactionTemplate` + `FOR UPDATE`) đảm nhiệm.

Nguồn đối chiếu trực tiếp: [requirements.md](../requirements/requirements.md), [business-rules.md](../requirements/business-rules.md), [erd-chen.puml](erd-chen.puml), [erd-chen.drawio](erd-chen.drawio), [luoc-do-quan-he.drawio](../relational-model/luoc-do-quan-he.drawio), [01_schema.sql](../../database/schema/01_schema.sql), [01b_foreign_keys.sql](../../database/schema/01b_foreign_keys.sql), [02_triggers.sql](../../database/schema/02_triggers.sql), [normalization.md](../normalization/normalization.md), [lecture-comparison.md](lecture-comparison.md).

## 2. Chứng minh mô hình bao phủ dữ liệu nghiệp vụ và đẳng thức số cột ($59 + 17 = 76$)

| Nhóm yêu cầu | Thực thể / Thực thể kết hợp | Thuộc tính nội tại trên E–R Chen | Cột FK sinh ra khi ánh xạ sang Lược đồ Quan hệ | Vì sao cần tách như vậy? |
|---|---|---:|---:|---|
| Quản lý hạ tầng chuỗi rạp | `cinema_branch`, `screening_room`, `seat_type`, `seat` | $7 + 4 + 3 + 3 = 17$ | $0 + 1 + 0 + 2 = 3$ | Chi nhánh có nhiều phòng; ghế thuộc phòng; phụ thu quản lý theo hạng ghế, tránh lặp tên hạng/phụ thu trên từng ghế. |
| Quản lý phim & phân loại | `movie`, `genre`, `movie_genre` *(kết hợp)* | $8 + 3 + 0 = 11$ | $0 + 0 + 2 = 2$ | Quan hệ `M:N` giữa Phim và Thể loại ánh xạ thành hai khóa ngoại tạo khóa chính ghép `(movie_id, genre_id)`, không lưu chuỗi gộp thể loại. |
| Lập lịch chiếu | `screening` | $5$ | $2$ | Phim và phòng tồn tại độc lập với từng lần chiếu; giờ bắt đầu/kết thúc, giá cơ sở và trạng thái thuộc suất chiếu. |
| Người mua & người bán | `customer`, `employee` | $6 + 6 = 12$ | $0 + 1 = 1$ | Nhân viên thuộc một chi nhánh xác định; hồ sơ khách hàng độc lập để đơn tại quầy của khách vãng lai có thể để `customer_id = NULL`. |
| Đơn đặt vé nhiều ghế | `booking`, `ticket` | $4 + 4 = 8$ | $3 + 2 = 5$ | Thông tin chung của lần mua nằm ở `booking`; từng ghế và đơn giá chốt lịch sử nằm ở `ticket` (không lưu lại `screening_id`). |
| Giữ khóa ghế hiệu lực | `screening_seat_lock` *(kết hợp)* | $0$ | $3$ | Thực thể kết hợp `M:N` giữa `screening` và `seat`, liên kết `1:1` với `ticket` đang `VALID`: giữ PK ghép `(screening_id, seat_id)` chống bán trùng ghế mà vẫn lưu lịch sử vé `CANCELLED`. |
| Lịch sử thanh toán | `payment` | $6$ | $1$ | Một đơn có thể có giao dịch `FAILED` trước khi `SUCCESS`; không ép toàn bộ lịch sử thanh toán thành `1:1`. |
| **Tổng cộng** | **12 thực thể mạnh + 2 thực thể kết hợp** | **59 thuộc tính nội tại** | **17 cột khóa ngoại** | **$59 + 17 = 76$ cột logic** *(cộng 1 cột vật lý `GENERATED` `active_success_booking` = 77 cột SQL)* |

Sức chứa phòng (`COUNT(seat)`), tổng tiền đơn (`SUM(ticket.unit_price)`) và doanh thu thực thu đều là dữ liệu dẫn xuất tính qua `VIEW`/truy vấn nên không lưu thành thuộc tính trên E–R hay cột bảng gốc, tuân thủ nghiêm ngặt định nghĩa `1NF` trên Slide 7 trang 14.

## 3. Chứng minh bản số và sự tham gia của hệ thống liên kết

Trên sơ đồ Chen (`erd-chen.drawio`), dự án gồm **13 hình thoi quan hệ hai ngôi** (`12` quan hệ `1:N` và `1` quan hệ `1:1` `GIU_KHOA_GHE`) cộng **2 thực thể kết hợp `M:N`** nối trực tiếp với hai thực thể gốc (tổng cộng **30 đoạn nối**, trong đó có **13 đường nét đôi** biểu diễn sự tham gia đầy đủ và **17 đường nét đơn** biểu diễn sự tham gia một phần). Khi ánh xạ sang Lược đồ Quan hệ, hệ thống này sinh ra đúng **17 ràng buộc khóa ngoại (`FK → PK`)**:

| Liên kết / Thực thể kết hợp | Lực lượng trên Chen | Nét nối trên Chen (Cha — Con) | Ánh xạ Khóa ngoại (`FK → PK`) | Căn cứ nghiệp vụ & DDL |
|---|---|---|---|---|
| `CHI_NHANH` — `SO_HUU_PHONG` — `PHONG_CHIEU` | `1 : N` | Đơn — **Đôi** | `screening_room.branch_id → cinema_branch.branch_id` | Phòng phải thuộc đúng 1 chi nhánh (`NOT NULL`); chi nhánh mới cấu hình có thể chưa có phòng (`BR-01`). |
| `CHI_NHANH` — `TUYEN_DUNG` — `NHAN_VIEN` | `1 : N` | Đơn — **Đôi** | `employee.branch_id → cinema_branch.branch_id` | Nhân viên thuộc đúng 1 chi nhánh (`NOT NULL`). |
| `PHONG_CHIEU` — `BO_TRI_GHE` — `GHE` | `1 : N` | Đơn — **Đôi** | `seat.room_id → screening_room.room_id` | Ghế gắn cố định vào 1 phòng (`NOT NULL`). |
| `LOAI_GHE` — `PHAN_HANG` — `GHE` | `1 : N` | Đơn — **Đôi** | `seat.seat_type_code → seat_type.seat_type_code` | Mỗi ghế có đúng 1 hạng ghế (`NOT NULL`); hạng ghế danh mục có thể chưa gán ghế. |
| `PHIM` — `PHIM_THE_LOAI` *(kết hợp)* — `THE_LOAI` | `M : N` | Đơn — Đơn | `movie_genre.movie_id → movie.movie_id`<br>`movie_genre.genre_id → genre.genre_id` | Thực thể kết hợp không có ID riêng nhận PK ghép `(movie_id, genre_id)` (PDF trang 72). |
| `PHIM` — `DUOC_XEP_LICH` — `SUAT_CHIEU` | `1 : N` | Đơn — **Đôi** | `screening.movie_id → movie.movie_id` | Suất chiếu phải chiếu đúng 1 phim (`NOT NULL`); phim sắp khởi chiếu có thể chưa xếp lịch. |
| `PHONG_CHIEU` — `TO_CHUC_CHIEU` — `SUAT_CHIEU` | `1 : N` | Đơn — **Đôi** | `screening.room_id → screening_room.room_id` | Suất chiếu phải diễn ra tại đúng 1 phòng (`NOT NULL`). |
| `KHACH_HANG` — `DAT_DON` — `DON_DAT_VE` | `1 : N` | Đơn — Đơn | `booking.customer_id → customer.customer_id` | `customer_id` cho phép `NULL` đối với khách vãng lai mua trực tiếp tại quầy (`BR-07`). |
| `NHAN_VIEN` — `LAP_DON` — `DON_DAT_VE` | `1 : N` | Đơn — **Đôi** | `booking.employee_id → employee.employee_id` | Mọi đơn đều do đúng 1 nhân viên thu ngân lập (`NOT NULL`). |
| `SUAT_CHIEU` — `CO_DON_DAT` — `DON_DAT_VE` | `1 : N` | Đơn — **Đôi** | `booking.screening_id → screening.screening_id` | Mỗi đơn đặt vé chỉ mua cho đúng 1 suất chiếu (`NOT NULL`). |
| `DON_DAT_VE` — `BAO_GOM_VE` — `VE_XEM_PHIM` | `1 : N` | **Đôi** — **Đôi** | `ticket.booking_id → booking.booking_id` | Đơn hoàn chỉnh bắt buộc có ít nhất 1 vé (`BR-08`, cả hai phía nét đôi trên Chen); vé bắt buộc thuộc 1 đơn (`NOT NULL`). |
| `GHE` — `PHAT_HANH_VE` — `VE_XEM_PHIM` | `1 : N` | Đơn — **Đôi** | `ticket.seat_id → seat.seat_id` | Mỗi vé gắn với đúng 1 ghế vật lý (`NOT NULL`); một ghế có nhiều vé lịch sử qua các suất/lần bán lại. |
| `SUAT_CHIEU` — `KHOA_GHE_SUAT_CHIEU` *(kết hợp)* — `GHE` | `M : N` | Đơn — Đơn | `screening_seat_lock.screening_id → screening.screening_id`<br>`screening_seat_lock.seat_id → seat.seat_id` | Thực thể kết hợp phân bổ ghế đang hiệu lực nhận PK ghép `(screening_id, seat_id)` chống bán trùng ghế ở mức B-Tree InnoDB. |
| `VE_XEM_PHIM` — `GIU_KHOA_GHE` — `KHOA_GHE_SUAT_CHIEU` | `1 : 1` | Đơn — **Đôi** | `screening_seat_lock.ticket_id → ticket.ticket_id` | Áp dụng quy tắc `1:1` một bên tùy chọn – một bên bắt buộc (Slide 5 trang 30 / PDF trang 70–71): đặt `ticket_id NOT NULL UNIQUE` ở phía tham gia đầy đủ `screening_seat_lock`. |
| `DON_DAT_VE` — `GHI_NHAN_TT` — `THANH_TOAN` | `1 : N` | Đơn — **Đôi** | `payment.booking_id → booking.booking_id` | Một đơn có thể có lần `FAILED` rồi `SUCCESS`; mỗi giao dịch thuộc đúng 1 đơn (`NOT NULL`). |

*Lưu ý đối chiếu Chen và Crow's Foot:* Chen biểu diễn quy tắc nghiệp vụ `BR-08` (đơn đặt vé hoàn chỉnh tham gia đầy đủ vào `BAO_GOM_VE`), trong khi bản Crow's Foot phản ánh mức ràng buộc FK của DDL (cho phép chèn dòng `booking` trước khi chèn các dòng `ticket` bên trong cùng một giao dịch).

## 4. Chứng minh chuẩn hóa 3NF, `MinCover`, `Lossless` và `Preserve`

1. **Loại bỏ vi phạm 2NF/3NF ở thiết kế vé cũ:** Trên $U_{\text{old}} = \{\text{ticket\_id}, \text{booking\_id}, \text{screening\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$, hai khóa dự bị tối thiểu là $K_1 = \{\text{ticket\_id}\}$ và $K_2 = \{\text{booking\_id}, \text{seat\_id}\}$. Phụ thuộc hàm $\text{booking\_id} \to \text{screening\_id}$ có vế trái không phải siêu khóa và vế phải không phải thuộc tính khóa, vừa phụ thuộc bộ phận vào $K_2$ (vi phạm 2NF) vừa phụ thuộc bắc cầu qua $K_1 \to \text{booking\_id} \to \text{screening\_id}$ (vi phạm 3NF).
2. **Phủ tối thiểu & Phân tách chuẩn 3NF:** Thực thi `MinCover(F_old)` (Slide 6 trang 47) loại bỏ FD dư thừa `ticket_id → screening_id`, thu được 7 FD tối thiểu $G_{\min}$. Áp dụng `Thuật toán 3NF.2 / 3NF.3` (Slide 7 trang 23–24 & PDF trang 175–178) cho đúng phép phân tách $D = \{R_{\text{ticket}}, R_{\text{bs}}\}$ với $R_{\text{bs}}(\underline{\text{booking\_id}}, \text{screening\_id})$.
3. **Kiểm chứng bằng `Lossless` và `Preserve`:** Bảng ma trận $T$ kích thước $2 \times 7$ của `Lossless(R_old, F_old, D)` biến đổi $b_{13} \leftarrow a_3$ nhờ FD `booking_id → screening_id`, sinh hàng $R_{\text{ticket}}$ toàn ký hiệu $a_1 \dots a_7$ (`Lossless = true`). Thuật toán `Preserve(D, F_old, X -> Y)` xác nhận bảo toàn mọi FD trong $F_{\text{old}}$ (đối chiếu khớp bao đóng trên toàn bộ $2^7 = 128$ tập con thuộc tính trong `tools/verify_erd.py`).
4. **Tính chuẩn 3NF/BCNF của `screening_seat_lock`:** Quan hệ $L(\underline{\text{screening\_id}, \text{seat\_id}}, \text{ticket\_id})$ có đúng hai FD không hiển nhiên $\{\text{screening\_id}, \text{seat\_id}\} \to \text{ticket\_id}$ và $\text{ticket\_id} \to \{\text{screening\_id}, \text{seat\_id}\}$; cả hai vế trái đều là khóa dự bị nên đạt cả 3NF và BCNF.

## 5. Các tình huống kiểm chứng ngữ nghĩa mô hình

*(Bảng lập luận ngữ nghĩa mô hình dữ liệu — không thay thế log thực thi SQL trực tiếp)*:

| Tình huống nghiệp vụ | Kết quả của mô hình 14 bảng | Cơ chế bảo vệ |
|---|---|---|
| Một bộ phim thuộc đồng thời hai thể loại Hành động và Khoa học viễn tưởng | Hợp lệ (2 bộ trong `movie_genre`) | PK ghép `(movie_id, genre_id)` chỉ chặn trùng lặp cùng cặp. |
| Hai chi nhánh khác nhau cùng đặt tên `Phòng 01` | Hợp lệ | Khóa phụ `uq_room_branch_name (branch_id, room_name)`. |
| Khách vãng lai mua 2 ghế tại quầy | Hợp lệ (`customer_id IS NULL`, 2 dòng `ticket`) | Liên kết `DAT_DON` tham gia một phần phía `DON_DAT_VE`. |
| Cùng ghế `A01` bán cho hai suất chiếu khác nhau | Hợp lệ | Hai cặp khóa chính `(screening_id, seat_id)` khác nhau. |
| Hai giao dịch cùng đặt ghế `A01` trong một suất chiếu | Bị từ chối | Khóa chính `PRIMARY KEY (screening_id, seat_id)` trên `screening_seat_lock`. |
| Hủy đơn vé rồi mở bán lại chính ghế đó cho khách mới | Hợp lệ, giữ nguyên lịch sử vé hủy | Trigger xóa dòng trong `screening_seat_lock`, giữ dòng `CANCELLED` trong `ticket`. |
| Thanh toán thẻ lần 1 `FAILED`, thanh toán tiền mặt lần 2 `SUCCESS` | Hợp lệ | `payment.booking_id` là FK không `UNIQUE` toàn bảng. |
| Ghi nhận hai giao dịch `SUCCESS` cho cùng một đơn đặt vé | Bị từ chối | Chỉ mục vật lý `uq_payment_one_success` trên cột sinh có điều kiện. |

## 6. Ranh giới chính xác khi bảo vệ (Phân biệt Mô hình, DDL, Trigger và Ứng dụng)

| Quy tắc | Phạm vi bảo vệ thực tế trong source hiện hành | Lưu ý trình bày trung thực khi bảo vệ |
|---|---|---|
| `BR-08`: Đơn đặt vé có ít nhất 1 vé | `BookingService` kiểm tra danh sách ghế khác rỗng và ghi `booking` + `ticket` trong cùng giao dịch; DDL cho phép `INSERT` dòng `booking` trước khi chèn `ticket`. | Trình bày rõ sự khác biệt giữa nét đôi mức nghiệp vụ trên Chen và trình tự chèn dòng trong giao dịch SQL. |
| `BR-11`: Hủy đơn đồng bộ hủy vé & hoàn tiền | `BookingRepository.cancelBookingRecords()` cập nhật `booking`, `ticket`, `payment` trong cùng giao dịch; trigger trên `ticket` tự giải phóng `screening_seat_lock`. | Không nói rằng chỉ chạy một lệnh `UPDATE booking` đơn lẻ ngoài ứng dụng sẽ tự hủy vé và hoàn tiền. |
| `BR-13`: Số tiền thanh toán khớp tổng vé hợp lệ | `BookingService` tính tổng `unit_price` trong giao dịch tạo đơn; `trg_payment_validate_ins/upd` kiểm tra `amount > 0` và `paid_at`. | Phân biệt phần kiểm tra miền giá trị ở Trigger với phần tính tổng hóa đơn ở tầng `Service` trong giao dịch. |
| Cột vật lý `active_success_booking` trong `payment` | Quan hệ logic `payment` gồm 7 thuộc tính đạt 3NF/BCNF; cột thứ 8 trong `01_schema.sql` là biểu thức sinh vật lý hỗ trợ `UNIQUE` có điều kiện trên tập con `SUCCESS`. | Trình bày đúng ranh giới mức khái niệm/logic và mức trong/vật lý theo Slide 2 trang 9–12. |

## 7. Bằng chứng kiểm chứng tĩnh đã chạy trong lượt đồng bộ này

Lệnh kiểm chứng tĩnh đã chạy trực tiếp:

```powershell
python -X utf8 -B tools/verify_erd.py
```

Kết quả **`PASS` (Exit code `0`)** cho toàn bộ các hạng mục:
- Khớp danh sách 14 bảng, 76 cột logic (+1 cột vật lý `GENERATED`), kiểu dữ liệu, `NULL`/`NOT NULL`, `PK`, `UQ`, 17 `FK` giữa SQL DDL và các sơ đồ logic (`erd-logical.puml`, `luoc-do-quan-he.{drawio,svg}`, `erd-logical.{drawio,svg}`, Mermaid).
- Khớp sơ đồ Chen (`erd-chen.puml`, `erd-chen.drawio`, `erd-chen.svg`): **12 thực thể mạnh + 2 thực thể kết hợp** (có hình thoi nội tiếp), đúng **59 thuộc tính nội tại** (0 thuộc tính khóa ngoại, 0 cột vật lý `GENERATED`), **13 hình thoi quan hệ nhị phân + 2 kết nối `M:N` = 30 cạnh nối**, đúng **13 đường nét đôi** tham gia đầy đủ, không đè chữ/hình và khớp dấu vân tay SHA-256 nguồn `.drawio`.
- Khớp chiều toàn bộ **17 mũi tên `FK → PK`** trên Lược đồ Quan hệ (`luoc-do-quan-he.drawio`/`.svg`).
- Khớp thực thi tự động `MinCover` (7 FD tối thiểu), `Lossless` (ma trận $T$), `Preserve` (128 tập con) và **9 phép thử đột biến âm tính** cố ý làm sai trong bộ nhớ (đều bị phát hiện chính xác).

*Phạm vi kiểm chứng (`AGENTS.md` Mục 7):* Lượt chỉnh sửa đồng bộ tài liệu và sơ đồ này không sửa mã Java, không sửa SQL schema/trigger/view, không đụng chạm CSDL `cinema_chain` và không chạy lại Maven/MySQL/GUI; các kết quả kiểm thử thực thi ứng dụng/MySQL trong báo cáo tổng hợp tiếp tục tham chiếu từ manifest kiểm chứng thực nghiệm `verification/last-run.json` đã xác thực trước đó.

## 8. Đoạn trình bày ngắn khi bảo vệ đồ án

> Nhóm em thiết kế mô hình dữ liệu bám sát quy trình từ mức khái niệm đến mức quan hệ và chuẩn hóa trong bài giảng của cô. Trên sơ đồ E–R chuẩn Chen, nhóm chỉ biểu diễn đúng 59 thuộc tính nội tại của 12 thực thể mạnh, không đưa khóa ngoại hay thuộc tính dẫn xuất lên thực thể; hai mối quan hệ nhiều–nhiều là phân loại phim–thể loại và phân bổ khóa ghế theo suất chiếu được biểu diễn bằng ký hiệu thực thể kết hợp hình chữ nhật lồng hình thoi, đi kèm lực lượng `1`, `N`, `M` và 13 đường nét đôi cho các phía tham gia đầy đủ. Khi ánh xạ sang Lược đồ Quan hệ 14 bảng (76 cột logic), các liên kết sinh ra đúng 17 khóa ngoại với mũi tên tham chiếu vẽ thống nhất từ khóa ngoại nét đứt về khóa chính nét liền (`FK → PK`). Riêng bảng vé không lưu lặp lại `screening_id` để triệt tiêu phụ thuộc bắc cầu `ticket_id → booking_id → screening_id`; phép phân tách này đã được kiểm chứng bằng phủ tối thiểu `MinCover`, thuật toán `3NF.3`, bảng ma trận `Lossless` và thuật toán `Preserve`.
