# PHỤ THUỘC HÀM VÀ CHỨNG MINH CHUẨN HÓA QUAN HỆ (1NF, 2NF, 3NF)
**(Tài liệu đáp ứng Tiêu chí R4 — 1.0 điểm)**

---

## 1. Cơ sở Lý thuyết Chuẩn hóa Quan hệ

Cho lược đồ quan hệ $R(U)$ với tập thuộc tính $U$ và tập phụ thuộc hàm $F$ xác định trên $U$:
- **Khóa dự tuyển (Candidate Key – $K$):** Tập con $K \subseteq U$ sao cho bao đóng $K^+_F = U$ và không tồn tại tập con thực sự $K' \subset K$ nào có $(K')^+_F = U$.
- **Thuộc tính khóa (Prime Attribute):** Thuộc tính thuộc về ít nhất một khóa dự tuyển của $R$.
- **Thuộc tính không khóa (Non-prime Attribute):** Thuộc tính không thuộc bất kỳ khóa dự tuyển nào của $R$.
- **Dạng chuẩn 1 (1NF):** Mọi thuộc tính trong $R$ đều mang giá trị nguyên tố (atomic), không có thuộc tính đa trị hoặc thuộc tính lặp.
- **Dạng chuẩn 2 (2NF):** $R$ đạt 1NF và mọi thuộc tính không khóa đều phụ thuộc hàm đầy đủ vào mọi khóa dự tuyển của $R$ (không tồn tại phụ thuộc hàm bộ phận $X \to A$ với $X \subset K$ thực sự và $A$ không khóa).
- **Dạng chuẩn 3 (3NF):** $R$ đạt 2NF và với mọi phụ thuộc hàm không tầm thường $X \to A \in F^+$ (trong đó $A \notin X$), thì hoặc:
  1. $X$ là một siêu khóa (Superkey) của $R$, **hoặc**
  2. $A$ là một thuộc tính khóa (Prime attribute) của $R$.
  *(Nói cách khác, không tồn tại thuộc tính không khóa nào phụ thuộc bắc cầu vào khóa dự tuyển).*

---

## 2. Các Quyết định Thiết kế Loại bỏ Vi phạm Chuẩn hóa Ngay từ Đầu

Trước khi đi vào chứng minh từng bảng, kiến trúc sư CSDL đã chủ động loại bỏ 5 nguy cơ vi phạm 2NF/3NF thường gặp trong bài toán rạp chiếu phim:

1. **Không lưu cột `capacity` (sức chứa) trong `screening_room`:** Sức chứa của phòng bằng tổng số dòng ghế thuộc phòng đó trong bảng `seat`. Việc tính động qua truy vấn/View giúp loại bỏ hoàn toàn dị thường cập nhật khi thêm/bớt ghế.
2. **Không lưu cột `total_amount` trong `booking`:** Tổng tiền đơn vé bằng tổng `unit_price` của các vé thuộc đơn đó. Không lưu dư thừa trong `booking` giúp tránh mâu thuẫn dữ liệu giữa đơn và danh sách vé.
3. **Không lưu cột `room_id` trong `ticket`:** Nếu lưu `room_id` trong `ticket`, sẽ xuất hiện phụ thuộc bắc cầu `ticket_id -> screening_id -> room_id` và `ticket_id -> seat_id -> room_id` (vi phạm 3NF). Thay vào đó, ràng buộc *"ghế trên vé phải thuộc đúng phòng chiếu của suất chiếu"* được cưỡng chế bằng Trigger `trg_ticket_validate_ins`/`upd`.
4. **Bản chất lịch sử của `ticket.unit_price`:** Tại thời điểm phát hành vé, đơn giá được tính từ `screening.base_price + seat_type.surcharge` tại thời điểm đó và chốt cứng vào `ticket.unit_price`. Về sau, khi rạp thay đổi bảng giá hạng ghế (`seat_type.surcharge`) hoặc điều chỉnh giá cơ sở cho các đợt khuyến mãi mới, giá trên các vé đã bán trong quá khứ **không được phép thay đổi**. Do đó, trên tập dữ liệu lịch sử của bảng `ticket`, `unit_price` là thuộc tính phụ thuộc trực tiếp vào khóa `ticket_id` ($\text{ticket\_id} \to \text{unit\_price}$), không phụ thuộc hàm vào giá hiện hành của `seat_type` hay `screening`.
5. **Bản chất vận hành của `screening.end_time` và các cột sinh vật lý (`GENERATED ALWAYS AS ... STORED`):**
   - `screening.end_time` bao gồm thời lượng phim cộng thêm thời gian chiếu trailer quảng cáo và thời gian nhân viên vệ sinh phòng chiếu (có thể linh hoạt 15–30 phút tùy khung giờ cao điểm hay suất IMAX). Vì vậy `end_time` không suy diễn hàm thuần túy từ `(start_time, movie_id)`, mà chỉ chịu ràng buộc miền giá trị $\text{end\_time} \ge \text{start\_time} + \text{duration\_minutes}$.
   - Hai cột `active_seat_key` (trong `ticket`) và `active_success_booking` (trong `payment`) là biểu thức chỉ mục có điều kiện do DBMS tự động tính toán (`GENERATED ALWAYS AS ... STORED`) để thực thi ràng buộc duy nhất một phần (Partial Unique Index) trong MySQL/MariaDB. Người dùng và ứng dụng không thể `INSERT`/`UPDATE` trực tiếp hai cột này nên tuyệt đối không thể xảy ra dị thường cập nhật (Update Anomaly).

---

## 3. Phân tích Phụ thuộc Hàm, Tìm Khóa và Chứng minh 3NF cho Từng Bảng

### 3.1. Quan hệ `cinema_branch`
- **Tập thuộc tính:** $U_1 = \{\text{branch\_id}, \text{branch\_code}, \text{branch\_name}, \text{address}, \text{city}, \text{phone}, \text{active}\}$
- **Tập phụ thuộc hàm $F_1$:**
  - $f_{1.1}: \text{branch\_id} \to \{\text{branch\_code}, \text{branch\_name}, \text{address}, \text{city}, \text{phone}, \text{active}\}$
  - $f_{1.2}: \text{branch\_code} \to \{\text{branch\_id}, \text{branch\_name}, \text{address}, \text{city}, \text{phone}, \text{active}\}$
  - $f_{1.3}: \text{branch\_name} \to \{\text{branch\_id}, \text{branch\_code}, \text{address}, \text{city}, \text{phone}, \text{active}\}$
  - $f_{1.4}: \text{phone} \to \{\text{branch\_id}, \text{branch\_code}, \text{branch\_name}, \text{address}, \text{city}, \text{active}\}$
- **Bao đóng & Khóa dự tuyển:**
  - $(\text{branch\_id})^+ = U_1$; $(\text{branch\_code})^+ = U_1$; $(\text{branch\_name})^+ = U_1$; $(\text{phone})^+ = U_1$.
  - Các khóa dự tuyển (1 thuộc tính): $K_{1.1} = \{\text{branch\_id}\}$ (PK), $K_{1.2} = \{\text{branch\_code}\}$, $K_{1.3} = \{\text{branch\_name}\}$, $K_{1.4} = \{\text{phone}\}$.
- **Kiểm tra dạng chuẩn:**
  - **1NF:** Mọi thuộc tính đều nguyên tố $\Rightarrow$ Đạt **1NF**.
  - **2NF:** Mọi khóa dự tuyển đều gồm 1 thuộc tính duy nhất, không thể có phụ thuộc bộ phận $\Rightarrow$ Đạt **2NF**.
  - **3NF:** Mọi phụ thuộc hàm không tầm thường trong $F_1$ đều có vế trái là một khóa dự tuyển (siêu khóa) $\Rightarrow$ Đạt **3NF** (và đạt cả **BCNF**).

---

### 3.2. Quan hệ `screening_room`
- **Tập thuộc tính:** $U_2 = \{\text{room\_id}, \text{branch\_id}, \text{room\_name}, \text{room\_type}, \text{active}\}$
- **Tập phụ thuộc hàm $F_2$:**
  - $f_{2.1}: \text{room\_id} \to \{\text{branch\_id}, \text{room\_name}, \text{room\_type}, \text{active}\}$
  - $f_{2.2}: \{\text{branch\_id}, \text{room\_name}\} \to \{\text{room\_id}, \text{room\_type}, \text{active}\}$
- **Bao đóng & Khóa dự tuyển:**
  - $(\text{room\_id})^+ = U_2$; $(\{\text{branch\_id}, \text{room\_name}\})^+ = U_2$.
  - Hai khóa dự tuyển: $K_{2.1} = \{\text{room\_id}\}$ (PK) và $K_{2.2} = \{\text{branch\_id}, \text{room\_name}\}$ (UQ).
  - Thuộc tính khóa: $\{\text{room\_id}, \text{branch\_id}, \text{room\_name}\}$; Thuộc tính không khóa: $\{\text{room\_type}, \text{active}\}$.
- **Kiểm tra dạng chuẩn:**
  - **1NF:** Tất cả thuộc tính đều nguyên tố $\Rightarrow$ Đạt **1NF**.
  - **2NF:** Xét khóa phức hợp $K_{2.2} = \{\text{branch\_id}, \text{room\_name}\}$: chỉ mình `branch_id` không xác định được `room_type` hay `active` (một chi nhánh có nhiều phòng khác loại), và chỉ mình `room_name` (VD: `Phòng 01`) cũng không xác định được `room_type` hay `active` vì các chi nhánh khác nhau đều có `Phòng 01`. Do đó các thuộc tính không khóa phụ thuộc đầy đủ vào cả hai khóa dự tuyển $\Rightarrow$ Đạt **2NF**.
  - **3NF:** Cả $f_{2.1}$ và $f_{2.2}$ đều có vế trái là khóa dự tuyển $\Rightarrow$ Đạt **3NF**.

---

### 3.3. Quan hệ `seat_type`
- **Tập thuộc tính:** $U_3 = \{\text{seat\_type\_code}, \text{type\_name}, \text{surcharge}\}$
- **Tập phụ thuộc hàm $F_3$:**
  - $f_{3.1}: \text{seat\_type\_code} \to \{\text{type\_name}, \text{surcharge}\}$
  - $f_{3.2}: \text{type\_name} \to \{\text{seat\_type\_code}, \text{surcharge}\}$
- **Khóa dự tuyển:** $K_{3.1} = \{\text{seat\_type\_code}\}$ (PK), $K_{3.2} = \{\text{type\_name}\}$ (UQ).
- **Kiểm tra dạng chuẩn:** Mọi thuộc tính nguyên tố (1NF), các khóa đều đơn thuộc tính (2NF), mọi FD đều có vế trái là khóa dự tuyển $\Rightarrow$ Đạt **3NF**.

---

### 3.4. Quan hệ `seat`
- **Tập thuộc tính:** $U_4 = \{\text{seat\_id}, \text{room\_id}, \text{seat\_row}, \text{seat\_number}, \text{seat\_type\_code}\}$
- **Tập phụ thuộc hàm $F_4$:**
  - $f_{4.1}: \text{seat\_id} \to \{\text{room\_id}, \text{seat\_row}, \text{seat\_number}, \text{seat\_type\_code}\}$
  - $f_{4.2}: \{\text{room\_id}, \text{seat\_row}, \text{seat\_number}\} \to \{\text{seat\_id}, \text{seat\_type\_code}\}$
- **Bao đóng & Khóa dự tuyển:**
  - $(\text{seat\_id})^+ = U_4$; $(\{\text{room\_id}, \text{seat\_row}, \text{seat\_number}\})^+ = U_4$.
  - Khóa dự tuyển: $K_{4.1} = \{\text{seat\_id}\}$ (PK) và $K_{4.2} = \{\text{room\_id}, \text{seat\_row}, \text{seat\_number}\}$ (UQ).
  - Thuộc tính không khóa duy nhất: `seat_type_code` (vì cấu hình hạng ghế theo hàng có thể khác nhau giữa phòng 2D, 3D và IMAX hoặc ghế đôi ở cuối phòng nên cần đủ bộ `{room_id, seat_row, seat_number}`).
- **Kiểm tra dạng chuẩn:**
  - **1NF:** Giá trị đơn nguyên tố $\Rightarrow$ Đạt **1NF**.
  - **2NF:** `seat_type_code` phụ thuộc hàm đầy đủ vào $K_{4.1}$ và $K_{4.2}$ $\Rightarrow$ Đạt **2NF**.
  - **3NF:** Mọi FD không tầm thường trong $F_4$ đều có vế trái là siêu khóa; bảng `seat` không lưu `surcharge` hay `type_name` nên không có phụ thuộc bắc cầu qua `seat_type_code` $\Rightarrow$ Đạt **3NF**.

---

### 3.5. Quan hệ `movie`
- **Tập thuộc tính:** $U_5 = \{\text{movie\_id}, \text{movie\_code}, \text{title}, \text{duration\_minutes}, \text{release\_date}, \text{age\_rating}, \text{country}, \text{description}\}$
- **Tập phụ thuộc hàm $F_5$:**
  - $f_{5.1}: \text{movie\_id} \to U_5 \setminus \{\text{movie\_id}\}$
  - $f_{5.2}: \text{movie\_code} \to U_5 \setminus \{\text{movie\_code}\}$
- **Khóa dự tuyển:** $K_{5.1} = \{\text{movie\_id}\}$ (PK), $K_{5.2} = \{\text{movie\_code}\}$ (UQ).
- **Kiểm tra dạng chuẩn:** Thể loại phim đa trị đã được tách sang bảng `movie_genre` nên `movie` đạt **1NF**; các khóa đều đơn thuộc tính nên đạt **2NF**; không có phụ thuộc bắc cầu giữa các thuộc tính không khóa $\Rightarrow$ Đạt **3NF**.

---

### 3.6. Quan hệ `genre`
- **Tập thuộc tính:** $U_6 = \{\text{genre\_id}, \text{genre\_name}, \text{description}\}$
- **Tập phụ thuộc hàm $F_6$:**
  - $f_{6.1}: \text{genre\_id} \to \{\text{genre\_name}, \text{description}\}$
  - $f_{6.2}: \text{genre\_name} \to \{\text{genre\_id}, \text{description}\}$
- **Khóa dự tuyển:** $K_{6.1} = \{\text{genre\_id}\}$ (PK), $K_{6.2} = \{\text{genre\_name}\}$ (UQ).
- **Kiểm tra dạng chuẩn:** Mọi FD có vế trái là khóa dự tuyển $\Rightarrow$ Đạt **1NF, 2NF, 3NF**.

---

### 3.7. Quan hệ `movie_genre` (Khóa chính phức hợp)
- **Tập thuộc tính:** $U_7 = \{\text{movie\_id}, \text{genre\_id}\}$
- **Tập phụ thuộc hàm $F_7$:** Chỉ có các phụ thuộc hàm tầm thường ($\{\text{movie\_id}, \text{genre\_id}\} \to \text{movie\_id}, \text{genre\_id}$).
- **Khóa dự tuyển duy nhất:** $K_7 = \{\text{movie\_id}, \text{genre\_id}\}$ (PK phức hợp).
- **Kiểm tra dạng chuẩn:**
  - **1NF:** Mỗi ô chứa đúng một mã nguyên tố $\Rightarrow$ Đạt **1NF**.
  - **2NF & 3NF:** Quan hệ hoàn toàn không có thuộc tính không khóa ($U_7 \setminus K_7 = \varnothing$), do đó không thể tồn tại phụ thuộc bộ phận hay phụ thuộc bắc cầu $\Rightarrow$ Đạt **2NF, 3NF, BCNF**.

---

### 3.8. Quan hệ `screening`
- **Tập thuộc tính:** $U_8 = \{\text{screening\_id}, \text{movie\_id}, \text{room\_id}, \text{start\_time}, \text{end\_time}, \text{base\_price}, \text{status}\}$
- **Tập phụ thuộc hàm $F_8$:**
  - $f_{8.1}: \text{screening\_id} \to \{\text{movie\_id}, \text{room\_id}, \text{start\_time}, \text{end\_time}, \text{base\_price}, \text{status}\}$
- **Khóa dự tuyển:** $K_8 = \{\text{screening\_id}\}$ (PK). *(Lưu ý: một phòng chiếu có thể có một suất bị `CANCELLED` rồi tạo lại suất `OPEN` mới ở cùng khung giờ nên `(room_id, start_time)` không phải khóa cứng trên toàn tập lịch sử mà chỉ là ràng buộc không giao nhau trên các suất `OPEN`).*
- **Kiểm tra dạng chuẩn:**
  - **1NF & 2NF:** Khóa đơn `screening_id`, các thuộc tính nguyên tố $\Rightarrow$ Đạt **1NF, 2NF**.
  - **3NF:** Như đã phân tích tại Mục 2.5, `end_time` bao gồm thời gian quảng cáo và dọn phòng tùy suất chiếu, và `base_price` được ấn định theo khung giờ vàng/ngày lễ cho từng suất chiếu chứ không phụ thuộc hàm vào `movie_id` hay `room_id`. Bảng không lưu `branch_id` hay `title` $\Rightarrow$ Không có phụ thuộc bắc cầu $\Rightarrow$ Đạt **3NF**.

---

### 3.9. Quan hệ `customer`
- **Tập thuộc tính:** $U_9 = \{\text{customer\_id}, \text{full\_name}, \text{phone}, \text{email}, \text{birth\_date}, \text{created\_at}\}$
- **Tập phụ thuộc hàm $F_9$:**
  - $f_{9.1}: \text{customer\_id} \to \{\text{full\_name}, \text{phone}, \text{email}, \text{birth\_date}, \text{created\_at}\}$
  - $f_{9.2}: \text{phone} \to \{\text{customer\_id}, \text{full\_name}, \text{email}, \text{birth\_date}, \text{created\_at}\}$
  - $f_{9.3}: \text{email} \to \{\text{customer\_id}, \text{full\_name}, \text{phone}, \text{birth\_date}, \text{created\_at}\}$
- **Khóa dự tuyển:** $K_{9.1} = \{\text{customer\_id}\}$ (PK), $K_{9.2} = \{\text{phone}\}$ (UQ), $K_{9.3} = \{\text{email}\}$ (UQ).
- **Kiểm tra dạng chuẩn:** Mọi FD không tầm thường đều có vế trái là khóa dự tuyển $\Rightarrow$ Đạt **1NF, 2NF, 3NF**.

---

### 3.10. Quan hệ `employee`
- **Tập thuộc tính:** $U_{10} = \{\text{employee\_id}, \text{branch\_id}, \text{employee\_code}, \text{full\_name}, \text{phone}, \text{role}, \text{hired\_date}\}$
- **Tập phụ thuộc hàm $F_{10}$:**
  - $f_{10.1}: \text{employee\_id} \to \{\text{branch\_id}, \text{employee\_code}, \text{full\_name}, \text{phone}, \text{role}, \text{hired\_date}\}$
  - $f_{10.2}: \text{employee\_code} \to \{\text{employee\_id}, \text{branch\_id}, \text{full\_name}, \text{phone}, \text{role}, \text{hired\_date}\}$
  - $f_{10.3}: \text{phone} \to \{\text{employee\_id}, \text{branch\_id}, \text{employee\_code}, \text{full\_name}, \text{role}, \text{hired\_date}\}$
- **Khóa dự tuyển:** $K_{10.1} = \{\text{employee\_id}\}$ (PK), $K_{10.2} = \{\text{employee\_code}\}$ (UQ), $K_{10.3} = \{\text{phone}\}$ (UQ).
- **Kiểm tra dạng chuẩn:** Bảng không lưu thông tin chi nhánh (`branch_name`, `city`), mọi FD đều xuất phát từ khóa dự tuyển $\Rightarrow$ Đạt **1NF, 2NF, 3NF**.

---

### 3.11. Quan hệ `booking`
- **Tập thuộc tính:** $U_{11} = \{\text{booking\_id}, \text{booking\_code}, \text{screening\_id}, \text{customer\_id}, \text{employee\_id}, \text{booked\_at}, \text{booking\_status}\}$
- **Tập phụ thuộc hàm $F_{11}$:**
  - $f_{11.1}: \text{booking\_id} \to \{\text{booking\_code}, \text{screening\_id}, \text{customer\_id}, \text{employee\_id}, \text{booked\_at}, \text{booking\_status}\}$
  - $f_{11.2}: \text{booking\_code} \to \{\text{booking\_id}, \text{screening\_id}, \text{customer\_id}, \text{employee\_id}, \text{booked\_at}, \text{booking\_status}\}$
- **Khóa dự tuyển:** $K_{11.1} = \{\text{booking\_id}\}$ (PK), $K_{11.2} = \{\text{booking\_code}\}$ (UQ).
- **Kiểm tra dạng chuẩn:** Danh sách ghế và vé đã được tách sang bảng `ticket` (đạt **1NF**); khóa đơn thuộc tính (đạt **2NF**); không lưu tổng tiền hay thông tin khách hàng/phim dư thừa $\Rightarrow$ Đạt **3NF**.

---

### 3.12. Quan hệ `ticket`
- **Tập thuộc tính logic:** $U_{12} = \{\text{ticket\_id}, \text{booking\_id}, \text{screening\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$
- **Tập phụ thuộc hàm $F_{12}$:**
  - $f_{12.1}: \text{ticket\_id} \to \{\text{booking\_id}, \text{screening\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$
  - $f_{12.2}: \text{booking\_id} \to \text{screening\_id}$ *(mỗi đơn đặt vé thuộc một suất chiếu xác định; cột `screening_id` có mặt trong `ticket` để hỗ trợ chỉ mục vật lý chống bán trùng ghế `(screening_id, seat_id)`)*
- **Thảo luận học thuật và chuẩn hóa `ticket`:**
  - Xét phụ thuộc $f_{12.2}: \text{booking\_id} \to \text{screening\_id}$: Trong thiết kế chuẩn hóa thuần túy, `screening_id` có thể suy ra từ `booking_id`. Tuy nhiên, trong hệ quản trị MySQL/MariaDB, chỉ mục `UNIQUE` không thể tham chiếu cột của bảng khác (`booking.screening_id`). Để DBMS trực tiếp cưỡng chế quy tắc cốt lõi *"không bao giờ bán 2 vé hợp lệ cho cùng một ghế trong cùng một suất chiếu"* ở mức chỉ mục phần cứng B-Tree mà không phụ thuộc độc quyền vào mã Java, `(booking_id, screening_id)` tạo thành tham chiếu khóa tổng hợp (composite foreign key reference) sang `booking(booking_id, screening_id)` và được khóa chặt bởi Trigger `NEW.screening_id = booking.screening_id`.
  - Đối với `unit_price`: Đây là giá chốt lịch sử tại thời điểm giao dịch ($\text{ticket\_id} \to \text{unit\_price}$), không phụ thuộc hàm vào giá hiện tại của `screening` hay `seat_type`.
  - Đối với `active_seat_key`: Là cột vật lý `GENERATED ALWAYS AS (...) STORED` do hệ quản trị tự duy trì để đánh chỉ mục duy nhất có điều kiện cho các vé `VALID`.
  - Do đó, mọi thuộc tính nghiệp vụ của `ticket` đều nguyên tố (**1NF**), phụ thuộc đầy đủ vào khóa đơn `ticket_id` (**2NF**), và không phát sinh bất kỳ dị thường thêm/sửa/xóa nào (**3NF**).

---

### 3.13. Quan hệ `payment`
- **Tập thuộc tính logic:** $U_{13} = \{\text{payment\_id}, \text{booking\_id}, \text{amount}, \text{payment\_method}, \text{payment\_status}, \text{paid\_at}, \text{transaction\_ref}\}$
- **Tập phụ thuộc hàm $F_{13}$:**
  - $f_{13.1}: \text{payment\_id} \to \{\text{booking\_id}, \text{amount}, \text{payment\_method}, \text{payment\_status}, \text{paid\_at}, \text{transaction\_ref}\}$
- **Khóa dự tuyển:** $K_{13} = \{\text{payment\_id}\}$ (PK). *(Một `booking_id` có thể có nhiều lần thử thanh toán `FAILED` trước khi `SUCCESS` nên `booking_id` không phải khóa trên toàn bảng `payment`, chỉ duy nhất trên tập con `payment_status = 'SUCCESS'` thông qua cột chỉ mục sinh `active_success_booking`).*
- **Kiểm tra dạng chuẩn:**
  - **1NF:** Mọi thuộc tính đều nguyên tố $\Rightarrow$ Đạt **1NF**.
  - **2NF:** Khóa dự tuyển đơn `payment_id` $\Rightarrow$ Đạt **2NF**.
  - **3NF:** Không có phụ thuộc hàm bắc cầu giữa các thuộc tính không khóa $\Rightarrow$ Đạt **3NF**.

---

## 4. Bảng Tổng kết Kết quả Chuẩn hóa 13 Quan hệ

| STT | Tên Quan hệ | Khóa chính (PK) | Khóa dự tuyển khác (Alternate Keys) | 1NF | 2NF | 3NF | Kết luận |
|---:|---|---|---|---|---|---|---|
| 1 | `cinema_branch` | `branch_id` | `branch_code`, `branch_name`, `phone` | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 2 | `screening_room` | `room_id` | `(branch_id, room_name)` | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 3 | `seat_type` | `seat_type_code` | `type_name` | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 4 | `seat` | `seat_id` | `(room_id, seat_row, seat_number)` | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 5 | `movie` | `movie_id` | `movie_code` | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 6 | `genre` | `genre_id` | `genre_name` | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 7 | `movie_genre` | `(movie_id, genre_id)` | — | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 8 | `screening` | `screening_id` | — | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 9 | `customer` | `customer_id` | `phone`, `email` | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 10 | `employee` | `employee_id` | `employee_code`, `phone` | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 11 | `booking` | `booking_id` | `booking_code` | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 12 | `ticket` | `ticket_id` | `active_seat_key` *(khi VALID)* | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
| 13 | `payment` | `payment_id` | `active_success_booking` *(khi SUCCESS)* | Đạt | Đạt | Đạt | **Đạt chuẩn 3NF** |
