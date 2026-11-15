# Diễn tập cập nhật trigger trên clone MySQL 8.4

Đã chạy thực tế trên `cinema_chain_migration_clone` mới tạo; không áp dụng migration/DDL/DML lên `cinema_chain` hoặc `cinema_chain_test`.

- Sao lưu `cinema_chain` bằng helper mới; khôi phục đầy đủ 14 bảng vào clone. Số dòng và SHA-256 dữ liệu từng bảng khớp nguồn.
- DDL clone tương đương nguồn; mysqldump thêm `CHARACTER SET utf8mb4` tường minh ở một số cột nên raw DDL SHA nguồn/clone không bằng nhau. Chi tiết trong `clone-restore-comparison.json`.
- Chạy `refresh_triggers.py --db cinema_chain_migration_clone --maintenance-confirmed`: 12 trigger khớp source; SHA dữ liệu, DDL bảng và view clone trước/sau giữ nguyên tuyệt đối.
- Chạy recovery SQL đã lưu: toàn bộ overall SHA clone khớp lại trạng thái trước refresh.
- Refresh lần hai: overall SHA khớp lần đầu, chứng minh thao tác lặp lại được.
- Dấu vân tay `cinema_chain` sau diễn tập khớp baseline tươi `../demo-before.json`.

Rehearsal phát hiện và sửa giới hạn delimiter MySQL client: token phục hồi cũ quá 16 ký tự; đã đổi sang token ngắn và chạy recovery thành công. File `restore-delimiter-fix.json` ghi lại giới hạn này.

Backup chứa dữ liệu chỉ nằm trong `target/demo-backup/`, không đưa vào bộ nộp. Thư mục này chứa hash, kết quả, metadata trigger và recovery SQL. Clone đã được dọn sau khi root cho phép, kiểm chứng marker tạo mới và fingerprint cuối rehearsal; không xóa CSDL khác. Bằng chứng: `clone-cleanup.json`.

Giới hạn: đây là diễn tập schema 14 bảng và refresh trigger. Không chạy migration lịch sử 13→14; cần cửa sổ bảo trì thật, đóng app/callback/client ghi suốt thao tác.
