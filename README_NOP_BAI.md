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

- Báo cáo nộp: `docs/academic-report/BaoCao_QuanLyChuoiRap.pdf` và bản sửa được `.docx`. Bảy chương lần lượt trình bày yêu cầu dữ liệu, ERD, quan hệ, 3NF, hai form nhập, tìm kiếm, hai báo cáo. Phụ lục có SQL cài đặt và SQL tương ứng các chức năng.
- ERD Chen: `docs/erd/ERD_Chen_A1.pdf` để phóng lớn/đọc thuộc tính, `erd-chen.drawio`, `.puml`, `.svg`. Sơ đồ dữ liệu quan hệ: `docs/erd/Data_Diagram_A3.pdf`, `erd-logical.drawio`, `.puml`, `.svg`.
- SQL: `database/schema/`, `database/seed/10_seed.sql`, `database/queries/functional_sql_verification.sql` và thuyết minh `docs/sql-features/functional-sql-mapping.md`.
- Hai báo cáo xuất thực tế: `reports/exports/BaoCaoDoanhThu_Mau.pdf` và `BaoCaoBanVe_Mau.pdf`, tổng seed kiểm chứng **35.165.000 VNĐ**. Báo cáo doanh thu do Kế toán lập gửi Giám đốc Kinh doanh; báo cáo bán vé do Quản lý chi nhánh lập gửi Ban Điều hành và Quản lý Kinh doanh.
- Source Java, Maven: `src/`, `pom.xml`, `mvnw.cmd`. Chương trình và bảy thư viện runtime đã được đóng kèm trong `runtime/` của gói ZIP.
- Bằng chứng mới ngày 15/11/2026: `verification/last-run.json`, `verification/test-results/`, các log và ảnh màn hình. Bộ Java **104 tests / 15 suites, Failures=0, Errors=0, Skipped=0**; công cụ Python có **9 tests** riêng.

## Chạy trên máy đã cấu hình

Yêu cầu **JDK 21** và **MySQL Community Server 8.4**. Gói không kèm mật khẩu cục bộ hoặc MySQL/JDK installer.

1. Giải nén toàn bộ ZIP vào một thư mục.
2. Sao chép `config/application.properties.example` thành `config/application.properties`; điền tài khoản `cinema_app`, mật khẩu và URL MySQL của máy chạy. Không gửi file đã điền mật khẩu cùng bài nộp.
3. Khi đã có CSDL `cinema_chain` đúng schema và dữ liệu, nhấp đúp `run_demo.cmd`. Có thể chạy từ source bằng `mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"` nếu máy có Maven.
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
python -X utf8 -B -m unittest discover -s tools/tests -v
```

`verify_all.ps1` bắt buộc đủ mọi lớp `*Test`/`*IT` hiện hành, XML mới, không bỏ qua test, GUI, hai JVM, SQL chức năng và đối chiếu SHA-256 demo. Không chạy các test thay dữ liệu trên `cinema_chain`.

## Phạm vi và lưu ý

Bản nộp triển khai quản lý/bán vé tại quầy bằng Swing. `employee.role` mô tả vai trò nghiệp vụ, không phải hệ thống đăng nhập/phân quyền online đã triển khai. VNPay là phần sandbox bổ sung ở DB test; callback tự động dùng chữ ký mô phỏng, chưa chứng nhận giao dịch merchant hay hoàn tiền thật. Không dùng demo để nhận tiền thật.

Ngày 15/11/2026, theo xác nhận người vận hành, 12 trigger demo đã được cập nhật khớp source sau rehearsal backup/restore trên clone; dữ liệu, DDL bảng và view giữ nguyên. Doanh thu demo có lịch sử thao tác là **35.375.000 VNĐ**, khác seed test là bình thường. Bằng chứng tại `verification/submission-2026-11-15/demo-trigger-upgrade/`.

Trọng số 8 mục trong báo cáo là tiêu chí chấm của học phần, không phải điểm số do nhóm tự cam kết.
