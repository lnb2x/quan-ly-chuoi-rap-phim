# Kiểm thử mức 2 và 4 — 2026-11-13

Thực thi trên MySQL Community Server 8.4.0, JDK 21, tài khoản `cinema_app`,
chỉ ghi `cinema_chain_test` qua `TestDatabaseGuard`. Không reset database.

## Phạm vi 13 test mới trong PaymentRecoveryIT

Mức 2 — trạng thái nghiệp vụ (8 test):

- Chặn INSERT và UPDATE payment SUCCESS sai tổng tiền vé VALID (2 test).
- Chặn ứng dụng xác nhận thanh toán PENDING có số tiền sai.
- Chặn INSERT/UPDATE SUCCESS cho đơn đã hủy bằng SQL trực tiếp.
- Xác nhận SUCCESS hai lần: không nhân đôi tiền/vé.
- Hủy hai lần, xác nhận lại đơn đã hủy, và bán lại ghế được giải phóng.
- Hủy đơn đã thu tiền: chuyển REFUNDED đúng một lần.
- Trạng thái đích null/PENDING/REFUNDED/UNKNOWN: từ chối và giữ nguyên dữ liệu.

Mức 4 — lỗi và phục hồi (5 test):

- Lỗi CHECK khi ghi payment sau khi đã ghi booking, ticket và khóa ghế.
- RuntimeException sau khi ghi đủ booking/ticket/payment.
- AssertionError trước commit.
- KILL CONNECTION đúng kết nối test đang giữ transaction; kết nối mới vẫn bán
  lại được ghế. Không dừng server hoặc kết nối của người khác.
- Dừng cưỡng bức JVM con sau khi ghi đủ dữ liệu nhưng chưa commit; JVM cha
  xác nhận không còn dữ liệu dở dang và đặt vé lại thành công.

Các lỗi SQL/Java được chèn tại repository/TransactionTemplate trong bài test,
không thêm cơ chế giả lập sự cố vào code ứng dụng. JVM con dùng cấu hình test
và chốt an toàn như JVM cha, không nhận mật khẩu trên CLI.

## Lỗi đã tái hiện và sửa

Lượt RED ban đầu có 11 test: 5 failures, 0 errors, 0 skipped.
Ba failure tái hiện thiếu kiểm tra tiền/trạng thái ở trigger payment;
một failure tái hiện Error làm dữ liệu bị commit do finally bật auto-commit.
Failure còn lại do test kỳ vọng BusinessRuleException cho CHECK, trong khi
JDBC trả SQLException 3819 được ứng dụng dịch thành DataAccessException;
đã sửa kỳ vọng và kiểm tra đúng mã lỗi.

- `TransactionTemplate` rollback cả RuntimeException và Error trước khi khôi
  phục auto-commit; Error vẫn được ném lại.
- Hai trigger payment kiểm tra SUCCESS: khóa dòng booking, chặn đơn không tồn
  tại/đã hủy, đọc tổng vé VALID bằng locking read, đối chiếu số tiền.
- Bổ sung 2 test cho xác nhận PENDING sai tiền và hoàn tiền một lần; lượt GREEN
  có 13/13 PASS, 0 failures/errors/skipped.

Không đổi tên/kiểu cột, số bảng, số trigger, tập trạng thái hoặc mô hình 3NF.
Trigger nguồn đã cập nhật tại `database/schema/02_triggers.sql` và chỉ được
áp dụng lên **cinema_chain_test** trong phiên này. **cinema_chain giữ nguyên
trigger cũ**, chưa nhận bản sửa; đối chiếu SHA-256 không thay thế việc migration.

## Chạy lại

Với DB test đã có schema/trigger hiện hành và dữ liệu mẫu chuẩn:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/start_mysql84.ps1
.\mvnw.cmd '-Dit.test=PaymentRecoveryIT' verify
powershell -NoProfile -ExecutionPolicy Bypass -File database/verify_all.ps1
```

`PaymentRecoveryIT` cũng được Maven chạy mặc định vì có hậu tố IT. Nếu dùng DB
test cũ chưa có trigger mới, các test thanh toán phải fail; không bỏ qua chúng.
Mỗi test tạo suất chiếu riêng ngày 01/02/2027, dọn đúng screening_id sau test.
Auto-increment của DB test có thể tăng. Không dùng script này để cập nhật demo.

## Bằng chứng và giới hạn

- `red.log`, `RED-PaymentRecoveryIT.xml`: lỗi tái hiện trước sửa.
- `green.log`, `GREEN-PaymentRecoveryIT.xml`: lượt kiểm thử tập trung sau sửa.
- `verify-all.log`: toàn bộ kiểm thử, GUI/PDF, hai JVM và SQL chức năng.
- `cinema_chain_before.json`, `cinema_chain_after.json`, `result.json`:
  tổng hợp kết quả và đối chiếu dữ liệu được bảo vệ.

Chưa mô phỏng mất điện, crash tiến trình MySQL, lỗi ổ đĩa hoặc mất phản hồi
đúng lúc COMMIT (kết quả commit không xác định). Không tuyên bố khả năng phục
hồi sau mất điện hoặc xử lý thanh toán cổng bên ngoài từ các test này.
