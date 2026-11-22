# Kết quả kiểm chứng bản nộp

Lần thực thi ngày **22/11/2026**, trên **Java 21.0.13** và **MySQL Community Server 8.4**:

| Phép kiểm tra | Kết quả ghi nhận |
|---|---|
| Maven biên dịch và kiểm thử | 104 tests, 15 suites; Failures=0, Errors=0, Skipped=0 |
| Hai form nhập | Nhập hợp lệ thành công; dữ liệu sai, chồng lịch và ghế đã giữ bị từ chối |
| Tìm kiếm | Kết quả, tổng số dòng và phân trang khớp SQL |
| Doanh thu và bán vé | 125 đơn, 292 vé, 35.165.000 VNĐ trên bộ seed báo cáo; khớp SQL độc lập |
| Hai JVM | Đọc lại đúng suất, đơn, vé, payment, giá và tổng báo cáo sau khi đóng/mở |
| Giao dịch đồng thời | Tranh chấp cùng ghế và lịch phòng được kiểm tra bằng các lớp concurrency IT |
| PDF | Đã kiểm tra tiếng Việt, nhiều trang, tên dài và xử lý lỗi ghi; xem từng trang hai báo cáo mẫu |
| Database demo | Kiểm tra fingerprint trong bộ verify; dữ liệu demo được bảo toàn |

Tóm tắt từng suite và hash nguồn của lần chạy nằm trong [summary.json](../../verification/test-results/summary.json). Ảnh minh họa ở `verification/screenshots/`; hai PDF nghiệp vụ ở `reports/exports/`.

Chạy lại `database/verify_all.ps1` sẽ tạo log, XML và manifest mới trên máy chạy. Kết quả ở bảng trên mô tả lần thực thi đã ghi nhận, không tự áp dụng cho môi trường khác.

Chưa chứng nhận giao dịch VNPay merchant, hoàn tiền thật, tải lớn, phục hồi sau mất điện hoặc vận hành hệ thống đặt vé online.


## Đối chiếu sơ đồ E - R và lược đồ quan hệ

Script `python -X utf8 -B tools/verify_erd.py` kiểm tra chéo [ER.drawio](../erd/ER.drawio) và [luoc-do-quan-he.drawio](../erd/luoc-do-quan-he.drawio) với [01_schema.sql](../../database/schema/01_schema.sql) và [01b_foreign_keys.sql](../../database/schema/01b_foreign_keys.sql): khớp 14 bảng, 59 thuộc tính bản thân trên E - R, 76 thuộc tính logic trên lược đồ quan hệ, quy ước gạch chân khóa chính/khóa ngoại và đủ 17 mũi tên tham chiếu `FK -> PK`.
