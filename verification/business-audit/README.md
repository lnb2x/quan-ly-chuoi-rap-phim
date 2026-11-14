# Kiểm thử nghiệp vụ chuyên sâu và VNPay Sandbox

## Phần đã kiểm tra

- `BusinessRulesIT`: 17 test bổ sung về ranh giới thời lượng, lịch nối đuôi/qua
  đêm, SQL đồng thời có snapshot cũ, trạng thái chi nhánh, giá lịch sử, ghế sai,
  dữ liệu null, hủy/hoàn tiền, SQL trực tiếp và báo cáo.
- `VnpayGatewayTest`: 10 test giao thức, có vector HMAC tính độc lập bằng Python.
- `SandboxPaymentIT`: 15 test HTTP loopback + JDBC/MySQL thật; chữ ký giả lập,
  không gửi giao dịch lên VNPay. Có IPN lặp/đồng thời, sai chữ ký/tiền/merchant,
  callback cho đơn thủ công, trạng thái chưa hoàn tất, hết hạn, tiền về muộn,
  chống refund giả và guard runtime.
- Giữ toàn bộ 53 test đã có (gồm 13 test phục hồi); mặc định hiện có 95 test.
- Dataset 1K được kiểm tra riêng, không thay seed/đáp án của bộ mặc định.

Đáp án dùng tổng fixture BigDecimal, SQL trên bảng gốc, ledger CSV và vector
HMAC độc lập; không chỉ gọi lại view/service đang cần kiểm tra để lấy đáp án.
Log RED chứng minh lỗi trước sửa: `red.log`, `state-red.log`,
`scheduling-red.log`. XML và log mới trong thư mục là nguồn kết luận thực thi.
Kiểm tra toàn dự án: `verify-all.log`; tổng hợp: `result.json`.

## Các lỗi được tái hiện và sửa

1. Chi nhánh đóng vẫn lập lịch được.
2. Đóng chi nhánh làm mất tổng doanh thu lịch sử nhưng chi tiết theo ngày vẫn có.
3. Danh sách ghế có null gây NullPointerException thay vì lỗi kiểm tra hợp lệ.
4. SQL thêm/kích hoạt lại vé VALID cho đơn đã hủy.
5. SQL sửa giá vé đã phát hành làm lệch tiền lịch sử.
6. SQL hủy đơn không đồng bộ vé/khóa ghế/thanh toán.
7. SQL xác nhận đơn PENDING khi chưa có SUCCESS.
8. SQL hủy đơn VNPay đã thu tiền có thể giả lập hoàn tiền tại quầy.
9. Hai transaction SQL đã đọc snapshot cũ cùng chèn lịch chồng lấn thành công.

Một lượt thử khóa FOR UPDATE trên booking từ trigger ticket gây xung đột với
câu UPDATE JOIN khi dọn fixture; đã đổi sang FOR SHARE và chạy lại thành công.
9 fixture từ lượt thử lỗi đã được nhận diện theo dấu fixture, dọn đúng branch ID;
danh sách lưu trong `cleanup-recovered-ids.json`, không reset database.

## Chạy sandbox khi có tài khoản merchant

1. Tự đăng ký tại https://sandbox.vnpayment.vn/devreg/ để nhận TmnCode/HashSecret.
2. Điền các khóa sau vào **config/application.properties** cục bộ, không đưa vào
   chat, CLI, log hoặc Git:

```properties
vnpay.tmn-code=<ma-merchant-8-ky-tu>
vnpay.hash-secret=<khoa-bi-mat-sandbox>
vnpay.return-url=http://127.0.0.1:8889/sandbox/return
vnpay.port=8889
```

3. Cấu hình HTTPS public forward tới `http://127.0.0.1:8889/sandbox/ipn` và đăng
   ký IPN URL đó với VNPay. Return URL và IPN là hai endpoint khác nhau.
4. Khởi động MySQL rồi mở launcher riêng:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/start_mysql84.ps1
.\mvnw.cmd exec:java '-Dexec.mainClass=com.cinema.app.SandboxMain'
```

Chọn suất chiếu tương lai, ghế và bấm **Thanh toán VNPay Sandbox**. Các lựa chọn
trạng thái thủ công phía trên thuộc nút đặt vé tại quầy; nút sandbox luôn tạo
PENDING. Trình duyệt mở cổng chính thức, chỉ IPN hợp lệ mới xác nhận vé.
Làm mới danh sách đơn sau khi thanh toán. Đơn giữ ghế 15 phút, tác vụ dọn kiểm tra
mỗi 30 giây và chỉ xử lý đơn có payment VNPAY. Bấm hủy đơn đã SUCCESS qua VNPay
sẽ bị chặn vì chưa có API refund. Dấu `VNPAY-LATE-*` cần đối soát thủ công.

Không có merchant: GUI vẫn mở, nút sandbox phải báo thiếu cấu hình trước khi
tạo đơn. Kiểm tra riêng trạng thái này:

```powershell
.\mvnw.cmd test-compile exec:java '-Dexec.mainClass=com.cinema.gui.SandboxGuiVerificationRunner' '-Dexec.classpathScope=test'
```

## Chạy lại kiểm thử

Với schema/12 trigger hiện hành đã áp dụng vào DB test:

```powershell
.\mvnw.cmd clean verify -Pmysql-it
powershell -NoProfile -ExecutionPolicy Bypass -File database/verify_all.ps1
python tools/prepare_dataset_sample.py
.\mvnw.cmd '-Dtest=CinemaDatasetCheck' test
```

Không dùng script setup/migration nhắm DB demo để chạy test. Trigger cập nhật
chỉ được áp dụng vào `cinema_chain_test` trong lượt này; DB demo giữ nguyên.

## Chưa kiểm chứng / giới hạn

- Chưa có merchant, chưa có giao dịch qua VNPay Sandbox thật hoặc public IPN.
- Chưa có API query/refund; không nhận tiền thật. LATE là dấu đối soát, không
  phải xác nhận đã hoàn tiền.
- Chưa chạy tải 100K/1M, crash MySQL/mất điện hoặc mất phản hồi đúng lúc COMMIT.
- Quy tắc tuổi khách, phân quyền nhân viên, hạn bán vé tại quầy chưa được đặc tả.
- Không bảo đảm trước mọi SQL đặc quyền xóa/sửa lịch sử ngoài API nghiệp vụ.
- Main/DB demo chưa được migration trigger; không lấy test trên DB test để
  tuyên bố bản demo đang có đầy đủ bảo vệ mới.
