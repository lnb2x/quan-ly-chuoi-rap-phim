# Kịch bản bảo vệ BTL Cơ sở dữ liệu nhóm 8

Lớp INT1313-20261-01, giảng viên Nguyễn Quỳnh Chi. Chuẩn bị báo cáo PDF, ERD Chen A1 và Data Diagram A3; mở chương trình Swing trên MySQL 8.4/JDK 21. Không reset database đang dùng.

## Phân bổ 15 phút

| Thời gian | Nội dung |
|---|---|
| 0–2 phút | Bài toán chuỗi rạp; dữ liệu cần lưu và các vai trò nghiệp vụ |
| 2–5 phút | ERD Chen, bản số/tham gia; Data Diagram 14 bảng/17 FK |
| 5–7 phút | Khóa/FD, ticket cũ vi phạm 2NF/3NF; phép tách lossless và bảng lock ghế |
| 7–11 phút | Hai form nhập đúng/sai và tìm kiếm |
| 11–14 phút | Hai báo cáo preview/PDF, người lập/người nhận và SQL đối soát |
| 14–15 phút | Transaction/concurrency, bằng chứng 104 tests và giới hạn phạm vi |

## Chuẩn bị trên máy hiện tại

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/start_mysql84.ps1
.\mvnw.cmd compile exec:java "-Dexec.mainClass=com.cinema.app.Main"
```

Khi chạy ứng dụng, kiểm tra thanh tiêu đề và thanh bên trái (sidebar) để xác nhận chương trình kết nối đúng MySQL 8.4 và tài khoản `cinema_app`. Khi khởi động, chương trình giữ nguyên dữ liệu hiện có trong database (không tự động reset hay nạp lại dữ liệu mẫu).

Ngày 15/11/2026, dữ liệu demo hiện có 161 suất, 162 đơn, 376 vé và doanh thu 35.375.000 VNĐ. Đây là mốc trước người trình bày nhập thêm; tổng sẽ thay đổi theo các thao tác hợp lệ sau đó. Seed test dùng trong ảnh báo cáo là 35.165.000 VNĐ; không trộn hai mốc khi giải thích.

## Hai form nhập và tìm kiếm

1. **Quản lý suất chiếu:** chọn chi nhánh/phòng/phim; đặt một ngày chưa có lịch và khung giờ đủ thời lượng; nhập giá dương, thêm thành công. Sau đó thử cùng phòng/cùng giờ để thấy lỗi BR-06. Thử giá âm hoặc thời lượng ngắn; giải thích service validation và trigger DB. Chọn suất đã có vé và thử đổi phòng để minh họa BR-09.
2. **Đặt vé:** chọn một suất OPEN, chọn khách hoặc khách vãng lai, nhân viên và ghế trống. Nhấn đặt, quan sát đơn/vé/tiền. Thử không chọn ghế hoặc ghế đã giữ để hiện lỗi; nếu demo tranh chấp thật, mở hai cửa sổ cùng suất/ghế. Hủy một đơn tại quầy hợp lệ và kiểm ghế mở lại; không dùng nhánh VNPay để giả hoàn tiền.
3. **Tìm kiếm:** thử ngày sai định dạng để thấy lỗi; nhập ngày có lịch và từ khóa phim; đổi bộ lọc chi nhánh/cờ còn ghế. Xóa bớt lọc để duyệt trang 2, nêu tổng số dòng và 50 dòng/trang.

## Hai báo cáo

- **Doanh thu:** Kế toán lập gửi Giám đốc Kinh doanh, lọc theo ngày thanh toán. Chọn kỳ có dữ liệu, xem tổng chi nhánh/chi tiết ngày, xuất PDF và mở file. Chỉ tính VALID + CONFIRMED + SUCCESS; giữ lịch sử chi nhánh đã đóng và không nhân bản do payment FAILED trước SUCCESS.
- **Bán vé/lấp đầy:** Quản lý chi nhánh lập gửi Ban Điều hành và Quản lý Kinh doanh, lọc theo ngày chiếu. Chỉ rõ sức chứa, vé bán, ghế đang giữ và doanh thu, xuất PDF. PENDING giữ ghế nhưng chưa có doanh thu.
- Đối chiếu SQL trong `docs/sql-features/functional-sql-mapping.md` hoặc các truy vấn read-only trong `database/queries/business_queries.sql`. Không chạy toàn bộ ví dụ có INSERT/UPDATE trên demo; dùng `cinema_chain_test` cho diễn giải SQL ghi và kết thúc ROLLBACK.

## Khi giảng viên hỏi thiết kế

- Không lưu screening_id trong ticket vì booking_id đã xác định screening_id. Ticket có khóa ticket_id và (booking_id,seat_id).
- PK(screening_id,seat_id) ở screening_seat_lock cưỡng chế một ghế hiệu lực/suất; UNIQUE(ticket_id) gắn một lock với một vé. Trigger đồng bộ khi thêm/hủy vé. Giá unit_price là lịch sử, không lấy giá hiện hành tính lại doanh thu.
- Cột generated payment.active_success_booking là cơ chế UNIQUE có điều kiện vật lý của MySQL, không phải khóa dự tuyển logic toàn bảng.
- Phần giao booking_id xác định quan hệ (booking_id,screening_id) nên phép tách ticket là lossless; chứng minh này không tự mở rộng thành phép phân rã quan hệ phổ quát 14 bảng.
- Bộ 104 bài kiểm thử tự động bao phủ các quy tắc nghiệp vụ, giao dịch đồng thời và đối soát báo cáo trên `cinema_chain_test`. Nhánh thanh toán VNPay được cài đặt ở chế độ sandbox phục vụ minh họa luồng thanh toán trực tuyến.

## Kết thúc

Đóng ứng dụng rồi mở lại để xem dữ liệu vừa tạo còn giữ. Khi không còn dùng server cục bộ:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/stop_mysql84.ps1
```
