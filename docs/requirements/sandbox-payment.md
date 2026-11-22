# Thanh toán SePay Sandbox (VietQR & Webhook JSON) và ma trận kiểm thử nghiệp vụ

## Phạm vi

Giữ 14 bảng và tập trạng thái hiện có. Demo sandbox chỉ dùng
`cinema_chain_test`, không bật trong `Main` kết nối `cinema_chain`.
Ứng dụng tạo đơn `PENDING`, lưu `payment` có phương thức `TRANSFER` và tiền tố
`SEPAY-`, đồng thời hiển thị hộp thoại mã VietQR động (`https://qr.sepay.vn/img`)
ngay trong cửa sổ Java Swing kèm nội dung chuyển khoản `SP<bookingId>P<paymentId>`.
Chỉ Webhook JSON hợp lệ tại `POST /sandbox/sepay/webhook` được chuyển trạng thái;
không nhập hoặc lưu thông tin thẻ, không phát sinh tiền thật ở chế độ Sandbox.

Nguồn tài liệu giao thức: [SePay Docs](https://docs.sepay.vn).
Bật môi trường thử nghiệm: công tắc **Test Mode** tại `https://my.sepay.vn`.
Các khóa `sepay.bank-code`, `sepay.account-number`, `sepay.auth-mode` và
`sepay.webhook-secret` đặt trong `config/application.properties` đã được Git bỏ qua.
Khi không cấu hình tài khoản Sandbox trên máy chạy: chỉ kiểm chứng giao thức giả lập
cục bộ và CSDL thật, không tuyên bố giao dịch từ máy chủ SePay bên ngoài đã VERIFIED.

## Hợp đồng xử lý

- Hỗ trợ hai chế độ xác thực Webhook tách biệt (không cho phép hạ cấp chéo):
  `HMAC_SHA256` mặc định (`X-SePay-Signature: sha256=<hex>` và `X-SePay-Timestamp`
  ký trên `<timestamp>.<rawBody>`, cửa sổ chống phát lại 300 giây) hoặc `API_KEY`
  (`Authorization: Apikey <secret>`), đối chiếu thời gian hằng.
- Phân tích đối tượng JSON phẳng theo RFC 8259 trước khi tra đơn: chặn khóa JSON
  trùng lặp, cấu trúc lồng nhau, dữ liệu quá 16KB; số tiền VND nguyên dương xử lý
  chính xác bằng `BigDecimal`, không dùng `double`. Múi giờ `Asia/Ho_Chi_Minh`.
- Đối chiếu khớp tài khoản nhận (`accountNumber` hoặc tài khoản ảo `subAccount`),
  chỉ nhận giao dịch tiền vào (`transferType = "in"`), kiểm tra mã `SP<bookingId>P<paymentId>`
  không mâu thuẫn và chặn tái sử dụng một `id` giao dịch SePay cho hai đơn khác nhau.
- Khóa `booking` trước `payment` trong transaction. Webhook lặp cùng mã giao dịch
  không cộng tiền/vé lần nữa; giao dịch trái ngược sau trạng thái kết thúc bị từ chối.
- Hạn giữ ghế 15 phút theo `booked_at`; chỉ tự hết hạn đơn `PENDING` có `payment`
  tiền tố `SEPAY-`. Không tự hủy các đơn chờ thanh toán thủ công tại quầy.
- Webhook thành công đến sau khi hủy/hết hạn không được lấy lại ghế đã bán. Ghi
  dấu `SEPAY-LATE-<id>` vào `transaction_ref` để đối soát; giữ trạng thái đơn
  `CANCELLED` và thanh toán `FAILED`, không ghi doanh thu, không tuyên bố đã hoàn tiền.
- Chưa tích hợp API hoàn tiền tự động: chặn hủy đơn SePay đã `SUCCESS` ở cả tầng dịch vụ
  và Trigger CSDL, yêu cầu đối soát/hoàn khoản ngoài hệ thống. Không giả lập `REFUNDED`
  như thể tiền đã trả thật.
- Máy chủ Webhook HTTP chỉ bind loopback (`127.0.0.1`). Khi dùng công cụ **Mô phỏng giao dịch**
  trên Dashboard SePay Test Mode, người vận hành dùng `SePay CLI` (`sepay webhook listen`)
  hoặc HTTPS tunnel forward về `/sandbox/sepay/webhook`.

## Ma trận kiểm thử bổ sung

| Nhóm | Tình huống cần có | Đáp án độc lập |
|---|---|---|
| BR-01..06 | Phòng/chi nhánh ngừng hoạt động; sát biên thời lượng; lịch nối đuôi, qua nửa đêm, chồng lấn | Thời lượng và giao khoảng tính từ fixture |
| BR-07..10 | Khách không tồn tại, khách vãng lai, ghế sai phòng/null, nhiều ghế trong đơn, hết ghế | Số dòng/khóa từ SQL gốc |
| BR-11..13 | Hủy, bán lại, giá lịch sử, sửa giá vé đã bán, vé của đơn đã hủy, payment sai | Tổng BigDecimal biết trước và trạng thái trước/sau |
| BR-14..16 | Không nhân bản doanh thu, ngày thanh toán khác ngày chiếu, chi nhánh ngừng hoạt động, kỳ rỗng | SQL độc lập + tổng fixture + PDF |
| Đồng thời | Xác nhận vs hủy; nhiều Webhook cùng lúc; cập nhật lịch vs đặt vé | Một kết quả hợp lệ, không vé/khóa/tiền mâu thuẫn |
| Sandbox | HMAC-SHA256/API Key đúng/sai, chống hạ cấp, replay >300s, tamper JSON, duplicate key, sai tiền/tài khoản, `transferType=out`, callback muộn, hộp thoại VietQR | Vector Python độc lập + HTTP loopback + Swing + MySQL thật |
| Phục hồi | SQL/Java/Error, cắt kết nối và kill JVM trước commit | Không còn ghi dở dang, ghế bán lại được |

Các hành vi chưa có quy tắc trong đồ án (tuổi khách so với phim, bán trước giờ
chiếu bao lâu, phân quyền nhân viên theo chi nhánh) phải được ghi là giới hạn,
không tự đặt đáp án rồi nhận là đã kiểm chứng nghiệp vụ.
