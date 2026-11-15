# Danh mục thực thi và giới hạn bằng chứng

Ngày: 15/11/2026 UTC+07. Commit: `07ba4d33f6a3f19c4b011c6f0577ffb6338b9db6`.

## Lệnh và thao tác đã thực hiện

| Thao tác | Kết quả thực tế |
|---|---|
| `git status --short`, `git rev-parse HEAD`, `git diff --stat` | Ban đầu chỉ `tools/__pycache__/` untracked. Cuối phiên thêm duy nhất thư mục báo cáo này; không có diff tracked |
| Đọc bằng rg/Get-Content và ba agent ECC | Chỉ đọc source/SQL/docs/test; không agent nào được giao sửa hoặc ghi DB |
| Graphify query với backend none | Windows Application Control chặn executable; không chạy được query CLI |
| Đọc graph JSON/vocabulary bằng PowerShell | Dùng định vị, đối chiếu source; không cập nhật graph |
| `powershell -NoProfile -ExecutionPolicy Bypass -File database/start_mysql84.ps1` | Đã khởi động instance trước đó chưa chạy; MySQL 8.4.0, datadir `C:/Users/LNB/.mysql/data-8.4/`, loopback3306 |
| SELECT/SHOW metadata và SHOW CREATE TABLE | Lưu TSV bằng helper đã có; credentials trong defaults-extra-file tạm, SQL qua stdin; không in mật khẩu |
| SELECT toàn vẹn | 17 chỉ số lỗi đều0 trên mỗi DB; 4 tổng tiền khớp trong từng DB; SQL tại queries-integrity.json |
| EXPLAIN thường, 10 truy vấn | Lưu SQL/plan; không chạy EXPLAIN ANALYZE, không có benchmark latency |
| `python -B tools/verify_erd.py` | Exit1 do cp1252 khi in tiếng Việt; log giữ tại erd-static-check.log |
| `python -X utf8 -B tools/verify_erd.py` | Exit0, assertions ERD/FD và self-check trong script hoàn thành; log riêng |
| Import module kiểm tên backup helper | Không tồn tại resolve_mysql_bin; không gọi backup/migration; kết quả backup-static-check.json |
| Parse XML lịch sử, SHA-256 nguồn/bằng chứng | 12 suite/95 tests lịch sử; không chạy JUnit; manifest có hash để đối chiếu |
| Xem ba ảnh có sẵn | Crow’s Foot ERD và trang đầu revenue/sales PDF demo; không khởi chạy GUI hoặc tái xuất PDF |
| Fingerprint demo trước/sau bằng compute_fingerprint(use_root=True) | Cùng hash và toàn bộ manifest bằng nhau; không xuất data rows ra artifact |
| `powershell -NoProfile -ExecutionPolicy Bypass -File database/stop_mysql84.ps1` | Graceful shutdown thành công; sau đó không còn listener3306/tiến trình mysqld |

Các truy vấn metadata/integrity/EXPLAIN chính xác đã lưu trong ba tệp JSON, không sửa các script query của dự án. Lệnh Python điều phối được truyền qua stdin, không cài dependency mới hoặc thêm script vận hành vào repo.

## Không thực hiện

- Không Maven compile/test/verify, không chạy hoặc reset fixture database.
- Không setup/reset/prepare_demo/seed/migration và không chạy functional_sql_verification.sql có DML.
- Không INSERT/UPDATE/DELETE/DDL, không đổi grants/global variables hoặc schema.
- Không nhận tiền, gửi IPN tới merchant VNPay, gọi API refund, mở public callback hoặc gửi dữ liệu repo tới dịch vụ audit.
- Không reset/stash/commit/publish, không sửa TASKS/traceability, không cập nhật graph hay xóa thư mục pycache ban đầu.

## Bảo toàn và độ tin cậy

Fingerprint demo trước/sau bằng nhau trong phạm vi công cụ. Việc khởi động/dừng server có thể ghi file vận hành của MySQL; không đồng nghĩa mọi byte datadir/OS bất biến. Công cụ đọc nhiều connection không phải snapshot nguyên tử; audit không mở writer, không coi kết quả này là chứng minh isolation dưới tải đồng thời.

Các hash nguồn được tính lại ở cuối phiên để xác minh không đổi các file đã ghi manifest. `evidence-sha256.json` hash các artifact trong thư mục này, trừ chính manifest hash để tránh tham chiếu vòng.
