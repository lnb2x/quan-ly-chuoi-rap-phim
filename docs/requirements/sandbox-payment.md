# Thanh toán VNPay Sandbox và ma trận kiểm thử nghiệp vụ

## Phạm vi

Giữ 14 bảng và tập trạng thái hiện có. Demo sandbox chỉ dùng
`cinema_chain_test`, không bật trong Main kết nối `cinema_chain`.
Ứng dụng tạo đơn PENDING, lưu payment có tiền tố VNPAY, ký URL theo phiên bản
2.1.0. Chỉ IPN hợp lệ được chuyển trạng thái; Return URL không ghi database.
Không nhập hoặc lưu thông tin thẻ. Không hỗ trợ nhận tiền thật.

Nguồn giao thức: [VNPay PAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
Đăng ký merchant: https://sandbox.vnpayment.vn/devreg/.
TmnCode/HashSecret đặt trong `config/application.properties` đã được Git bỏ qua.
Không có merchant: chỉ kiểm chứng được giao thức giả lập và DB thật, không được
tuyên bố giao dịch trên cổng VNPay đã VERIFIED.

## Hợp đồng xử lý

- Ký HMAC-SHA512 trên tên tham số sắp xếp và giá trị URL-encoded; số tiền VND
  nhân 100 bằng BigDecimal, không dùng double. Múi giờ Asia/Ho_Chi_Minh.
- IPN kiểm tra chữ ký trước khi tra đơn; chặn tham số lặp, dữ liệu quá dài,
  sai merchant/reference/amount. SUCCESS đòi cả response và transaction status 00.
- Khóa booking trước payment trong transaction. IPN lặp không cộng tiền/vé lần
  nữa; IPN trái ngược sau trạng thái kết thúc không được kích hoạt lại đơn.
- Hạn giữ ghế 15 phút theo booked_at; chỉ tự hết hạn đơn PENDING có payment
  tiền tố VNPAY. Không tự hủy các đơn chờ thanh toán thủ công.
- Callback thành công đến sau hủy/hết hạn không được lấy lại ghế đã bán. Ghi
  dấu `VNPAY-LATE-<transactionNo>` vào transaction_ref để đối soát; giữ trạng
  thái đơn CANCELLED, không ghi doanh thu, không tuyên bố đã hoàn tiền.
- Chưa tích hợp API refund: chặn hủy tự động đơn VNPay đã SUCCESS, yêu cầu đối
  soát/hoàn tiền tại cổng. Không giả lập REFUNDED như thể tiền đã trả thật.
- Callback HTTP chỉ bind loopback. Khi dùng merchant thật ở sandbox, người vận
  hành cấu hình HTTPS public forward tới đường dẫn IPN và đăng ký với VNPay.

## Ma trận kiểm thử bổ sung

| Nhóm | Tình huống cần có | Đáp án độc lập |
|---|---|---|
| BR-01..06 | Phòng/chi nhánh ngừng hoạt động; sát biên thời lượng; lịch nối đuôi, qua nửa đêm, chồng lấn | Thời lượng và giao khoảng tính từ fixture |
| BR-07..10 | Khách không tồn tại, khách vãng lai, ghế sai phòng/null, nhiều ghế trong đơn, hết ghế | Số dòng/khóa từ SQL gốc |
| BR-11..13 | Hủy, bán lại, giá lịch sử, sửa giá vé đã bán, vé của đơn đã hủy, payment sai | Tổng BigDecimal biết trước và trạng thái trước/sau |
| BR-14..16 | Không nhân bản doanh thu, ngày thanh toán khác ngày chiếu, chi nhánh ngừng hoạt động, kỳ rỗng | SQL độc lập + tổng fixture + PDF |
| Đồng thời | Xác nhận vs hủy; hai IPN cùng lúc; cập nhật lịch vs đặt vé | Một kết quả hợp lệ, không vé/khóa/tiền mâu thuẫn |
| Sandbox | HMAC đúng/sai, tamper, duplicate key, replay, sai tiền/merchant, callback muộn, Return URL | Vector ký độc lập + HTTP loopback + MySQL thật |
| Phục hồi | SQL/Java/Error, cắt kết nối và kill JVM trước commit | Không còn ghi dở dang, ghế bán lại được |

Các hành vi chưa có quy tắc trong đồ án (tuổi khách so với phim, bán trước giờ
chiếu bao lâu, phân quyền nhân viên theo chi nhánh) phải được ghi là giới hạn,
không tự đặt đáp án rồi nhận là đã kiểm chứng nghiệp vụ.
