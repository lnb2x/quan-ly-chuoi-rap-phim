# Các tình huống kiểm chứng bản nộp

Kết quả thực thi mới ngày 15/11/2026 ở `verification/last-run.json` và các XML trong `verification/test-results/`. Java có **104 kiểm thử thuộc 15 suite**, không lỗi/không bỏ qua. Bảng dưới nhóm các tình huống có bằng chứng; không đồng nhất một dòng bảng với một test JUnit.

| Nhóm | Thao tác hoặc đầu vào | Kết quả mong đợi | Bằng chứng |
|---|---|---|---|
| Kiến trúc | Kiểm phụ thuộc UI, service, repository và startup | UI không truy cập JDBC trực tiếp; backend không phụ thuộc Swing; startup không chạy reset | ArchitectureComplianceTest |
| An toàn DB | URL nhắm demo/root/MariaDB hoặc reset nhầm demo | Từ chối trước khi ghi DB | TestSafetyGuardTest |
| Form suất chiếu | Phim/phòng đúng, giá dương, đủ thời lượng | Ghi được suất và hiển thị lại | ScreeningValidationTest, GUI |
| Form suất chiếu sai | Giá không dương, giờ kết thúc sai hoặc thời lượng thiếu | Thông báo lỗi tiếng Việt; không ghi sai | ScreeningValidationTest, BusinessRulesIT |
| Chồng lịch | Cùng phòng có hai khoảng thời gian giao nhau | Chặn BR-06; lịch nối đuôi hợp lệ | ScreeningConcurrencyIT, BusinessRulesIT, GUI |
| Form đặt vé | Chọn khách hoặc khách vãng lai, nhân viên và ghế trống đúng phòng | Booking, ticket, payment cùng transaction; giá chốt đúng | BookingConcurrencyIT, BusinessRulesIT, GUI |
| Form đặt vé sai | Chưa chọn ghế, ghế trùng/null/sai phòng hoặc đã được giữ | Từ chối có thông báo; không để dữ liệu ghi dở | ScreeningValidationTest, BusinessRulesIT, GUI |
| Tranh chấp ghế | Hai mươi giao dịch cùng ghế | Đúng một giao dịch thắng; một khóa hiệu lực | BookingConcurrencyIT |
| Hai JVM và thời gian | Hai JVM UTC tạo đơn cùng instant ở hai ghế khác nhau | Hai mã đơn khác nhau; booked_at đúng Việt Nam | BookingIdentityIT |
| Hủy và bán lại | Hủy đơn theo chính sách | Hủy vé, đổi payment nội bộ, giải phóng lock; bán lại được, giữ lịch sử | DatabaseIntegrityIT, BusinessRulesIT |
| Thanh toán/rollback | Sai amount/trạng thái, lặp callback, SQL/Runtime/Error, cắt kết nối/kill JVM trước commit | Từ chối hoặc rollback sạch, không tiền/vé/lock mâu thuẫn | PaymentRecoveryIT, SandboxPaymentIT |
| Ranh giới gateway | Gọi đường xác nhận thủ công cho đơn VNPay | Bị chặn; IPN ký hợp lệ theo nhánh sandbox mới được xử lý | SandboxPaymentIT, VnpayGatewayTest |
| Tìm kiếm | Nhiều bộ lọc và duyệt trang | Tổng và kết quả đúng; mỗi trang 50 dòng, không trùng/sót hoặc cắt ngầm | ScreeningSearchIT, GUI |
| Báo cáo | Tổng kỳ/chi nhánh, nhiều payment thử lại | Khớp SQL độc lập, seed 35.165.000 VNĐ, không nhân đôi | ReportAccuracyIT, BusinessRulesIT |
| Snapshot báo cáo | Writer commit giữa summary và daily | Cùng report giữ một snapshot; lần đọc mới thấy tiền mới | ReportAccuracyIT |
| PDF | Tiếng Việt, tên dài, nhiều trang, đích ghi lỗi | Không tràn lề/mất nội dung; lỗi không phá đích cũ | PdfReportWriterTest |
| Tác vụ nền UI | Worker chờ sự kiện EDT trước hoàn tất; ném lỗi nghiệp vụ/Error | EDT xử lý được sự kiện, giữ kiểu lỗi gốc | UiTaskTest |
| Bền vững dữ liệu | Đóng JVM thứ nhất rồi mở JVM mới | IDs, vé, giá, payment và tổng báo cáo khớp | persistence_restart_verify.log |
| Công cụ | XML cũ/thiếu/lặp/skip, backup lỗi, DDL sai giai đoạn, gói có secrets | Fail closed; không báo thành công giả | tools/tests/, python-tests.log |

Kịch bản GUI nhập đúng/sai có ảnh thật tại `verification/screenshots/`. SQL demo ghi đều rollback trên DB test. Không coi IPN mô phỏng là giao dịch VNPay merchant hoặc refund thật; không tuyên bố đã kiểm tra crash server, mất điện hoặc tải lớn.
