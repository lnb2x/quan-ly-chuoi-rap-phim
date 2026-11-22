# Bài tập lớn Cơ sở dữ liệu nhóm 8

Học viện Công nghệ Bưu chính Viễn thông. Lớp **INT1313-20261-01**. Giảng viên **Nguyễn Quỳnh Chi**.

Đề tài: **Hệ thống quản lý chuỗi rạp chiếu phim**.

| MSSV | Họ và tên |
|---|---|
| B24DCCN152 | Hoàng Tiến Dũng |
| B24DCCN504 | Phạm Thế Sơn |
| B24DCCN537 | Phạm Tiến Thành |
| B24DCCN053 | Lê Nguyên Bá |
| B24DCCN570 | Dương Bảo Trung |

## Các file chính

- Báo cáo nộp: `docs/academic-report/BaoCao_QuanLyChuoiRap.pdf` và bản sửa được `.docx`. Bảy phần theo mẫu trình bày yêu cầu dữ liệu, ERD, mô hình quan hệ, chuẩn hóa, hai màn hình nhập, tìm kiếm và hai báo cáo. Các phần IV–VII dẫn tới SQL cài đặt, phần V–VII kèm truy vấn và ảnh chạy thật.
- ERD Chen: `docs/erd/ER.drawio.pdf` và nguồn `docs/erd/ER.drawio`. Lược đồ quan hệ dùng `docs/erd/luoc-do-quan-he.drawio.pdf` và tệp nguồn `.drawio` cùng tên.
- SQL: `database/schema/`, `database/seed/10_seed.sql`, `database/queries/functional_sql_verification.sql` và thuyết minh `docs/sql-features/functional-sql-mapping.md`.
- Hai báo cáo xuất thực tế: `reports/exports/BaoCaoDoanhThu_Mau.pdf` và `BaoCaoBanVe_Mau.pdf`, tổng seed kiểm chứng **35.165.000 VNĐ**. Báo cáo doanh thu do Kế toán lập gửi Giám đốc Kinh doanh; báo cáo bán vé do Quản lý chi nhánh lập gửi Ban Điều hành và Quản lý Kinh doanh.
- Source Java, Maven: `src/`, `pom.xml`, `mvnw.cmd`.
- Bằng chứng ngày 22/11/2026 có [109/109 bài Java đạt trong 15 bộ](verification/test-results/summary.json) và ảnh 5 màn hình. Xem [biên bản kiểm chứng](docs/demo/verification-results.md).

## Chạy trên máy đã cấu hình

Yêu cầu **JDK 21** và **MySQL Community Server 8.4**.

1. Mở PowerShell ở thư mục gốc của mã nguồn.
2. Sao chép `config/application.properties.example` thành `config/application.properties`; điền tài khoản `cinema_app`, mật khẩu và URL MySQL của máy chạy. Không gửi file đã điền mật khẩu cùng bài nộp.
3. Khi đã có CSDL `cinema_chain` đúng schema và dữ liệu, chạy `mvnw.cmd compile exec:java "-Dexec.mainClass=com.cinema.app.Main"` nếu máy có Maven.
4. Thực hiện kịch bản trong `DEMO_GUIDE.md`. Mở ứng dụng không reset dữ liệu; đóng/mở lại giữ các đơn đã lập.

## Cài database lần đầu

Trên máy Windows có MySQL tại `%USERPROFILE%/.mysql/mysql-8.4`, datadir `%USERPROFILE%/.mysql/data-8.4`, đã đặt cấu hình local, chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/setup.ps1 -TargetDatabase cinema_chain
```

Script chỉ khởi tạo khi database trống, không tự thay database đã có bảng. Không chạy các chế độ reset để chuẩn bị buổi bảo vệ trên dữ liệu đang dùng.

Nếu cài ở đường dẫn khác, dùng MySQL client/Workbench trên database mới, trống: tạo schema `cinema_chain` với `utf8mb4_unicode_ci`, cấp cho `cinema_app` quyền SELECT/INSERT/UPDATE/DELETE/SHOW VIEW/EXECUTE, rồi chạy theo thứ tự `01_schema.sql`, `01b_foreign_keys.sql`, `02_triggers.sql`, `03_views.sql`, `10_seed.sql`. Đăng nhập tài khoản quản trị trong công cụ để cài DDL, không lưu mật khẩu vào script. Các script PowerShell kiểm đúng instance cục bộ nên không dùng nguyên xi để điều khiển server ở vị trí khác.

## Kiểm chứng lại

Môi trường tự động tách riêng `cinema_chain_test`. Trên máy đã cấu hình test theo hướng dẫn dự án:

```powershell
.\mvnw.cmd clean verify -Pmysql-it
powershell -NoProfile -ExecutionPolicy Bypass -File database/verify_all.ps1
python -X utf8 -B tools/verify_erd.py
uv run --no-project --with pytest pytest -q tools/tests/test_verify_erd.py tools/tests/test_verify_test_results.py
```

`verify_all.ps1` bắt buộc đủ mọi lớp `*Test`/`*IT` hiện hành, XML mới, không bỏ qua test, GUI, hai JVM, SQL chức năng và đối chiếu SHA-256 demo. Không chạy các test thay dữ liệu trên `cinema_chain`.

## Phạm vi và lưu ý

Bản nộp triển khai quản lý/bán vé tại quầy bằng Swing. `employee.role` mô tả vai trò nghiệp vụ, không phải hệ thống đăng nhập/phân quyền online đã triển khai. SePay VietQR chạy trong sandbox trên cơ sở dữ liệu kiểm thử. Bộ kiểm chứng dùng webhook mô phỏng để kiểm tra xác thực, đối soát giao dịch và trạng thái đơn.
