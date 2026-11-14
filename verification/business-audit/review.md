# Rà soát thay đổi bằng Ponytail Review

Phạm vi: các file thay đổi trong lượt kiểm thử nghiệp vụ/sandbox, đối chiếu caller
BookingService, repository, trigger, callback HTTP, SwingWorker và bộ test.
Không rà soát lại các chỉnh sửa ERD/DOCX có sẵn của người dùng.
Tải giả định: demo desktop cục bộ, nhiều quầy đặt vé và tối đa 4 IPN được xử lý
đồng thời; không suy diễn kết quả thành tải production.

## Phát hiện trong lượt rà soát

1. Thiếu test cho callback ký đúng nhưng trỏ tới payment thủ công; test cũ chỉ
   kiểm tra mã đơn không tồn tại. Bổ sung trường hợp manual PENDING, yêu cầu IPN
   trả 01 và không chuyển trạng thái.
2. Thiếu test cho callback lặp có mã giao dịch provider khác sau SUCCESS và
   guard runtime trỏ sai schema. Bổ sung yêu cầu trả 99/giữ tiền và từ chối schema
   information_schema trước khi cho chạy sandbox; không ghi DB demo.

Đã bổ sung các kiểm tra trên. Không thấy lỗi chặn demo cục bộ trong source đã
đọc; kết luận thực thi căn cứ XML/log mới, không căn cứ riêng báo cáo này.

Giới hạn: chưa có merchant để chạy VNPay thật; chưa triển khai API refund/query;
chưa đo tải lớn hoặc khôi phục sau mất điện. Không dùng bản demo để nhận tiền thật.
