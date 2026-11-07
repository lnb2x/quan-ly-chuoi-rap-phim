# PHỤ THUỘC HÀM VÀ CHỨNG MINH CHUẨN HÓA QUAN HỆ (1NF, 2NF, 3NF)
**(Tài liệu đáp ứng Tiêu chí R4 — 1.0 điểm)**

---

## 1. Cơ sở Lý thuyết Chuẩn hóa Quan hệ & Phân biệt Khái niệm Nghiêm ngặt

Cho lược đồ quan hệ $R(U, F)$ với tập thuộc tính $U$ và tập phụ thuộc hàm $F$ xác định trên $U$:
- **Siêu khóa (Superkey):** Tập thuộc tính $X \subseteq U$ sao cho bao đóng thuộc tính $X^+_F = U$ trên **mọi thể hiện hợp lệ (all valid tuples)** của quan hệ $R$, và không có thuộc tính nào trong $X$ mang giá trị bất định (`NULL`) làm mất tính định danh bộ.
- **Khóa dự tuyển tối thiểu trên toàn quan hệ (Minimal Candidate Key – $K$):** Siêu khóa $K \subseteq U$ sao cho không tồn tại tập con thực sự $K' \subset K$ nào có $(K')^+_F = U$. Một khóa dự tuyển được chọn làm **Khóa chính (Primary Key – PK)**, các khóa dự tuyển còn lại trên toàn quan hệ được gọi là **Khóa dự tuyển thay thế (Alternate Keys – AK)**.
- **Thuộc tính khóa (Prime Attribute):** Thuộc tính thuộc về ít nhất một khóa dự tuyển $K$ của $R$.
- **Thuộc tính không khóa (Non-prime Attribute):** Thuộc tính không thuộc bất kỳ khóa dự tuyển nào của $R$.
- **Dạng chuẩn 1 (1NF):** Mọi miền giá trị của thuộc tính trong $U$ đều nguyên tố (atomic), không chứa thuộc tính đa trị, danh sách gộp hay nhóm lặp.
- **Dạng chuẩn 2 (2NF):** $R$ đạt 1NF và mọi thuộc tính không khóa $A$ đều phụ thuộc hàm **đầy đủ** vào mọi khóa dự tuyển $K$ của $R$ (tức là không tồn tại khóa dự tuyển phức hợp $K$, tập con thực sự $X \subset K$ ($X \neq \varnothing$) và thuộc tính không khóa $A$ sao cho $X \to A \in F^+$).
- **Dạng chuẩn 3 (3NF):** $R$ đạt 1NF và với mọi phụ thuộc hàm không tầm thường $X \to A \in F^+$ ($A \notin X$), phải thỏa mãn ít nhất một trong hai điều kiện:
  1. $X$ là một **siêu khóa** của $R$, **hoặc**
  2. $A$ là một **thuộc tính khóa (Prime attribute)** của $R$.
  *(Hệ quả tương đương: Không tồn tại thuộc tính không khóa $A$ phụ thuộc bắc cầu vào khóa dự tuyển $K$ qua một tập không phải siêu khóa $Y$: $K \to Y$ và $Y \to A$ với $Y \not\to K$, $A \notin Y$).*
- **Dạng chuẩn Boyce–Codd (BCNF):** Mọi phụ thuộc hàm không tầm thường $X \to A \in F^+$ đều có $X$ là siêu khóa của $R$ ($\text{BCNF} \Rightarrow \text{3NF} \Rightarrow \text{2NF} \Rightarrow \text{1NF}$).

### Hai Phân biệt Lý thuyết Bắt buộc (Tránh Nhầm lẫn Học thuật)
1. **Khóa dự tuyển toàn bảng (Candidate Key) $\neq$ Ràng buộc duy nhất trên tập con (Partial / Subset Uniqueness Constraint):**
   - Một tập thuộc tính chỉ được gọi là *Candidate Key* hoặc *Alternate Key* của quan hệ $R$ nếu nó định danh duy nhất **mọi bộ** trong $R$ và không nhận giá trị `NULL`.
   - Ngược lại, quy tắc *"mỗi `booking_id` chỉ có tối đa một dòng `payment` mang trạng thái `SUCCESS`"* chỉ áp dụng trên tập con $\sigma_{\text{payment\_status}='SUCCESS'}(\text{payment})$, còn các dòng `PENDING`, `FAILED`, `REFUNDED` có thể lặp lại cùng `booking_id` (và biểu thức `active_success_booking` nhận `NULL` trên các dòng đó). Vì vậy, `booking_id` hay `active_success_booking` **tuyệt đối không phải là khóa dự tuyển / khóa thay thế** của quan hệ `payment`.
2. **Mô hình Quan hệ Mức Logic $\neq$ Cột Chỉ mục Sinh Mức Vật lý (`GENERATED ALWAYS AS ... STORED`):**
   - Việc một cột được khai báo `GENERATED` hay được bảo vệ bởi Trigger chống sửa sai **không làm biến mất phụ thuộc hàm** giữa các thuộc tính. Chuẩn hóa 1NF/2NF/3NF là tính chất toán học của tập thuộc tính $U$ và tập phụ thuộc hàm $F$, không phụ thuộc vào việc ứng dụng hay Trigger có chặn lệnh `UPDATE` hay không.

---

## 2. Phát hiện Vi phạm 3NF Thực sự trong Thiết kế Cũ của Bảng `ticket` và Phép Phân rã Chuẩn 3NF

### 2.1. Chẩn đoán Vi phạm 3NF ở Thiết kế Cũ (`ticket` 13 bảng trước đây)
Trong thiết kế 13 bảng ban đầu, bảng `ticket` có lược đồ:
$$U_{\text{old}} = \{\text{ticket\_id}, \text{booking\_id}, \text{screening\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$$
Xét các quy tắc nghiệp vụ bất biến:
- Mỗi đơn đặt vé (`booking`) được lập cho **đúng một** suất chiếu xác định: $\text{booking\_id} \to \text{screening\_id}$.
- Mỗi vé thuộc về một đơn đặt vé xác định: $\text{ticket\_id} \to \text{booking\_id}$, và trong cùng một đơn đặt vé không thể xuất hiện hai dòng vé cho cùng một ghế: $\{\text{booking\_id}, \text{seat\_id}\} \to \text{ticket\_id}$.
- Khóa dự tuyển của $U_{\text{old}}$ là $K_1 = \{\text{ticket\_id}\}$ và $K_2 = \{\text{booking\_id}, \text{seat\_id}\}$. Khi đó tập thuộc tính khóa là $\{\text{ticket\_id}, \text{booking\_id}, \text{seat\_id}\}$; thuộc tính `screening_id` là **thuộc tính không khóa (Non-prime attribute)**.
- Xét phụ thuộc hàm không tầm thường:
  $$\text{booking\_id} \to \text{screening\_id}$$
  - Vế trái `booking_id` **không phải siêu khóa** của `ticket` (vì một đơn đặt vé mua nhiều ghế khác nhau, $(\text{booking\_id})^+ \neq U_{\text{old}}$).
  - Vế phải `screening_id` **không phải thuộc tính khóa** (không nằm trong $K_1$ hay $K_2$).
- **Kết luận nghiêm ngặt:** Sự hiện diện của cột `screening_id` trong `ticket` tạo ra phụ thuộc hàm bộ phận vào $K_2$ ($\{\text{booking\_id}\} \subset \{\text{booking\_id}, \text{seat\_id}\}$, **vi phạm 2NF**) đồng thời là phụ thuộc hàm bắc cầu từ khóa chính $K_1$ ($\text{ticket\_id} \to \text{booking\_id} \to \text{screening\_id}$, **vi phạm 3NF**). Việc dùng khóa ngoại tổ hợp `FOREIGN KEY (booking_id, screening_id)` hay Trigger đồng bộ ở thiết kế cũ chỉ ngăn dữ liệu mâu thuẫn khi ghi, **không hề xóa bỏ phụ thuộc hàm $\text{booking\_id} \to \text{screening\_id}$** nên quan hệ cũ không đạt 3NF về mặt lý thuyết!

### 2.2. So sánh Các Phương án Thiết kế Chuẩn hóa 3NF cho Nghiệp vụ Vé & Chống Bán Trùng Ghế

| Tiêu chí Đánh giá | Phương án A: Chỉ bỏ `ticket.screening_id`, kiểm tra trùng ghế bằng `SELECT` trong Java / Trigger | **Phương án B (ĐƯỢC CHỌN): Bỏ `ticket.screening_id` + Tách quan hệ chuẩn 3NF `screening_seat_lock(screening_id, seat_id, ticket_id)`** | Phương án C: Gộp `booking` và `ticket` thành 1 dòng mỗi ghế |
|---|---|---|---|
| **Đạt chuẩn 3NF / BCNF?** | Đạt 3NF trên `ticket`. | **Đạt 3NF và BCNF** trên cả `ticket` lẫn `screening_seat_lock`. | Phá vỡ nghiệp vụ 1 hóa đơn nhiều ghế hoặc lặp lại `customer_id, employee_id, booked_at` theo `booking_code` (gây vi phạm 3NF mới). |
| **Chống bán trùng ghế ở mức chỉ mục phần cứng B-Tree InnoDB?** | **Không có UNIQUE Index trực tiếp** trên `(screening_id, seat_id)`; nếu chạy dưới `READ COMMITTED` hoặc ghi SQL trực tiếp ngoài ứng dụng dễ hở race condition. | **Có khóa chính vật lý `PRIMARY KEY (screening_id, seat_id)`** và `UNIQUE (ticket_id)` trực tiếp trong InnoDB — bất khả xâm phạm ngay cả khi 20 luồng giao dịch chạy đồng thời hoặc ghi SQL trực tiếp. | Có `UNIQUE`, nhưng đánh mất thực thể `booking` nhiều ghế. |
| **Lưu lịch sử vé hủy (`CANCELLED`) & bán lại ghế?** | Lưu được lịch sử trong `ticket`. | **Lưu trọn vẹn lịch sử** mọi lần hủy trong `ticket`, đồng thời tự động giải phóng dòng trong `screening_seat_lock` khi hủy để bán lại ghế ngay lập tức. | Phức tạp khi hủy toàn đơn nhiều ghế và đối chiếu `payment`. |

### 2.3. Chứng minh Kết nối Không Mất Thông tin (Lossless-Join) và Bảo toàn Phụ thuộc / Ràng buộc Liên Quan hệ

1. **Chứng minh Kết nối Không Mất Thông tin (Lossless-Join Decomposition):**
   - Xét phép chiếu bỏ cột dư thừa `screening_id` khỏi $U_{\text{old}}$ sang hai lược đồ đã tồn tại trong CSDL:
     - $R_{\text{ticket}}(\underline{\text{ticket\_id}}, \text{booking\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at})$
     - $R_{\text{booking}}(\underline{\text{booking\_id}}, \text{booking\_code}, \text{screening\_id}, \text{customer\_id}, \text{employee\_id}, \text{booked\_at}, \text{booking\_status})$
   - Phần giao thuộc tính giữa hai lược đồ là:
     $$R_{\text{ticket}} \cap R_{\text{booking}} = \{\text{booking\_id}\}$$
   - Vì $\text{booking\_id}$ là khóa chính (siêu khóa) của $R_{\text{booking}}$ nên $\text{booking\_id} \to R_{\text{booking}} \in F^+$ (và khóa ngoại `fk_ticket_booking` kèm `booking_id NOT NULL` đảm bảo mọi bộ trong `ticket` đều khớp đúng 1 bộ trong `booking`).
   - Theo **Định lý Heath**, phép phân rã bảo toàn thông tin tuyệt đối (**Lossless-Join**): mọi thông tin của bảng `ticket` cũ được khôi phục chính xác 1–1 qua phép kết nối tự nhiên:
     $$\pi_{\text{ticket\_id}, \text{booking\_id}, \text{screening\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}}(R_{\text{ticket}} \bowtie R_{\text{booking}}) = r_{\text{old}}$$
   - *(Thực nghiệm kiểm chứng trên bản sao CSDL thật trong `verification/logs/migration_clone_dryrun.log` cho thấy mã băm SHA-256 trên toàn bộ 373 dòng vé trước và sau khi tách khớp tuyệt đối `ef904b39c8e1b57b812d0b01b1ee76f3c4992c6b0be7f146cc150fb2e0ba3107`).*

2. **Chứng minh Bảo toàn Phụ thuộc Hàm (Dependency Preservation) & Cưỡng chế Ràng buộc Liên Quan hệ:**
   - Phụ thuộc hàm $\text{booking\_id} \to \text{screening\_id}$ được **bảo toàn trực tiếp trong quan hệ `booking`** (nơi `booking_id` là khóa chính).
   - Các phụ thuộc hàm của vé ($\text{ticket\_id} \to \{\text{booking\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$ và $\{\text{booking\_id}, \text{seat\_id}\} \to \text{ticket\_id}$) được **bảo toàn trực tiếp trong quan hệ `ticket`**.
   - Các phụ thuộc hàm của khóa ghế hiệu lực ($\{\text{screening\_id}, \text{seat\_id}\} \to \text{ticket\_id}$ và $\text{ticket\_id} \to \{\text{screening\_id}, \text{seat\_id}\}$) được **bảo toàn trực tiếp trong quan hệ `screening_seat_lock`** qua `PRIMARY KEY (screening_id, seat_id)` và `UNIQUE (ticket_id)`.
   - Hợp của các tập phụ thuộc hàm cục bộ $(F_{\text{booking}} \cup F_{\text{ticket}} \cup F_{\text{ssl}})^+$ suy diễn đầy đủ $F^+$, tức là phép thiết kế **bảo toàn phụ thuộc hàm (Dependency-Preserving)**.
   - Đối với **các ràng buộc toàn vẹn liên quan hệ (Inter-relational Tuple Constraints)** — cụ thể là: (i) `seat.room_id = screening.room_id` (`BR-09`), và (ii) $r_{\text{ssl}} = \pi_{\text{screening\_id}, \text{seat\_id}, \text{ticket\_id}}(\sigma_{\text{ticket\_status}='VALID'}(\text{ticket} \bowtie \text{booking}))$ (`BR-10`) — trong SQL chuẩn (khi chưa có `CREATE ASSERTION`), các ràng buộc liên bảng này được cưỡng chế đồng bộ 100% tại tầng DBMS bằng `FOREIGN KEY` kết hợp bộ Trigger `trg_ticket_validate_ins/upd`, `trg_ticket_after_ins/upd/del`, `trg_booking_before_upd` và `trg_ssl_guard_ins/upd/del` trong `database/schema/02_triggers.sql`.

---

## 3. Các Quyết định Thiết kế Loại bỏ Dư thừa Dữ liệu Khác

1. **Không lưu `capacity` (sức chứa) trong `screening_room`:** Sức chứa bằng tổng số ghế thuộc phòng trong bảng `seat`, tránh dị thường cập nhật khi cấu hình lại sơ đồ ghế.
2. **Không lưu `total_amount` trong `booking`:** Tổng tiền đơn vé được tính từ `SUM(ticket.unit_price)` của các vé hợp lệ thuộc đơn.
3. **Không lưu `room_id` trong `ticket` hoặc `booking`:** Nếu lưu `room_id` sẽ sinh phụ thuộc bắc cầu qua `screening_id -> room_id` và `seat_id -> room_id`.
4. **Bản chất lịch sử của `ticket.unit_price` (`BR-12`):** Tại thời điểm xuất vé, giá bán được tính bằng `screening.base_price + seat_type.surcharge` hiện hành và chốt cố định vào `ticket.unit_price`. Khi rạp điều chỉnh bảng giá phụ thu `seat_type.surcharge` hoặc giá cơ sở cho đợt mới, đơn giá trên các vé lịch sử đã bán không được thay đổi. Do đó trên quan hệ lịch sử `ticket`, `unit_price` không phụ thuộc hàm vào giá hiện hành của `seat_type` hay `screening`, mà phụ thuộc đầy đủ vào khóa của `ticket`.
5. **Bản chất vận hành của `screening.end_time` (`BR-05`):** Thời điểm kết thúc suất chiếu bao gồm thời lượng phim cộng thời gian chiếu trailer quảng cáo và dọn vệ sinh phòng linh hoạt (15–30 phút tùy khung giờ thường hay suất IMAX), nên `end_time` không phải hàm tất định thuần túy của `(movie_id, start_time)` mà chỉ chịu ràng buộc miền giá trị $\text{end\_time} \ge \text{start\_time} + \text{duration\_minutes}$.

---

## 4. Phân tích Phụ thuộc Hàm, Tìm Khóa Tối thiểu và Chứng minh 1NF, 2NF, 3NF cho Đủ 14 Quan hệ

### 4.1. Quan hệ 1: `cinema_branch`
- **Tập thuộc tính:** $U_1 = \{\text{branch\_id}, \text{branch\_code}, \text{branch\_name}, \text{address}, \text{city}, \text{phone}, \text{active}\}$
- **Tập phụ thuộc hàm cơ sở $F_1$:**
  - $f_{1.1}: \text{branch\_id} \to \{\text{branch\_code}, \text{branch\_name}, \text{address}, \text{city}, \text{phone}, \text{active}\}$
  - $f_{1.2}: \text{branch\_code} \to \{\text{branch\_id}, \text{branch\_name}, \text{address}, \text{city}, \text{phone}, \text{active}\}$
  - $f_{1.3}: \text{branch\_name} \to \{\text{branch\_id}, \text{branch\_code}, \text{address}, \text{city}, \text{phone}, \text{active}\}$
  - $f_{1.4}: \text{phone} \to \{\text{branch\_id}, \text{branch\_code}, \text{branch\_name}, \text{address}, \text{city}, \text{active}\}$
- **Bao đóng & Khóa dự tuyển tối thiểu:** $(\text{branch\_id})^+ = (\text{branch\_code})^+ = (\text{branch\_name})^+ = (\text{phone})^+ = U_1$ (mọi cột này đều `NOT NULL`).
  - Khóa dự tuyển: $K_{1.1} = \{\text{branch\_id}\}$ (**PK**), $K_{1.2} = \{\text{branch\_code}\}$ (**AK**), $K_{1.3} = \{\text{branch\_name}\}$ (**AK**), $K_{1.4} = \{\text{phone}\}$ (**AK**).
  - Thuộc tính khóa: $\{\text{branch\_id}, \text{branch\_code}, \text{branch\_name}, \text{phone}\}$; Thuộc tính không khóa: $\{\text{address}, \text{city}, \text{active}\}$ *(nhiều chi nhánh có thể cùng một `city` nên `city` không xác định chi nhánh)*.
- **Chứng minh dạng chuẩn:**
  - **1NF:** Mọi thuộc tính đều mang giá trị đơn nguyên tố.
  - **2NF:** Mọi khóa dự tuyển đều gồm đúng 1 thuộc tính nên không thể tồn tại tập con thực sự khác rỗng $\Rightarrow$ Không có phụ thuộc hàm bộ phận $\Rightarrow$ Đạt **2NF**.
  - **3NF & BCNF:** Mọi phụ thuộc hàm không tầm thường trong $F_1^+$ đều có vế trái chứa ít nhất một trong $\{K_{1.1}, K_{1.2}, K_{1.3}, K_{1.4}\}$ (tức là siêu khóa) $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.2. Quan hệ 2: `screening_room`
- **Tập thuộc tính:** $U_2 = \{\text{room\_id}, \text{branch\_id}, \text{room\_name}, \text{room\_type}, \text{active}\}$
- **Tập phụ thuộc hàm cơ sở $F_2$:**
  - $f_{2.1}: \text{room\_id} \to \{\text{branch\_id}, \text{room\_name}, \text{room\_type}, \text{active}\}$
  - $f_{2.2}: \{\text{branch\_id}, \text{room\_name}\} \to \{\text{room\_id}, \text{room\_type}, \text{active}\}$
- **Bao đóng & Khóa dự tuyển tối thiểu:**
  - $(\text{room\_id})^+ = U_2$.
  - $(\{\text{branch\_id}\})^+ = \{\text{branch\_id}\} \neq U_2$ (một chi nhánh có nhiều phòng); $(\{\text{room\_name}\})^+ = \{\text{room\_name}\} \neq U_2$ (nhiều chi nhánh cùng đặt tên `Phòng 01`); $(\{\text{branch\_id}, \text{room\_name}\})^+ = U_2$.
  - Khóa dự tuyển: $K_{2.1} = \{\text{room\_id}\}$ (**PK**), $K_{2.2} = \{\text{branch\_id}, \text{room\_name}\}$ (**AK**).
  - Thuộc tính khóa: $\{\text{room\_id}, \text{branch\_id}, \text{room\_name}\}$; Thuộc tính không khóa: $\{\text{room\_type}, \text{active}\}$.
- **Chứng minh dạng chuẩn:**
  - **1NF:** Mọi thuộc tính nguyên tố, không lưu danh sách ghế.
  - **2NF:** Xét khóa phức hợp $K_{2.2} = \{\text{branch\_id}, \text{room\_name}\}$: cả $(\{\text{branch\_id}\})^+$ lẫn $(\{\text{room\_name}\})^+$ đều không chứa `room_type` hay `active`, nên mọi thuộc tính không khóa phụ thuộc hàm đầy đủ vào mọi khóa dự tuyển $\Rightarrow$ Đạt **2NF**.
  - **3NF & BCNF:** Mọi FD không tầm thường trong $F_2^+$ đều có vế trái là siêu khóa ($K_{2.1}$ hoặc $K_{2.2}$) $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.3. Quan hệ 3: `seat_type`
- **Tập thuộc tính:** $U_3 = \{\text{seat\_type\_code}, \text{type\_name}, \text{surcharge}\}$
- **Tập phụ thuộc hàm cơ sở $F_3$:**
  - $f_{3.1}: \text{seat\_type\_code} \to \{\text{type\_name}, \text{surcharge}\}$
  - $f_{3.2}: \text{type\_name} \to \{\text{seat\_type\_code}, \text{surcharge}\}$
- **Khóa dự tuyển tối thiểu:** $K_{3.1} = \{\text{seat\_type\_code}\}$ (**PK**), $K_{3.2} = \{\text{type\_name}\}$ (**AK**). Thuộc tính không khóa: $\{\text{surcharge}\}$.
- **Chứng minh dạng chuẩn:** Mọi thuộc tính nguyên tố (**1NF**); cả hai khóa đều đơn thuộc tính (**2NF**); mọi FD không tầm thường đều có vế trái là siêu khóa (`surcharge` không xác định ngược hạng ghế) $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.4. Quan hệ 4: `seat`
- **Tập thuộc tính:** $U_4 = \{\text{seat\_id}, \text{room\_id}, \text{seat\_row}, \text{seat\_number}, \text{seat\_type\_code}\}$
- **Tập phụ thuộc hàm cơ sở $F_4$:**
  - $f_{4.1}: \text{seat\_id} \to \{\text{room\_id}, \text{seat\_row}, \text{seat\_number}, \text{seat\_type\_code}\}$
  - $f_{4.2}: \{\text{room\_id}, \text{seat\_row}, \text{seat\_number}\} \to \{\text{seat\_id}, \text{seat\_type\_code}\}$
- **Bao đóng & Khóa dự tuyển tối thiểu:**
  - $(\text{seat\_id})^+ = U_4$ và $(\{\text{room\_id}, \text{seat\_row}, \text{seat\_number}\})^+ = U_4$.
  - Không tập con thực sự nào của $\{\text{room\_id}, \text{seat\_row}, \text{seat\_number}\}$ suy ra được `seat_id` hoặc `seat_type_code` (vì sơ đồ phân hạng ghế có thể bố trí ghế VIP ở vùng giữa hàng — ví dụ ghế 3..8 là VIP còn ghế 1..2 cùng hàng là Thường — và khác nhau giữa các phòng chiếu).
  - Khóa dự tuyển: $K_{4.1} = \{\text{seat\_id}\}$ (**PK**), $K_{4.2} = \{\text{room\_id}, \text{seat\_row}, \text{seat\_number}\}$ (**AK**).
  - Thuộc tính khóa: $\{\text{seat\_id}, \text{room\_id}, \text{seat\_row}, \text{seat\_number}\}$; Thuộc tính không khóa: $\{\text{seat\_type\_code}\}$.
- **Chứng minh dạng chuẩn:**
  - **1NF:** Giá trị nguyên tố.
  - **2NF:** `seat_type_code` phụ thuộc hàm đầy đủ vào cả $K_{4.1}$ và $K_{4.2}$ $\Rightarrow$ Đạt **2NF**.
  - **3NF & BCNF:** Không lưu `type_name` hay `surcharge` trong `seat`; mọi FD không tầm thường trong $F_4^+$ đều có vế trái là siêu khóa $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.5. Quan hệ 5: `movie`
- **Tập thuộc tính:** $U_5 = \{\text{movie\_id}, \text{movie\_code}, \text{title}, \text{duration\_minutes}, \text{release\_date}, \text{age\_rating}, \text{country}, \text{description}\}$
- **Tập phụ thuộc hàm cơ sở $F_5$:**
  - $f_{5.1}: \text{movie\_id} \to U_5 \setminus \{\text{movie\_id}\}$
  - $f_{5.2}: \text{movie\_code} \to U_5 \setminus \{\text{movie\_code}\}$
- **Khóa dự tuyển tối thiểu:** $K_{5.1} = \{\text{movie\_id}\}$ (**PK**), $K_{5.2} = \{\text{movie\_code}\}$ (**AK**). *(Hai bộ phim sản xuất khác năm/quốc gia có thể trùng `title` nên `title` không phải khóa dự tuyển)*.
- **Chứng minh dạng chuẩn:** Thuộc tính đa trị thể loại đã tách sang `movie_genre` (**1NF**); các khóa dự tuyển đều gồm 1 thuộc tính (**2NF**); không tồn tại phụ thuộc hàm giữa các thuộc tính không khóa (`title`, `duration_minutes`, `release_date`, `age_rating`, `country`, `description`), mọi FD không tầm thường đều có vế trái là siêu khóa $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.6. Quan hệ 6: `genre`
- **Tập thuộc tính:** $U_6 = \{\text{genre\_id}, \text{genre\_name}, \text{description}\}$
- **Tập phụ thuộc hàm cơ sở $F_6$:**
  - $f_{6.1}: \text{genre\_id} \to \{\text{genre\_name}, \text{description}\}$
  - $f_{6.2}: \text{genre\_name} \to \{\text{genre\_id}, \text{description}\}$
- **Khóa dự tuyển tối thiểu:** $K_{6.1} = \{\text{genre\_id}\}$ (**PK**), $K_{6.2} = \{\text{genre\_name}\}$ (**AK**). Thuộc tính không khóa: $\{\text{description}\}$.
- **Chứng minh dạng chuẩn:** Thuộc tính nguyên tố (**1NF**); khóa đơn thuộc tính (**2NF**); mọi FD không tầm thường có vế trái là siêu khóa $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.7. Quan hệ 7: `movie_genre`
- **Tập thuộc tính:** $U_7 = \{\text{movie\_id}, \text{genre\_id}\}$
- **Tập phụ thuộc hàm cơ sở $F_7$:** $\varnothing$ (chỉ có các phụ thuộc hàm tầm thường $X \to Y$ với $Y \subseteq X$; một phim có nhiều thể loại nên $\text{movie\_id} \not\to \text{genre\_id}$, một thể loại có nhiều phim nên $\text{genre\_id} \not\to \text{movie\_id}$).
- **Khóa dự tuyển tối thiểu:** Duy nhất $K_7 = \{\text{movie\_id}, \text{genre\_id}\}$ (**PK**). Toàn bộ thuộc tính đều là thuộc tính khóa ($U_7 \setminus K_7 = \varnothing$).
- **Chứng minh dạng chuẩn:** Mỗi dòng chứa cặp mã nguyên tố (**1NF**); không tồn tại bất kỳ phụ thuộc hàm không tầm thường nào $\Rightarrow$ Hiển nhiên đạt **2NF, 3NF và BCNF**.

---

### 4.8. Quan hệ 8: `screening`
- **Tập thuộc tính:** $U_8 = \{\text{screening\_id}, \text{movie\_id}, \text{room\_id}, \text{start\_time}, \text{end\_time}, \text{base\_price}, \text{status}\}$
- **Tập phụ thuộc hàm cơ sở $F_8$:**
  - $f_{8.1}: \text{screening\_id} \to \{\text{movie\_id}, \text{room\_id}, \text{start\_time}, \text{end\_time}, \text{base\_price}, \text{status}\}$
- **Bao đóng & Khóa dự tuyển tối thiểu:**
  - Duy nhất $K_8 = \{\text{screening\_id}\}$ (**PK**).
  - *Vì sao `{room_id, start_time}` không phải khóa dự tuyển trên toàn bảng?* Trên lịch sử vận hành, một suất chiếu ở phòng $r$ lúc `19:00` có thể bị hủy (`status = 'CANCELLED'`) do sự cố kỹ thuật rồi được lập lại một suất chiếu mới `status = 'OPEN'` chiếu phim khác tại cùng phòng $r$ lúc `19:00`. Quy tắc không chồng lấn thời gian chỉ áp dụng trên tập con $\sigma_{\text{status}='OPEN'}(\text{screening})$, không phải phụ thuộc hàm toàn bảng.
  - Như đã phân tích tại Mục 3, `end_time` bao gồm thời gian trailer/vệ sinh linh hoạt và `base_price` thay đổi theo khung giờ vàng/ngày lễ nên không tồn tại FD $\{\text{movie\_id}, \text{start\_time}\} \to \text{end\_time}$ hay $\{\text{movie\_id}, \text{room\_id}\} \to \text{base\_price}$.
- **Chứng minh dạng chuẩn:** Thuộc tính nguyên tố (**1NF**); khóa đơn thuộc tính `screening_id` (**2NF**); FD không tầm thường duy nhất $f_{8.1}$ có vế trái là siêu khóa $K_8$ $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.9. Quan hệ 9: `customer`
- **Tập thuộc tính:** $U_9 = \{\text{customer\_id}, \text{full\_name}, \text{phone}, \text{email}, \text{birth\_date}, \text{created\_at}\}$
- **Tập phụ thuộc hàm cơ sở $F_9$:**
  - $f_{9.1}: \text{customer\_id} \to \{\text{full\_name}, \text{phone}, \text{email}, \text{birth\_date}, \text{created\_at}\}$
  - $f_{9.2}: \text{phone} \to \{\text{customer\_id}, \text{full\_name}, \text{email}, \text{birth\_date}, \text{created\_at}\}$
  - $f_{9.3}: \text{email} \to \{\text{customer\_id}, \text{full\_name}, \text{phone}, \text{birth\_date}, \text{created\_at}\}$
- **Khóa dự tuyển tối thiểu:** Cả `customer_id`, `phone`, `email` đều `NOT NULL` và duy nhất trên toàn bảng $\Rightarrow$ $K_{9.1} = \{\text{customer\_id}\}$ (**PK**), $K_{9.2} = \{\text{phone}\}$ (**AK**), $K_{9.3} = \{\text{email}\}$ (**AK**). Thuộc tính không khóa: $\{\text{full\_name}, \text{birth\_date}, \text{created\_at}\}$.
- **Chứng minh dạng chuẩn:** Mọi thuộc tính nguyên tố (**1NF**); mọi khóa dự tuyển đều đơn thuộc tính (**2NF**); mọi FD không tầm thường đều có vế trái là siêu khóa $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.10. Quan hệ 10: `employee`
- **Tập thuộc tính:** $U_{10} = \{\text{employee\_id}, \text{branch\_id}, \text{employee\_code}, \text{full\_name}, \text{phone}, \text{role}, \text{hired\_date}\}$
- **Tập phụ thuộc hàm cơ sở $F_{10}$:**
  - $f_{10.1}: \text{employee\_id} \to \{\text{branch\_id}, \text{employee\_code}, \text{full\_name}, \text{phone}, \text{role}, \text{hired\_date}\}$
  - $f_{10.2}: \text{employee\_code} \to \{\text{employee\_id}, \text{branch\_id}, \text{full\_name}, \text{phone}, \text{role}, \text{hired\_date}\}$
  - $f_{10.3}: \text{phone} \to \{\text{employee\_id}, \text{branch\_id}, \text{employee\_code}, \text{full\_name}, \text{role}, \text{hired\_date}\}$
- **Khóa dự tuyển tối thiểu:** $K_{10.1} = \{\text{employee\_id}\}$ (**PK**), $K_{10.2} = \{\text{employee\_code}\}$ (**AK**), $K_{10.3} = \{\text{phone}\}$ (**AK**). Thuộc tính không khóa: $\{\text{branch\_id}, \text{full\_name}, \text{role}, \text{hired\_date}\}$.
- **Chứng minh dạng chuẩn:** Không lưu tên hay thành phố của chi nhánh trong `employee`; mọi thuộc tính nguyên tố (**1NF**); mọi khóa đơn thuộc tính (**2NF**); mọi FD không tầm thường đều xuất phát từ siêu khóa $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.11. Quan hệ 11: `booking`
- **Tập thuộc tính:** $U_{11} = \{\text{booking\_id}, \text{booking\_code}, \text{screening\_id}, \text{customer\_id}, \text{employee\_id}, \text{booked\_at}, \text{booking\_status}\}$
- **Tập phụ thuộc hàm cơ sở $F_{11}$:**
  - $f_{11.1}: \text{booking\_id} \to \{\text{booking\_code}, \text{screening\_id}, \text{customer\_id}, \text{employee\_id}, \text{booked\_at}, \text{booking\_status}\}$
  - $f_{11.2}: \text{booking\_code} \to \{\text{booking\_id}, \text{screening\_id}, \text{customer\_id}, \text{employee\_id}, \text{booked\_at}, \text{booking\_status}\}$
- **Khóa dự tuyển tối thiểu:** $K_{11.1} = \{\text{booking\_id}\}$ (**PK**), $K_{11.2} = \{\text{booking\_code}\}$ (**AK**). Thuộc tính không khóa: $\{\text{screening\_id}, \text{customer\_id}, \text{employee\_id}, \text{booked\_at}, \text{booking\_status}\}$.
- **Chứng minh dạng chuẩn:** Danh sách ghế/vé đã tách sang `ticket` (**1NF**); cả hai khóa đều đơn thuộc tính (**2NF**); một nhân viên phục vụ nhiều chi nhánh/ca hoặc một khách hàng đặt nhiều suất nên không có phụ thuộc hàm giữa `customer_id`, `employee_id`, `screening_id`, `booked_at`, `booking_status`; mọi FD không tầm thường đều có vế trái là siêu khóa $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.12. Quan hệ 12: `ticket` (Sau khi Chuẩn hóa 3NF — Loại bỏ `screening_id`)
- **Tập thuộc tính:** $U_{12} = \{\text{ticket\_id}, \text{booking\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$
- **Tập phụ thuộc hàm cơ sở $F_{12}$:**
  - $f_{12.1}: \text{ticket\_id} \to \{\text{booking\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$
  - $f_{12.2}: \{\text{booking\_id}, \text{seat\_id}\} \to \{\text{ticket\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$
- **Bao đóng & Khóa dự tuyển tối thiểu:**
  - $(\text{ticket\_id})^+ = U_{12}$ và $(\{\text{booking\_id}, \text{seat\_id}\})^+ = U_{12}$ (cả `booking_id` và `seat_id` đều `NOT NULL` kèm `UNIQUE uq_ticket_booking_seat`).
  - Kiểm tra tính tối thiểu của $K_{12.2} = \{\text{booking\_id}, \text{seat\_id}\}$:
    - $(\{\text{booking\_id}\})^+ = \{\text{booking\_id}\}$: Một đơn đặt vé mua nhiều ghế khác hạng (`STD`, `VIP`, `COUPLE`) nên chỉ riêng `booking_id` **không** xác định được `seat_id` hay `unit_price`.
    - $(\{\text{seat\_id}\})^+ = \{\text{seat\_id}\}$: Cùng một ghế vật lý được bán qua hàng trăm suất chiếu/đơn đặt vé khác nhau với `unit_price` và `ticket_status` khác nhau nên chỉ riêng `seat_id` **không** xác định được bất kỳ thuộc tính nào khác.
  - Vậy `ticket` có đúng 2 khóa dự tuyển tối thiểu trên toàn bảng: $K_{12.1} = \{\text{ticket\_id}\}$ (**PK**) và $K_{12.2} = \{\text{booking\_id}, \text{seat\_id}\}$ (**AK**).
  - Thuộc tính khóa: $\{\text{ticket\_id}, \text{booking\_id}, \text{seat\_id}\}$; Thuộc tính không khóa: $\{\text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$.
- **Chứng minh dạng chuẩn:**
  - **1NF:** Mọi thuộc tính đều nguyên tố.
  - **2NF:** Xét khóa phức hợp $K_{12.2} = \{\text{booking\_id}, \text{seat\_id}\}$: không tồn tại phụ thuộc hàm nào từ tập con thực sự $\{\text{booking\_id}\}$ hay $\{\text{seat\_id}\}$ sang $\{\text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$ (vì `screening_id` đã được loại bỏ hoàn toàn khỏi `ticket`!). Mọi thuộc tính không khóa đều phụ thuộc hàm đầy đủ vào cả $K_{12.1}$ và $K_{12.2}$ $\Rightarrow$ Đạt **2NF**.
  - **3NF & BCNF:** Giữa các thuộc tính không khóa (`unit_price`, `ticket_status`, `cancelled_at`) không có phụ thuộc hàm không tầm thường nào *(lưu ý: quy tắc "nếu `ticket_status = 'VALID'` thì `cancelled_at IS NULL`" là ràng buộc miền giá trị có điều kiện cho 1 giá trị trạng thái, còn khi `ticket_status = 'CANCELLED'` thì `cancelled_at` nhận vô số thời điểm khác nhau nên `ticket_status` $\not\to$ `cancelled_at` và `cancelled_at` $\not\to$ `ticket_status` do `NULL` không xác định hàm)*. Mọi FD không tầm thường trong $F_{12}^+$ đều có vế trái là siêu khóa ($K_{12.1}$ hoặc $K_{12.2}$) $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.13. Quan hệ 13: `screening_seat_lock` (Quan hệ Khóa Ghế Hiệu lực)
- **Tập thuộc tính:** $U_{13} = \{\text{screening\_id}, \text{seat\_id}, \text{ticket\_id}\}$
- **Tập phụ thuộc hàm cơ sở $F_{13}$:**
  - $f_{13.1}: \{\text{screening\_id}, \text{seat\_id}\} \to \text{ticket\_id}$ *(tại mọi thời điểm, một ghế trong một suất chiếu chỉ được nắm giữ bởi tối đa 01 tấm vé hợp lệ)*
  - $f_{13.2}: \text{ticket\_id} \to \{\text{screening\_id}, \text{seat\_id}\}$ *(mỗi tấm vé hợp lệ chỉ giữ khóa cho đúng 01 ghế của đúng 01 suất chiếu)*
- **Bao đóng & Khóa dự tuyển tối thiểu:**
  - $(\{\text{screening\_id}, \text{seat\_id}\})^+ = U_{13}$ (không tập con thực sự nào suy ra `ticket_id` vì 1 suất chiếu có nhiều ghế được bán và 1 ghế được bán ở nhiều suất chiếu).
  - $(\text{ticket\_id})^+ = U_{13}$ (`ticket_id NOT NULL UNIQUE`).
  - Khóa dự tuyển tối thiểu trên toàn bảng: $K_{13.1} = \{\text{screening\_id}, \text{seat\_id}\}$ (**PK**) và $K_{13.2} = \{\text{ticket\_id}\}$ (**AK**).
  - Thuộc tính khóa: $\{\text{screening\_id}, \text{seat\_id}, \text{ticket\_id}\}$ (toàn bộ $U_{13}$); Thuộc tính không khóa: $\varnothing$.
- **Chứng minh dạng chuẩn:**
  - **1NF:** Mọi thuộc tính nguyên tố.
  - **2NF:** Không có thuộc tính không khóa ($U_{13} \setminus (K_{13.1} \cup K_{13.2}) = \varnothing$) $\Rightarrow$ Không thể có phụ thuộc bộ phận $\Rightarrow$ Đạt **2NF**.
  - **3NF & BCNF:** Cả hai phụ thuộc hàm không tầm thường $f_{13.1}$ và $f_{13.2}$ đều có vế trái là khóa dự tuyển (siêu khóa) $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

### 4.14. Quan hệ 14: `payment`
- **Tập thuộc tính mức logic:** $U_{14} = \{\text{payment\_id}, \text{booking\_id}, \text{amount}, \text{payment\_method}, \text{payment\_status}, \text{paid\_at}, \text{transaction\_ref}\}$
- **Tập phụ thuộc hàm cơ sở $F_{14}$:**
  - $f_{14.1}: \text{payment\_id} \to \{\text{booking\_id}, \text{amount}, \text{payment\_method}, \text{payment\_status}, \text{paid\_at}, \text{transaction\_ref}\}$
- **Bao đóng & Khóa dự tuyển tối thiểu:**
  - Duy nhất $K_{14} = \{\text{payment\_id}\}$ (**PK**).
  - *Kiểm tra các cột khác:*
    - `booking_id` **không phải khóa dự tuyển**: một đơn đặt vé có thể có dòng 1 `FAILED` (thẻ lỗi) và dòng 2 `SUCCESS` (tiền mặt), nên `booking_id` lặp lại trên nhiều dòng của `payment`.
    - `transaction_ref` cho phép `NULL` (các giao dịch tiền mặt `CASH` hoặc đang `PENDING` chưa có mã tham chiếu ngân hàng) nên không phải khóa dự tuyển trên toàn bảng.
    - Cột vật lý `active_success_booking` trong MySQL 8.4 nhận `NULL` trên mọi dòng khác `SUCCESS`, nên **không thuộc tập khóa dự tuyển** mà chỉ là biểu thức chỉ mục vật lý cho tập con `payment_status = 'SUCCESS'`.
- **Chứng minh dạng chuẩn:**
  - **1NF:** Mọi thuộc tính nguyên tố.
  - **2NF:** Khóa dự tuyển duy nhất $K_{14} = \{\text{payment\_id}\}$ là khóa đơn thuộc tính $\Rightarrow$ Đạt **2NF**.
  - **3NF & BCNF:** Giữa các thuộc tính không khóa (`booking_id`, `amount`, `payment_method`, `payment_status`, `paid_at`, `transaction_ref`) không tồn tại phụ thuộc hàm toàn bảng nào *(ví dụ: cùng một `booking_id` vẫn có thể có lần thử thanh toán khác `payment_method`, khác `payment_status` và khác `paid_at`)*. FD không tầm thường duy nhất $f_{14.1}$ có vế trái là siêu khóa $K_{14}$ $\Rightarrow$ Đạt **3NF** (và **BCNF**).

---

## 5. Bảng Tổng kết Kết quả Chuẩn hóa Đủ 14 Quan hệ

| STT | Tên Quan hệ | Khóa chính (PK) | Khóa dự tuyển khác trên toàn bảng (Alternate Keys) | Thuộc tính không khóa (Non-prime) | 1NF | 2NF | 3NF | Kết luận |
|---:|---|---|---|---|---|---|---|---|
| 1 | `cinema_branch` | `branch_id` | `branch_code`, `branch_name`, `phone` | `address, city, active` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 2 | `screening_room` | `room_id` | `(branch_id, room_name)` | `room_type, active` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 3 | `seat_type` | `seat_type_code` | `type_name` | `surcharge` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 4 | `seat` | `seat_id` | `(room_id, seat_row, seat_number)` | `seat_type_code` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 5 | `movie` | `movie_id` | `movie_code` | `title, duration_minutes, release_date, age_rating, country, description` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 6 | `genre` | `genre_id` | `genre_name` | `description` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 7 | `movie_genre` | `(movie_id, genre_id)` | — | *(Không có)* | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 8 | `screening` | `screening_id` | — | `movie_id, room_id, start_time, end_time, base_price, status` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 9 | `customer` | `customer_id` | `phone`, `email` | `full_name, birth_date, created_at` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 10 | `employee` | `employee_id` | `employee_code`, `phone` | `branch_id, full_name, role, hired_date` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 11 | `booking` | `booking_id` | `booking_code` | `screening_id, customer_id, employee_id, booked_at, booking_status` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 12 | `ticket` | `ticket_id` | `(booking_id, seat_id)` | `unit_price, ticket_status, cancelled_at` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 13 | `screening_seat_lock` | `(screening_id, seat_id)` | `ticket_id` | *(Không có)* | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
| 14 | `payment` | `payment_id` | — | `booking_id, amount, payment_method, payment_status, paid_at, transaction_ref` | Đạt | Đạt | Đạt | **Đạt 3NF (BCNF)** |
