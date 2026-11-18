# PHỤ THUỘC HÀM VÀ CHỨNG MINH CHUẨN HÓA QUAN HỆ (1NF, 2NF, 3NF)
**(Tài liệu đáp ứng Tiêu chí R4 — 1.0 điểm)**

---

## 1. Cơ sở Lý thuyết Phụ thuộc Hàm & Chuẩn hóa theo Giáo trình TS. Nguyễn Quỳnh Chi

Cho lược đồ quan hệ $R(U, F)$ với tập thuộc tính $U = \{A_1, A_2, \dots, A_n\}$ và tập phụ thuộc hàm $F$ xác định trên $U$ (Slide 6, Slide 7 & Giáo trình *Bài giảng DBMS v1.1*, Chương 4):

### 1.1. Phụ thuộc Hàm, Hệ Tiên đề Armstrong và Các Thuật toán Phủ Tối thiểu
- **Phụ thuộc hàm ($X \to Y$):** $X$ là *đối tượng xác định* (vế trái), $Y$ là *đối tượng hệ quả* (vế phải). $Y$ phụ thuộc hàm vào $X$ nếu mỗi giá trị của $X$ liên hệ tới duy nhất một giá trị của $Y$ trên **mọi thể hiện hợp lệ (bất biến theo thời gian)** của quan hệ $R$. Phụ thuộc hàm $X \to Y$ là *hiển nhiên đúng* khi và chỉ khi $Y \subseteq X$ (được bỏ qua khi xét chuẩn hóa).
- **Bao đóng tập phụ thuộc hàm ($F^+$) và Sáu luật suy diễn (`IR1`–`IR6`):** $F^+ \equiv \{X \to Y \mid F \models X \to Y\}$ được sinh đầy đủ từ **Hệ tiên đề Armstrong** (`IR1`–`IR3`) cùng ba luật dẫn xuất (`IR4`–`IR6`):
  - `IR1` *(Luật phản xạ)*: Nếu $X \supseteq Y$ thì $X \to Y$.
  - `IR2` *(Luật tăng trưởng)*: Nếu $X \to Y$ thì $XZ \to YZ$.
  - `IR3` *(Luật bắc cầu)*: Nếu $X \to Y$ và $Y \to Z$ thì $X \to Z$.
  - `IR4` *(Luật chiếu)*: Nếu $X \to YZ$ thì $X \to Y$ và $X \to Z$.
  - `IR5` *(Luật cộng thêm)*: Nếu $X \to Y$ và $X \to Z$ thì $X \to YZ$.
  - `IR6` *(Luật giả bắc cầu)*: Nếu $X \to Y$ và $YZ \to W$ thì $XZ \to W$.
- **Các thuật toán nền tảng trên tập phụ thuộc hàm (Slide 6, trang 27–47 & PDF trang 145–154):**
  - `Closure(X, F)`: Khởi tạo $X^+ \leftarrow X$; lặp `if W ⊆ X+ then X+ ← X+ ∪ Z` với mọi $W \to Z \in F$ cho tới khi $X^+$ không đổi.
  - `Member(F, X -> Y)`: Trả về `true` nếu $Y \subseteq \text{Closure}(X, F)$, ngược lại `false` (kiểm tra $F \models X \to Y$ mà không cần sinh toàn bộ $F^+$).
  - `Left-Reduce(G)` *(Tối giản trái / Phụ thuộc hàm đầy đủ)*: Loại bỏ thuộc tính dư thừa $A \in X$ khỏi $X \to Y \in F$ nếu `Member(F, (X - {A}) -> Y) = true`.
  - `Nonredundant(G)` *(Phủ không dư thừa)*: Loại bỏ phụ thuộc hàm dư thừa $X \to Y$ khỏi $F$ nếu `Member(F - {X -> Y}, X -> Y) = true`.
  - `MinCover(F)` *(Tìm phủ tối thiểu — thực hiện đúng thứ tự 3 bước)*: (1) Tách mọi vế phải thành thuộc tính đơn $X \to A_1, \dots, X \to A_k$; (2) Chạy `Left-Reduce(G)` trước; (3) Chạy `Nonredundant(G)` sau để thu được tập phụ thuộc hàm tối thiểu tương đương ($G^+ = F^+$).

### 1.2. Khóa của Lược đồ Quan hệ và Các Dạng Chuẩn (`N1NF`, `1NF`, `2NF`, `3NF`, `BCNF`)
- **Siêu khóa (Superkey):** Tập thuộc tính $X \subseteq U$ xác định duy nhất thực thể trong quan hệ ($\text{Closure}(X, F) = U$ trên mọi thể hiện hợp lệ và không nhận `NULL` làm mất tính định danh bộ). Với quan hệ $n$ thuộc tính, số tập con khác rỗng tối đa cần xét theo phương pháp tổng quát là $2^n - 1$ (xét tăng dần lực lượng $1, 2, \dots, n$; Slide 7, trang 2–7).
- **Khóa dự bị / Khóa dự tuyển (Candidate Key – $K$):** Siêu khóa có số thuộc tính nhỏ nhất — không tồn tại tập con thực sự $Y \subset K$ mà $\text{Closure}(Y, F) = U$. Một khóa dự bị được chọn làm **Khóa chính (Primary Key – PK)**; tất cả các khóa dự bị còn lại trở thành **Khóa phụ / Khóa thứ cấp (Alternate Key – AK)** (Slide 7, trang 15).
- **Thuộc tính khóa (Prime Attribute) & Thuộc tính không khóa (Non-prime Attribute):** Thuộc tính khóa là thành viên của ít nhất một khóa dự bị $K$ của $R$; thuộc tính không khóa không thuộc bất kỳ khóa dự bị nào của $R$ (Slide 7, trang 16).
- **Dạng chưa chuẩn hóa (`N1NF`) và Dạng chuẩn 1 (`1NF` — Slide 7, trang 11–14 & PDF trang 158):** Một quan hệ đạt **1NF** khi và chỉ khi thỏa mãn đồng thời **3 điều kiện**:
  1. Mọi giá trị thuộc tính của quan hệ đều ở dạng **nguyên tố** (đơn trị, không thể phân rã được nữa);
  2. **Không có thuộc tính đa trị** hay nhóm lặp;
  3. **Không có thuộc tính dẫn xuất** (thuộc tính tính toán được từ các thuộc tính/quan hệ khác).
- **Dạng chuẩn 2 (`2NF` — Slide 7, trang 17–18):** $R$ đạt **1NF** và mọi thuộc tính không khóa đều **phụ thuộc hàm đầy đủ** (tối giản trái) vào mọi khóa dự bị của $R$ (không tồn tại thuộc tính không khóa $A$ phụ thuộc một phần vào tập con thực sự $X \subset K$, $X \neq \varnothing$).
- **Dạng chuẩn 3 (`3NF` — hai định nghĩa tương đương trên Slide 7, trang 21–22):**
  - *Định nghĩa 1:* $R$ đạt **2NF** và với bất kỳ phụ thuộc hàm không hiển nhiên $X \to A \in F^+$ ($A \notin X$), hoặc $X$ là một **siêu khóa** của $R$, hoặc $A$ là một **thuộc tính khóa** của $R$.
  - *Định nghĩa 2:* $R$ đạt **2NF** và **không có thuộc tính không khóa nào phụ thuộc hàm bắc cầu** vào khóa của $R$ (không tồn tại $Y \subseteq U$ mà $K \to Y$, $Y \not\to K$, $Y \to A$ với $A \notin K \cup Y$).
- **Dạng chuẩn Boyce–Codd (`BCNF` — Slide 7, trang 32–36):** Mọi phụ thuộc hàm không hiển nhiên $X \to A \in F^+$ ($A \notin X$) đều có $X$ là **siêu khóa** của $R$ ($\text{BCNF} \Rightarrow \text{3NF} \Rightarrow \text{2NF} \Rightarrow \text{1NF}$). Theo Slide 7 trang 35–36, phân tách về `3NF` luôn đảm bảo đồng thời cả **(1) Kết nối không mất mát thông tin** và **(2) Bảo toàn các phụ thuộc hàm**; đặc biệt trên lược đồ 14 bảng của hệ thống rạp phim, cả 14 quan hệ đều đạt đồng thời **3NF và BCNF** mà vẫn bảo toàn trọn vẹn các phụ thuộc hàm nội bộ.

### 1.3. Hai Phân biệt Lý thuyết Bắt buộc (Tránh Nhầm lẫn Học thuật)
1. **Khóa dự bị toàn bảng (Candidate Key) $\neq$ Ràng buộc duy nhất trên tập con (Partial / Subset Uniqueness Constraint):**
   - Một tập thuộc tính chỉ được gọi là *Khóa dự bị (Candidate Key)* hoặc *Khóa phụ (Alternate Key)* của quan hệ $R$ nếu nó định danh duy nhất **mọi bộ** trong $R$ và không nhận giá trị `NULL`.
   - Ngược lại, quy tắc *"mỗi `booking_id` chỉ có tối đa một dòng `payment` mang trạng thái `SUCCESS`"* chỉ áp dụng trên tập con $\sigma_{\text{payment\_status}='SUCCESS'}(\text{payment})$, còn các dòng `PENDING`, `FAILED`, `REFUNDED` có thể lặp lại cùng `booking_id` (và biểu thức `active_success_booking` nhận `NULL` trên các dòng đó). Vì vậy, `booking_id` hay `active_success_booking` **tuyệt đối không phải là khóa dự bị / khóa phụ** của quan hệ `payment`.
2. **Mô hình Quan hệ Mức Logic $\neq$ Cột Chỉ mục Sinh Mức Vật lý (`GENERATED ALWAYS AS ... STORED`, Slide 2 trang 9–12):**
   - Việc một cột được khai báo `GENERATED` ở mức trong/vật lý hay được bảo vệ bởi Trigger chống sửa sai **không làm biến mất phụ thuộc hàm** giữa các thuộc tính. Chuẩn hóa `1NF`/`2NF`/`3NF` là tính chất toán học của tập thuộc tính $U$ và tập phụ thuộc hàm $F$, không phụ thuộc vào việc ứng dụng hay Trigger có chặn lệnh `UPDATE` hay không.

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

### 2.3. Thực thi Đầy đủ `MinCover`, `3NF.2–3`, `Lossless` và `Preserve` trên Lược đồ Vé

Áp dụng đúng hệ thống thuật toán trong Bài giảng của TS. Nguyễn Quỳnh Chi lên lược đồ vé cũ $R_{\text{old}}(U_{\text{old}}, F_{\text{old}})$ với thứ tự thuộc tính $(A_1=\text{ticket\_id},\ A_2=\text{booking\_id},\ A_3=\text{screening\_id},\ A_4=\text{seat\_id},\ A_5=\text{unit\_price},\ A_6=\text{ticket\_status},\ A_7=\text{cancelled\_at})$ và tập phụ thuộc hàm ban đầu:
$$F_{\text{old}} = \{\text{ticket\_id} \to \{\text{booking\_id}, \text{screening\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\},\ \{\text{booking\_id}, \text{seat\_id}\} \to \text{ticket\_id},\ \text{booking\_id} \to \text{screening\_id}\}$$

1. **Bước 1 — Tìm phủ tối thiểu bằng `Thuật toán MinCover(F_old)` (Slide 6, trang 47):**
   - **Tách vế phải thành thuộc tính đơn:** Thu được tập $G$ gồm 8 phụ thuộc hàm vế phải đơn: `ticket_id → booking_id`, `ticket_id → screening_id`, `ticket_id → seat_id`, `ticket_id → unit_price`, `ticket_id → ticket_status`, `ticket_id → cancelled_at`, `{booking_id, seat_id} → ticket_id`, `booking_id → screening_id`.
   - **Tối giản vế trái `Left-Reduce(G)`:** Chỉ có FD `{booking_id, seat_id} → ticket_id` có vế trái ghép. Kiểm tra `Member(G, {booking_id} → ticket_id)`: $\text{Closure}(\{\text{booking\_id}\}, G) = \{\text{booking\_id}, \text{screening\_id}\} \not\ni \text{ticket\_id} \Rightarrow \text{false}$; kiểm tra `Member(G, {seat_id} → ticket_id)`: $\text{Closure}(\{\text{seat\_id}\}, G) = \{\text{seat\_id}\} \not\ni \text{ticket\_id} \Rightarrow \text{false}$. Vậy $G$ đã tối giản trái.
   - **Loại bỏ phụ thuộc hàm dư thừa `Nonredundant(G)`:** Xét $f = (\text{ticket\_id} \to \text{screening\_id})$, tính bao đóng trên $G \setminus \{f\}$:
     $$\text{Closure}(\{\text{ticket\_id}\}, G \setminus \{f\}) = \{\text{ticket\_id}, \text{booking\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}, \text{screening\_id}\}$$
     Do $\text{screening\_id}$ vẫn suy diễn được qua chuỗi bắc cầu `ticket_id → booking_id` và `booking_id → screening_id`, `Member(G - {f}, f) = true` nên loại bỏ `ticket_id → screening_id`. Kiểm tra 7 FD còn lại đều cho `Member = false`. Phủ tối thiểu thu được gồm đúng **7 phụ thuộc hàm**:
     $$G_{\min} = \{\text{ticket\_id} \to \text{booking\_id},\ \text{ticket\_id} \to \text{seat\_id},\ \text{ticket\_id} \to \text{unit\_price},\ \text{ticket\_id} \to \text{ticket\_status},\ \text{ticket\_id} \to \text{cancelled\_at},\ \{\text{booking\_id}, \text{seat\_id}\} \to \text{ticket\_id},\ \text{booking\_id} \to \text{screening\_id}\}$$

2. **Bước 2 — Phân tách về chuẩn 3NF theo Slide 7 trang 23–24 & `Thuật toán 3NF.2 / 3NF.3` (PDF trang 175–178):**
   - Theo *Cách 1* (tách thuộc tính phụ thuộc bắc cầu `screening_id` sang bảng riêng với khóa chính là thuộc tính bắc cầu `booking_id`) và *Cách 2 / `Thuật toán 3NF.3`* (gộp các FD cùng vế trái trong $G_{\min}$, hợp nhất hai lược đồ chứa hai khóa dự bị tương đương $K_1 = \{\text{ticket\_id}\}$ và $K_2 = \{\text{booking\_id}, \text{seat\_id}\}$ của $R_{\text{old}}$, đồng thời kiểm tra đã có lược đồ con chứa khóa dự bị của $R_{\text{old}}$), ta thu được phép phân tách $D = \{R_{\text{ticket}}, R_{\text{bs}}\}$ trên đúng tập $U_{\text{old}}$ ($R_{\text{ticket}} \cup R_{\text{bs}} = U_{\text{old}}$):
     - $R_{\text{ticket}}(\underline{\text{ticket\_id}}, \text{booking\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at})$
     - $R_{\text{bs}}(\underline{\text{booking\_id}}, \text{screening\_id})$
   - *(Lưu ý phạm vi hình thức: Không gọi toàn bộ bảng `booking` là thành phần phân tách của $U_{\text{old}}$ vì `booking_code`, `customer_id`, `employee_id`, `booked_at`, `booking_status` không thuộc $U_{\text{old}}$. Trong cài đặt CSDL 14 bảng, $R_{\text{bs}}$ được cung cấp bởi phép chiếu hai cột `booking(booking_id, screening_id)` của bảng `booking`, kết hợp `fk_ticket_booking` `NOT NULL` bảo đảm mỗi vé kết nối tự nhiên đúng một đơn: $\pi_{U_{\text{old}}}(R_{\text{ticket}} * R_{\text{booking}}) = r_{\text{old}}$).*

3. **Bước 3 — Kiểm tra Kết nối Không Mất Thông tin bằng `Thuật toán Lossless(R_old, F_old, D)` (PDF trang 163–165):**
   - **Theo bảng ma trận $T$ kích thước $2 \times 7$:** Khởi tạo $T[i, j] = a_j$ nếu $A_j \in R_i$, ngược lại $T[i, j] = b_{ij}$:

   | Lược đồ con / Trạng thái bảng $T$ | `ticket_id` ($A_1$) | `booking_id` ($A_2$) | `screening_id` ($A_3$) | `seat_id` ($A_4$) | `unit_price` ($A_5$) | `ticket_status` ($A_6$) | `cancelled_at` ($A_7$) |
   |---|---|---|---|---|---|---|---|
   | $R_{\text{ticket}}$ *(khởi tạo)* | $a_1$ | $a_2$ | $b_{13}$ | $a_4$ | $a_5$ | $a_6$ | $a_7$ |
   | $R_{\text{bs}}$ *(khởi tạo)* | $b_{21}$ | $a_2$ | $a_3$ | $b_{24}$ | $b_{25}$ | $b_{26}$ | $b_{27}$ |
   | $R_{\text{ticket}}$ *(sau FD `booking_id → screening_id`)* | **$a_1$** | **$a_2$** | **$a_3$** | **$a_4$** | **$a_5$** | **$a_6$** | **$a_7$** |

   - Áp dụng phụ thuộc hàm $\text{booking\_id} \to \text{screening\_id} \in G_{\min}$: hai hàng $R_{\text{ticket}}$ và $R_{\text{bs}}$ có cùng giá trị $a_2$ tại cột `booking_id`, nên đồng nhất cột `screening_id` ưu tiên ký hiệu $a_3$ ($b_{13} \leftarrow a_3$). Hàng $R_{\text{ticket}}$ trở thành toàn ký hiệu $a_1 \dots a_7$ $\Rightarrow$ `Lossless` trả về **`true`**.
   - **Đối chiếu theo định lý phân tách hai ngôi:** $R_{\text{ticket}} \cap R_{\text{bs}} = \{\text{booking\_id}\}$ và $\{\text{booking\_id}\} \to R_{\text{bs}} \in F_{\text{old}}^+$, nên với mọi thể hiện hợp lệ $r_{\text{old}}$: $\pi_{R_{\text{ticket}}}(r_{\text{old}}) * \pi_{R_{\text{bs}}}(r_{\text{old}}) = r_{\text{old}}$.

4. **Bước 4 — Kiểm tra Bảo toàn Phụ thuộc Hàm bằng `Thuật toán Preserve(D, F_old, X -> Y)` (PDF trang 166–167) & Ràng buộc Liên Quan hệ:**
   - Mọi FD trong $G_{\min}$ đều nằm trọn trong $R_{\text{ticket}}$ hoặc $R_{\text{bs}}$ nên hiển nhiên được bảo toàn. Kiểm tra trực tiếp bằng `Preserve(D, F_old, ticket_id -> screening_id)` đối với FD bắc cầu gốc:
     - Khởi tạo $Z \leftarrow \{\text{ticket\_id}\}$.
     - Vòng lặp 1 trên $R_{\text{ticket}}$: $Z \cap R_{\text{ticket}} = \{\text{ticket\_id}\}$, $\text{Closure}(\{\text{ticket\_id}\}, F_{\text{old}}) \cap R_{\text{ticket}} = R_{\text{ticket}} \Rightarrow Z = \{\text{ticket\_id}, \text{booking\_id}, \text{seat\_id}, \text{unit\_price}, \text{ticket\_status}, \text{cancelled\_at}\}$.
     - Vòng lặp 1 trên $R_{\text{bs}}$: $Z \cap R_{\text{bs}} = \{\text{booking\_id}\}$, $\text{Closure}(\{\text{booking\_id}\}, F_{\text{old}}) \cap R_{\text{bs}} = \{\text{booking\_id}, \text{screening\_id}\} \Rightarrow Z = U_{\text{old}}$.
     - Vì $\{\text{screening\_id}\} \subseteq Z$, `Preserve` trả về **`true`**. (Đã kiểm chứng tự động bao đóng trên toàn bộ $2^7 = 128$ tập con của $U_{\text{old}}$ trong `tools/verify_erd.py`).
   - **Phân biệt rõ với quan hệ bổ sung và ràng buộc liên bảng:** `screening_seat_lock` là quan hệ bổ sung biểu diễn thực thể kết hợp phân bổ ghế đang hiệu lực (FD nội bộ chứng minh tại Mục 4.13), không phải phép chiếu của toàn bộ lịch sử vé. Hai điều kiện liên quan hệ — (i) `seat.room_id = screening.room_id` (`BR-09`) và (ii) $r_{\text{ssl}} = \pi_{\text{screening\_id},\text{seat\_id},\text{ticket\_id}}(\sigma_{\text{ticket\_status}='VALID'}(\text{ticket} * \text{booking}))$ (`BR-10`) — được cưỡng chế bằng FK, `PRIMARY KEY (screening_id, seat_id)`, `UNIQUE (ticket_id)` và bộ trigger đồng bộ trong `database/schema/02_triggers.sql`.

---

## 3. Các Quyết định Thiết kế Loại bỏ Thuộc tính Dẫn xuất (Đảm bảo 1NF theo Slide 7 trang 14) & Dư thừa Dữ liệu

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
