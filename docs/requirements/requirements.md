# PHÂN TÍCH YÊU CẦU NGHIỆP VỤ VÀ DỮ LIỆU CẦN LƯU TRỮ
**(Tài liệu đáp ứng Tiêu chí R1 — 1.0 điểm)**

---

## 1. Bối cảnh và Bài toán Quản lý Chuỗi Rạp Chiếu Phim

Doanh nghiệp **CineViet Cinema Chain** vận hành một chuỗi rạp chiếu phim hiện đại tại nhiều tỉnh/thành phố lớn (Hà Nội, TP. Hồ Chí Minh, Đà Nẵng, Hải Phòng...). Mỗi chi nhánh rạp quản lý nhiều phòng chiếu tiêu chuẩn khác nhau (`2D`, `3D`, `IMAX`), mỗi phòng chiếu có sơ đồ ghế vật lý cố định phân chia theo hạng ghế (`STD` – Ghế thường, `VIP` – Ghế VIP, `COUPLE` – Ghế đôi).

Hàng ngày, hệ thống phải xử lý đồng thời các quy trình nghiệp vụ cốt lõi:
1. **Quản lý danh mục phim và lập lịch chiếu (Screening Management):** Phân bổ phim vào từng phòng chiếu thuộc từng chi nhánh theo khung giờ cụ thể, đảm bảo thời lượng suất chiếu đủ cho thời lượng phim cộng thời gian dọn phòng/quảng cáo và tuyệt đối không chồng lấn lịch chiếu trong cùng một phòng.
2. **Bán vé và đặt chỗ theo sơ đồ ghế thời gian thực (Ticket Booking):** Phục vụ cả khách hàng thành viên lẫn khách vãng lai tại quầy. Hệ thống phải hiển thị trực quan ghế trống/ghế đã bán, tính đúng đơn giá vé từng ghế (giá cơ sở của suất chiếu + phụ thu hạng ghế tại thời điểm mua), xử lý thanh toán nguyên tử và ngăn chặn tuyệt đối việc một ghế bị bán hai lần cho cùng một suất chiếu ngay cả khi có nhiều nhân viên thu ngân thao tác đồng thời.
3. **Xử lý hủy vé, hoàn tiền và mở bán lại ghế (Ticket Cancellation & Seat Resale):** Khi đơn đặt vé hoặc vé bị hủy hợp lệ, khóa giữ ghế đang hiệu lực phải được giải phóng ngay để có thể bán lại cho khách hàng khác mà vẫn giữ nguyên lịch sử vé đã hủy phục vụ kiểm toán.
4. **Tra cứu lịch chiếu và kiểm tra ghế trống (Screening Search):** Cho phép nhân viên và khách hàng tìm kiếm nhanh các suất chiếu theo tên phim, chi nhánh, ngày chiếu, khoảng thời gian và xem ngay số lượng ghế còn trống với phân trang tường minh (không cắt ngầm kết quả).
5. **Tổng hợp báo cáo quản trị và tài chính (Business & Financial Reporting):**
   - **Báo cáo Doanh thu (Revenue Report):** Do **Kế toán** lập, trình **Giám đốc Kinh doanh chuỗi rạp**, tổng hợp doanh thu thực thu (chỉ tính các vé hợp lệ `VALID` thuộc các đơn `CONFIRMED` đã có giao dịch thanh toán thành công `SUCCESS`), không bị nhân bản dữ liệu khi một đơn có nhiều lần thử thanh toán.
   - **Báo cáo Tình hình Bán vé & Lấp đầy (Ticket Sales Report):** Do **Quản lý Chi nhánh** lập, gửi **Ban Điều hành và Quản lý Kinh doanh**, thống kê số vé bán ra, sức chứa phòng, số ghế trống, tỷ lệ lấp đầy (%) và doanh thu theo từng suất chiếu.

---

## 2. Phân tích Các Nhóm Người dùng và Phân quyền Nghiệp vụ

| Vai trò (Role) | Mã vai trò | Trách nhiệm chính | Màn hình / Báo cáo sử dụng |
|---|---|---|---|
| **Thu ngân / Nhân viên bán vé** | `CASHIER` | Tra cứu suất chiếu, tư vấn chỗ ngồi, lập đơn đặt vé cho khách thành viên hoặc khách vãng lai, thu tiền và xử lý hủy vé theo quy định. | Màn hình Tra cứu Suất chiếu, Màn hình Đặt vé & Chọn ghế |
| **Quản lý Chi nhánh** | `MANAGER` | Lập và điều chỉnh lịch chiếu tại các phòng chiếu của chi nhánh, theo dõi tỷ lệ lấp đầy phòng chiếu và lập Báo cáo Tình hình Bán vé gửi Ban Điều hành & Quản lý Kinh doanh. | Màn hình Quản lý Suất chiếu, Màn hình Tra cứu, Báo cáo Tình hình Bán vé |
| **Kế toán Chuỗi rạp** | `ACCOUNTANT` | Kiểm soát giao dịch thanh toán, đối soát doanh thu thực tế theo ngày/kỳ hạn và chi nhánh, lập Báo cáo Doanh thu trình Giám đốc Kinh doanh chuỗi rạp. | Báo cáo Doanh thu Chuỗi rạp, Báo cáo Tình hình Bán vé |
| **Giám đốc Kinh doanh & Ban Điều hành** | *(Người nhận báo cáo)* | Nhận và phê duyệt các báo cáo định kỳ từ Kế toán và Quản lý Chi nhánh để ra quyết định phân bổ suất chiếu, điều chỉnh giá vé và chiến lược khai thác. | Bản xem trên màn hình (Preview) & tệp PDF Báo cáo Doanh thu, Báo cáo Tình hình Bán vé |

---

## 3. Phân tích Chi tiết Dữ liệu Cần Lưu trữ (14 Bảng Quan hệ Chuẩn 3NF)

Để đảm bảo tính toàn vẹn dữ liệu, loại bỏ hoàn toàn phụ thuộc bắc cầu và đạt chuẩn **3NF nghiêm ngặt**, hệ thống tổ chức lưu trữ thành **14 bảng quan hệ** sau đây:

### 3.1. Nhóm dữ liệu Hạ tầng Rạp & Phòng chiếu
1. **`cinema_branch` (Chi nhánh rạp):**
   - **Ý nghĩa:** Lưu thông tin từng cụm rạp trong chuỗi.
   - **Dữ liệu cần lưu:** Mã định danh chi nhánh (`branch_id`), mã nghiệp vụ (`branch_code`, VD: `CN-HN01`), tên chi nhánh (`branch_name`), địa chỉ (`address`), thành phố (`city`), số điện thoại liên hệ (`phone`), trạng thái hoạt động (`active`).
2. **`screening_room` (Phòng chiếu):**
   - **Ý nghĩa:** Lưu các phòng chiếu thuộc từng chi nhánh rạp.
   - **Dữ liệu cần lưu:** Mã định danh phòng (`room_id`), mã chi nhánh sở hữu (`branch_id`), tên phòng (`room_name`, VD: `Phòng 01`), loại công nghệ chiếu (`room_type`: `2D`, `3D`, `IMAX`), trạng thái hoạt động (`active`). *Lưu ý chuẩn hóa: Tổng số ghế của phòng không lưu cứng để tránh dị thường cập nhật mà được tính tự động từ bảng `seat`.*
3. **`seat_type` (Hạng ghế):**
   - **Ý nghĩa:** Danh mục phân loại hạng ghế và mức phụ thu tiêu chuẩn.
   - **Dữ liệu cần lưu:** Mã hạng ghế (`seat_type_code`: `STD`, `VIP`, `COUPLE`), tên hiển thị tiếng Việt (`type_name`: Ghế Thường, Ghế VIP, Ghế Đôi), mức phụ thu chuẩn (`surcharge`, VNĐ).
4. **`seat` (Ghế vật lý):**
   - **Ý nghĩa:** Lưu từng vị trí ghế vật lý cố định bên trong một phòng chiếu.
   - **Dữ liệu cần lưu:** Mã ghế (`seat_id`), mã phòng chiếu (`room_id`), hàng ghế (`seat_row`: `A`..`H`), số thứ tự ghế trong hàng (`seat_number`: `1`..`10`), mã hạng ghế (`seat_type_code`).

### 3.2. Nhóm dữ liệu Phim, Thể loại & Lịch chiếu
5. **`movie` (Phim điện ảnh):**
   - **Ý nghĩa:** Lưu thông tin các bộ phim được khai thác tại chuỗi rạp.
   - **Dữ liệu cần lưu:** Mã định danh phim (`movie_id`), mã phim (`movie_code`, VD: `MV-001`), tựa đề tiếng Việt (`title`), thời lượng chiếu tính bằng phút (`duration_minutes`), ngày khởi chiếu (`release_date`), phân loại độ tuổi (`age_rating`: `P`, `K`, `T13`, `T16`, `T18`), quốc gia sản xuất (`country`), mô tả tóm tắt (`description`).
6. **`genre` (Thể loại phim):**
   - **Ý nghĩa:** Danh mục các thể loại điện ảnh (Hành động, Tâm lý, Kinh dị, Hoạt hình, Khoa học viễn tưởng...).
   - **Dữ liệu cần lưu:** Mã thể loại (`genre_id`), tên thể loại (`genre_name`), mô tả (`description`).
7. **`movie_genre` (Phân loại Phim – Thể loại):**
   - **Ý nghĩa:** Quan hệ nhiều – nhiều ($M:N$) cho phép một bộ phim thuộc nhiều thể loại và một thể loại có nhiều bộ phim.
   - **Dữ liệu cần lưu:** Khóa chính phức hợp (`movie_id`, `genre_id`).
8. **`screening` (Suất chiếu):**
   - **Ý nghĩa:** Một lần trình chiếu cụ thể của một bộ phim tại một phòng chiếu xác định trong một khoảng thời gian.
   - **Dữ liệu cần lưu:** Mã suất chiếu (`screening_id`), mã phim (`movie_id`), mã phòng chiếu (`room_id`), thời điểm bắt đầu (`start_time`), thời điểm kết thúc dự kiến bao gồm quảng cáo và vệ sinh phòng (`end_time`), giá vé cơ sở của suất chiếu (`base_price`), trạng thái suất chiếu (`status`: `OPEN`, `CANCELLED`).

### 3.3. Nhóm dữ liệu Khách hàng, Nhân sự, Đặt vé, Khóa ghế & Thanh toán
9. **`customer` (Khách hàng thành viên):**
   - **Ý nghĩa:** Lưu thông tin khách hàng đăng ký thành viên để tích lũy lịch sử đặt vé và chăm sóc khách hàng.
   - **Dữ liệu cần lưu:** Mã khách hàng (`customer_id`), họ và tên (`full_name`), số điện thoại (`phone`), địa chỉ email (`email`), ngày sinh (`birth_date`), thời điểm đăng ký (`created_at`).
10. **`employee` (Nhân viên):**
    - **Ý nghĩa:** Lưu thông tin nhân sự làm việc tại các chi nhánh (Thu ngân, Quản lý chi nhánh, Kế toán).
    - **Dữ liệu cần lưu:** Mã nhân viên (`employee_id`), mã chi nhánh làm việc (`branch_id`), mã nhân sự (`employee_code`), họ và tên (`full_name`), số điện thoại (`phone`), vai trò (`role`: `CASHIER`, `MANAGER`, `ACCOUNTANT`), ngày vào làm (`hired_date`).
11. **`booking` (Đơn đặt vé):**
    - **Ý nghĩa:** Ghi nhận một giao dịch đặt mua vé của khách hàng cho đúng một suất chiếu cụ thể.
    - **Dữ liệu cần lưu:** Mã đơn (`booking_id`), mã đơn hiển thị (`booking_code`, VD: `BK-20261101-0001`), mã suất chiếu (`screening_id`), mã khách hàng (`customer_id` — cho phép `NULL` đối với quy trình **Khách vãng lai tại quầy**), mã nhân viên thực hiện (`employee_id`), thời điểm lập đơn (`booked_at`), trạng thái đơn (`booking_status`: `CONFIRMED`, `PENDING`, `CANCELLED`).
12. **`ticket` (Vé xem phim chi tiết theo ghế — Chuẩn 3NF):**
    - **Ý nghĩa:** Mỗi dòng lưu một vé gắn với một ghế cụ thể trong một đơn đặt vé (bao gồm cả lịch sử các vé đã hủy `CANCELLED`).
    - **Dữ liệu cần lưu:** Mã vé (`ticket_id`), mã đơn đặt vé (`booking_id`), mã ghế (`seat_id`), đơn giá bán thực tế chốt tại thời điểm xuất vé (`unit_price`), trạng thái vé (`ticket_status`: `VALID`, `CANCELLED`), thời điểm hủy (`cancelled_at`). *Lưu ý chuẩn hóa 3NF: Không lưu dư thừa `screening_id` trong `ticket` vì mỗi `booking_id` đã xác định duy nhất một `screening_id` trong bảng `booking`.*
13. **`screening_seat_lock` (Khóa ghế hiệu lực theo suất chiếu):**
    - **Ý nghĩa:** Lưu danh sách các ghế đang được cấp phát hợp lệ (`VALID`) trong từng suất chiếu để cưỡng chế quy tắc chống bán trùng ghế ngay ở mức khóa chính CSDL mà không làm vi phạm 3NF trên bảng `ticket`.
    - **Dữ liệu cần lưu:** Khóa chính phức hợp (`screening_id`, `seat_id`) và mã vé đang giữ khóa (`ticket_id` — duy nhất `UNIQUE`). Khi vé bị hủy (`CANCELLED`), bản ghi khóa ghế tương ứng tự động được giải phóng để cho phép bán lại ghế đó cho đơn mới.
14. **`payment` (Giao dịch Thanh toán):**
    - **Ý nghĩa:** Lưu lịch sử các lần thanh toán gắn với một đơn đặt vé (cho phép ghi nhận cả các lần thanh toán thất bại `FAILED` trước khi thanh toán thành công `SUCCESS`, hoặc hoàn tiền `REFUNDED` khi hủy đơn).
    - **Dữ liệu cần lưu:** Mã thanh toán (`payment_id`), mã đơn đặt vé (`booking_id`), số tiền thanh toán (`amount`), phương thức thanh toán (`payment_method`: `CASH`, `CARD`, `TRANSFER`, `EWALLET`), trạng thái thanh toán (`payment_status`: `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`), thời điểm thanh toán (`paid_at`), mã tham chiếu giao dịch (`transaction_ref`). Ở tầng cài đặt vật lý MySQL 8.4, một cột chỉ mục sinh có điều kiện (`active_success_booking`) được dùng để cưỡng chế tối đa 01 giao dịch `SUCCESS` trên mỗi đơn đặt vé.

---

## 4. Khối lượng Dữ liệu Lưu trữ & Quy mô Khởi tạo (Seed Data Scale)

Để phản ánh chân thực hoạt động của chuỗi rạp và phục vụ kiểm chứng các báo cáo quản trị, cơ sở dữ liệu kiểm thử được khởi tạo tất định với quy mô:
- **04 Chi nhánh rạp** tại Hà Nội, TP. Hồ Chí Minh, Đà Nẵng và Hải Phòng.
- **12 Phòng chiếu** (mỗi chi nhánh 3 phòng gồm `2D`, `3D`, `IMAX`), tổng cộng **960 ghế vật lý** (mỗi phòng 8 hàng A–H $\times$ 10 ghế = 80 ghế phân bổ `STD`, `VIP`, `COUPLE`).
- **08 Thể loại phim** và **20 Bộ phim** điện ảnh có đầy đủ phân loại độ tuổi, thời lượng thực tế.
- **12 Nhân viên** (thu ngân, quản lý chi nhánh, kế toán) và **45 Khách hàng thành viên** (cùng quy trình khách vãng lai).
- **160 Suất chiếu** phân bổ hợp lệ không trùng lịch trên 12 phòng chiếu.
- **160 Đơn đặt vé** với **373 Vé xem phim** (**327 khóa ghế hiệu lực** trong `screening_seat_lock` gồm 292 ghế đơn `CONFIRMED` + 35 ghế đơn `PENDING`, cùng 46 vé `CANCELLED` trong đó có 23 ghế hủy được bán lại hợp lệ) và **175 Giao dịch thanh toán** bao phủ toàn bộ các kịch bản: thanh toán thành công (`SUCCESS`), đang chờ (`PENDING`), thất bại (`FAILED`), hoàn tiền khi hủy (`REFUNDED`), đơn có thử lại thanh toán sau khi lỗi thẻ, và ghế bị hủy được bán lại thành công cho khách mới.
