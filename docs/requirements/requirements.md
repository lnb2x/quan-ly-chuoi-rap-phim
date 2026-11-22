# Phân tích yêu cầu nghiệp vụ và dữ liệu cần lưu trữ

---

## 1. Bối cảnh và Bài toán Quản lý Chuỗi Rạp Chiếu Phim

Hệ thống phục vụ bài toán quản lý chuỗi rạp chiếu phim **CineViet** gồm 4 chi nhánh tại Hà Nội, TP. Hồ Chí Minh, Đà Nẵng và Hải Phòng. Mỗi chi nhánh có nhiều phòng chiếu (`2D`, `3D`, `IMAX`); mỗi phòng gồm 80 ghế cố định (8 hàng `A` đến `H`, mỗi hàng 10 ghế) chia theo 3 hạng: `STD` (ghế thường), `VIP` (ghế VIP) và `COUPLE` (ghế đôi).

Các nghiệp vụ chính chương trình giải quyết gồm:
1. **Lập lịch suất chiếu (Screening Management):** Xếp phim vào từng phòng chiếu theo khung giờ bắt đầu và kết thúc; kiểm tra khoảng giờ chiếu đủ cho thời lượng phim cộng 15 phút dọn phòng và không trùng giờ với suất khác đang mở cùng phòng.
2. **Đặt vé tại quầy (Ticket Booking):** Cho phép chọn ghế trên lưới ghế 8x10 cho khách thành viên hoặc khách vãng lai, tính đơn giá từng vé bằng `base_price + surcharge` tại thời điểm mua và ghi nhận đơn, vé, thanh toán trong cùng một giao dịch. Khóa chính `(screening_id, seat_id)` chặn hai thu ngân bán trùng một ghế trong cùng suất chiếu.
3. **Hủy đơn và mở bán lại ghế (Ticket Cancellation & Seat Resale):** Khi hủy đơn hợp lệ, hệ thống chuyển trạng thái vé sang `CANCELLED` để giữ lịch sử giao dịch và xóa bản ghi giữ chỗ để ghế đó đặt lại được cho khách khác.
4. **Tra cứu suất chiếu (Screening Search):** Tìm theo tên phim, chi nhánh, ngày chiếu và khung giờ bắt đầu; hiển thị số ghế còn trống và chia kết quả 50 dòng mỗi trang.
5. **Tổng hợp báo cáo (Reporting):**
   - **Báo cáo Doanh thu (Revenue Report):** Do **Kế toán** lập, trình **Giám đốc Kinh doanh chuỗi rạp**, tổng hợp doanh thu thực thu từ các vé `VALID` thuộc đơn `CONFIRMED` có giao dịch thanh toán `SUCCESS` (không nhân đôi khi đơn có lần thử thanh toán lỗi trước đó).
   - **Báo cáo Tình hình Bán vé & Lấp đầy (Ticket Sales Report):** Do **Quản lý Chi nhánh** lập, gửi **Ban Điều hành và Quản lý Kinh doanh**, thống kê sức chứa phòng, số vé đã bán, số ghế còn trống, tỷ lệ lấp đầy (%) và doanh thu theo từng suất chiếu.

---

## 2. Phân tích Các Nhóm Người dùng và Phân quyền Nghiệp vụ

| Vai trò (Role) | Mã vai trò | Trách nhiệm chính | Màn hình / Báo cáo sử dụng |
|---|---|---|---|
| **Thu ngân / Nhân viên bán vé** | `CASHIER` | Tra cứu suất chiếu, chọn ghế và lập đơn đặt vé cho khách thành viên hoặc khách vãng lai; thu tiền và xử lý hủy đơn theo quy định. | Màn hình Tra cứu Suất chiếu, Màn hình Đặt vé & Chọn ghế |
| **Quản lý Chi nhánh** | `MANAGER` | Lập và điều chỉnh lịch chiếu tại các phòng chiếu của chi nhánh; lập Báo cáo Tình hình Bán vé gửi Ban Điều hành & Quản lý Kinh doanh. | Màn hình Quản lý Suất chiếu, Màn hình Tra cứu, Báo cáo Tình hình Bán vé |
| **Kế toán Chuỗi rạp** | `ACCOUNTANT` | Đối soát giao dịch thanh toán và doanh thu thực thu theo kỳ hạn, chi nhánh; lập Báo cáo Doanh thu trình Giám đốc Kinh doanh chuỗi rạp. | Báo cáo Doanh thu Chuỗi rạp, Báo cáo Tình hình Bán vé |
| **Giám đốc Kinh doanh & Ban Điều hành** | *(Người nhận báo cáo)* | Xem xét báo cáo định kỳ từ Kế toán và Quản lý Chi nhánh để điều chỉnh lịch chiếu và giá vé giữa các cụm rạp. | Bản xem trước (Preview) & tệp PDF Báo cáo Doanh thu, Báo cáo Tình hình Bán vé |

---

## 3. Phân tích Chi tiết Dữ liệu Cần Lưu trữ (14 Bảng Quan hệ Chuẩn 3NF)

Cơ sở dữ liệu gồm **14 bảng quan hệ** đạt chuẩn **3NF** (tách bảng giữ chỗ ghế khỏi bảng vé để tránh phụ thuộc hàm bắc cầu):

### 3.1. Nhóm dữ liệu Hạ tầng Rạp & Phòng chiếu
1. **`cinema_branch` (Chi nhánh rạp):**
   - **Ý nghĩa:** Lưu thông tin từng cụm rạp trong chuỗi.
   - **Dữ liệu cần lưu:** Mã định danh chi nhánh (`branch_id`), mã nghiệp vụ (`branch_code`, VD: `CN-HN01`), tên chi nhánh (`branch_name`), địa chỉ (`address`), thành phố (`city`), số điện thoại liên hệ (`phone`), trạng thái hoạt động (`active`).
2. **`screening_room` (Phòng chiếu):**
   - **Ý nghĩa:** Lưu các phòng chiếu thuộc từng chi nhánh rạp.
   - **Dữ liệu cần lưu:** Mã định danh phòng (`room_id`), mã chi nhánh sở hữu (`branch_id`), tên phòng (`room_name`, VD: `Phòng 01`), loại công nghệ chiếu (`room_type`: `2D`, `3D`, `IMAX`), trạng thái hoạt động (`active`). Tổng số ghế của phòng không lưu cứng để tránh dị thường cập nhật mà đếm từ bảng `seat`.
3. **`seat_type` (Hạng ghế):**
   - **Ý nghĩa:** Danh mục phân loại hạng ghế và mức phụ thu.
   - **Dữ liệu cần lưu:** Mã hạng ghế (`seat_type_code`: `STD`, `VIP`, `COUPLE`), tên hiển thị tiếng Việt (`type_name`: Ghế Thường, Ghế VIP, Ghế Đôi), mức phụ thu (`surcharge`, VNĐ).
4. **`seat` (Ghế vật lý):**
   - **Ý nghĩa:** Lưu từng vị trí ghế cố định bên trong một phòng chiếu.
   - **Dữ liệu cần lưu:** Mã ghế (`seat_id`), mã phòng chiếu (`room_id`), hàng ghế (`seat_row`: `A`..`H`), số thứ tự ghế trong hàng (`seat_number`: `1`..`10`), mã hạng ghế (`seat_type_code`).

### 3.2. Nhóm dữ liệu Phim, Thể loại & Lịch chiếu
5. **`movie` (Phim điện ảnh):**
   - **Ý nghĩa:** Lưu thông tin các bộ phim chiếu tại chuỗi rạp.
   - **Dữ liệu cần lưu:** Mã định danh phim (`movie_id`), mã phim (`movie_code`, VD: `MV-001`), tựa đề tiếng Việt (`title`), thời lượng tính bằng phút (`duration_minutes`), ngày khởi chiếu (`release_date`), phân loại độ tuổi (`age_rating`: `P`, `K`, `T13`, `T16`, `T18`), quốc gia sản xuất (`country`), mô tả tóm tắt (`description`).
6. **`genre` (Thể loại phim):**
   - **Ý nghĩa:** Danh mục các thể loại điện ảnh (Hành động, Tâm lý, Kinh dị, Hoạt hình, Khoa học viễn tưởng...).
   - **Dữ liệu cần lưu:** Mã thể loại (`genre_id`), tên thể loại (`genre_name`), mô tả (`description`).
7. **`movie_genre` (Phân loại Phim - Thể loại):**
   - **Ý nghĩa:** Quan hệ nhiều - nhiều ($M:N$) cho phép một bộ phim thuộc nhiều thể loại và một thể loại có nhiều bộ phim.
   - **Dữ liệu cần lưu:** Khóa chính ghép (`movie_id`, `genre_id`).
8. **`screening` (Suất chiếu):**
   - **Ý nghĩa:** Một lần trình chiếu cụ thể của một bộ phim tại một phòng chiếu xác định trong một khoảng thời gian.
   - **Dữ liệu cần lưu:** Mã suất chiếu (`screening_id`), mã phim (`movie_id`), mã phòng chiếu (`room_id`), thời điểm bắt đầu (`start_time`), thời điểm kết thúc dự kiến bao gồm quảng cáo và vệ sinh phòng (`end_time`), giá vé cơ sở (`base_price`), trạng thái suất chiếu (`status`: `OPEN`, `CANCELLED`).

### 3.3. Nhóm dữ liệu Khách hàng, Nhân sự, Đặt vé, Khóa ghế & Thanh toán
9. **`customer` (Khách hàng thành viên):**
   - **Ý nghĩa:** Lưu thông tin khách hàng đăng ký thành viên để tra cứu lịch sử đặt vé.
   - **Dữ liệu cần lưu:** Mã khách hàng (`customer_id`), họ và tên (`full_name`), số điện thoại (`phone`), địa chỉ email (`email`), ngày sinh (`birth_date`), thời điểm đăng ký (`created_at`).
10. **`employee` (Nhân viên):**
    - **Ý nghĩa:** Lưu thông tin nhân sự làm việc tại các chi nhánh (Thu ngân, Quản lý chi nhánh, Kế toán).
    - **Dữ liệu cần lưu:** Mã nhân viên (`employee_id`), mã chi nhánh làm việc (`branch_id`), mã nhân sự (`employee_code`), họ và tên (`full_name`), số điện thoại (`phone`), vai trò (`role`: `CASHIER`, `MANAGER`, `ACCOUNTANT`), ngày vào làm (`hired_date`).
11. **`booking` (Đơn đặt vé):**
    - **Ý nghĩa:** Ghi nhận một giao dịch đặt mua vé của khách hàng cho đúng một suất chiếu cụ thể.
    - **Dữ liệu cần lưu:** Mã đơn (`booking_id`), mã đơn hiển thị (`booking_code`, VD: `BK-20261101-0001`), mã suất chiếu (`screening_id`), mã khách hàng (`customer_id`, cho phép `NULL` với khách vãng lai tại quầy), mã nhân viên thực hiện (`employee_id`), thời điểm lập đơn (`booked_at`), trạng thái đơn (`booking_status`: `CONFIRMED`, `PENDING`, `CANCELLED`).
12. **`ticket` (Vé xem phim theo ghế - Chuẩn 3NF):**
    - **Ý nghĩa:** Mỗi dòng lưu một vé gắn với một ghế cụ thể trong một đơn đặt vé (kể cả lịch sử các vé đã hủy `CANCELLED`).
    - **Dữ liệu cần lưu:** Mã vé (`ticket_id`), mã đơn đặt vé (`booking_id`), mã ghế (`seat_id`), đơn giá chốt tại thời điểm xuất vé (`unit_price`), trạng thái vé (`ticket_status`: `VALID`, `CANCELLED`), thời điểm hủy (`cancelled_at`). Không đặt cột `screening_id` trong `ticket` vì `booking_id` đã xác định duy nhất `screening_id` trong bảng `booking`.
13. **`screening_seat_lock` (Khóa ghế hiệu lực theo suất chiếu):**
    - **Ý nghĩa:** Lưu các ghế đang có vé `VALID` trong từng suất chiếu để chặn bán trùng ghế bằng khóa chính CSDL mà không tạo phụ thuộc bắc cầu trên bảng `ticket`.
    - **Dữ liệu cần lưu:** Khóa chính ghép (`screening_id`, `seat_id`) và mã vé đang giữ khóa (`ticket_id`, `UNIQUE`). Khi vé chuyển sang `CANCELLED`, dòng khóa ghế tương ứng được xóa để ghế đó mở bán lại.
14. **`payment` (Giao dịch Thanh toán):**
    - **Ý nghĩa:** Lưu lịch sử các lần thanh toán của đơn đặt vé (gồm cả lần thử thất bại `FAILED` trước khi `SUCCESS`, hoặc hoàn tiền `REFUNDED` khi hủy đơn).
    - **Dữ liệu cần lưu:** Mã thanh toán (`payment_id`), mã đơn đặt vé (`booking_id`), số tiền thanh toán (`amount`), phương thức thanh toán (`payment_method`: `CASH`, `CARD`, `TRANSFER`, `EWALLET`), trạng thái thanh toán (`payment_status`: `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`), thời điểm thanh toán (`paid_at`), mã tham chiếu giao dịch (`transaction_ref`). Trên MySQL 8.4, cột sinh có điều kiện `active_success_booking` giới hạn tối đa 01 giao dịch `SUCCESS` cho mỗi đơn.

---

## 4. Khối lượng Dữ liệu Lưu trữ & Quy mô Khởi tạo (Seed Data)

Bộ dữ liệu mẫu dùng cho kiểm thử và chạy báo cáo gồm:
- **04 Chi nhánh rạp** tại Hà Nội, TP. Hồ Chí Minh, Đà Nẵng và Hải Phòng.
- **12 Phòng chiếu** (mỗi chi nhánh 3 phòng `2D`, `3D`, `IMAX`), tổng cộng **960 ghế vật lý** (mỗi phòng 8 hàng A - H $\times$ 10 ghế = 80 ghế gồm `STD`, `VIP`, `COUPLE`).
- **08 Thể loại phim** và **20 Bộ phim** điện ảnh có phân loại độ tuổi và thời lượng chiếu.
- **12 Nhân viên** và **45 Khách hàng thành viên** (cùng luồng khách vãng lai tại quầy).
- **160 Suất chiếu** không trùng lịch trên 12 phòng chiếu.
- **160 Đơn đặt vé** với **373 Vé xem phim** (**327 khóa ghế hiệu lực** trong `screening_seat_lock` gồm 292 ghế đơn `CONFIRMED` + 35 ghế đơn `PENDING`, cùng 46 vé `CANCELLED` trong đó có 23 ghế hủy được đặt lại) và **175 Giao dịch thanh toán** thuộc 4 trạng thái `SUCCESS`, `PENDING`, `FAILED`, `REFUNDED`.
