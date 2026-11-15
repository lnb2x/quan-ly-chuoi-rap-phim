# Hệ thống quản lý chuỗi rạp chiếu phim

Bài tập lớn môn **Cơ sở dữ liệu**, Học viện Công nghệ Bưu chính Viễn thông.
Lớp **INT1313-20261-01** · Nhóm **8** · Giảng viên **Nguyễn Quỳnh Chi**.

| MSSV | Thành viên |
|---|---|
| B24DCCN152 | Hoàng Tiến Dũng |
| B24DCCN504 | Phạm Thế Sơn |
| B24DCCN537 | Phạm Tiến Thành |
| B24DCCN053 | Lê Nguyên Bá |
| B24DCCN570 | Dương Bảo Trung |

## Sản phẩm nộp

| Nội dung | Tệp hoặc thư mục |
|---|---|
| Báo cáo đầy đủ 7 mục và phụ lục SQL | [PDF](docs/academic-report/BaoCao_QuanLyChuoiRap.pdf), [Word](docs/academic-report/BaoCao_QuanLyChuoiRap.docx) |
| ERD Chen và mô hình quan hệ | [ERD A1](docs/erd/ERD_Chen_A1.pdf), [Data Diagram A3](docs/erd/Data_Diagram_A3.pdf); nguồn sửa được trong `docs/erd/` |
| Yêu cầu dữ liệu và nghiệp vụ | `docs/requirements/` |
| Lược đồ 14 bảng và từ điển dữ liệu | `docs/relational-model/schema-design.md` |
| Phụ thuộc hàm, 3NF và lossless join | `docs/normalization/normalization.md` |
| SQL cài đặt, dữ liệu mẫu và truy vấn chức năng | `database/schema/`, `database/seed/`, `database/queries/` |
| SQL tương ứng hai form nhập, tìm kiếm và hai báo cáo | `docs/sql-features/functional-sql-mapping.md` |
| Hai báo cáo xuất thực tế | [Doanh thu](reports/exports/BaoCaoDoanhThu_Mau.pdf), [Bán vé](reports/exports/BaoCaoBanVe_Mau.pdf) |
| Kịch bản trình diễn | [DEMO_GUIDE.md](DEMO_GUIDE.md) |
| Kiến trúc ứng dụng | [ARCHITECTURE.md](ARCHITECTURE.md) |

Ứng dụng có hai form nhập **suất chiếu** và **đặt vé**, một màn hình **tìm kiếm suất chiếu**, hai màn hình **doanh thu** và **bán vé** có xem trước và xuất PDF tiếng Việt. Báo cáo doanh thu do Kế toán lập gửi Giám đốc Kinh doanh; báo cáo bán vé do Quản lý chi nhánh lập gửi Ban Điều hành và Quản lý Kinh doanh.

## Cài đặt và chạy

Yêu cầu **JDK 21**, **Maven** và **MySQL Community Server 8.4**. Giao diện dùng Java Swing/FlatLaf, truy cập dữ liệu bằng JDBC, xuất PDF bằng PDFBox.

1. Sao chép `config/application.properties.example` thành `config/application.properties` và điền thông tin MySQL cục bộ. File chứa mật khẩu được Git bỏ qua.
2. Trên một database mới, trống tên `cinema_chain`, chạy SQL theo thứ tự: `01_schema.sql`, `01b_foreign_keys.sql`, `02_triggers.sql`, `03_views.sql`, `10_seed.sql`. Các tệp nằm trong `database/schema/` và `database/seed/`. Dùng `utf8mb4_unicode_ci`; tài khoản ứng dụng `cinema_app` cần quyền SELECT, INSERT, UPDATE, DELETE, SHOW VIEW và EXECUTE. Dùng tài khoản quản trị để cài DDL.
3. Chạy ứng dụng từ thư mục dự án:

```powershell
.\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"
```

Các script PowerShell đi kèm dành cho máy Windows có MySQL tại `%USERPROFILE%/.mysql/mysql-8.4` và datadir `%USERPROFILE%/.mysql/data-8.4`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/start_mysql84.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File database/setup.ps1 -TargetDatabase cinema_chain
```

`setup.ps1` chỉ khởi tạo database trống. Khởi động ứng dụng giữ dữ liệu hiện có; các chế độ reset trong setup chỉ áp dụng cho `cinema_chain_test` theo điều kiện xác nhận của script.

## Kiểm thử

Mã kiểm thử nằm trong `src/test/`. Cấu hình test phải trỏ tới `cinema_chain_test` bằng tài khoản `cinema_app` trong file cấu hình cục bộ.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/setup.ps1 -ResetTestDb -TargetDatabase cinema_chain_test -ConfirmYes
.\mvnw.cmd clean verify -Pmysql-it
powershell -NoProfile -ExecutionPolicy Bypass -File database/verify_all.ps1
python -X utf8 -B tools/verify_erd.py
```

Lần kiểm chứng ngày **15/11/2026**: **104 kiểm thử Java, 15 suite; Failures=0, Errors=0, Skipped=0**. Xem [kết quả](docs/demo/verification-results.md) và [tình huống kiểm thử](docs/demo/test-cases.md). Log/XML được sinh cục bộ khi chạy kiểm thử. Hai tệp trong `verification/dataset-sample/` là dữ liệu đầu vào cho kiểm thử bổ sung `CinemaDatasetCheck`; `verification/screenshots/` chứa ảnh demo dùng trong tài liệu.

## Phạm vi

Bản nộp triển khai ứng dụng quản lý và bán vé bằng Swing. Tổng doanh thu của bộ seed kiểm chứng là **35.165.000 VNĐ**. Demo đang dùng có lịch sử thao tác nên có thể khác seed.

VNPay là phần sandbox bổ sung, chưa chứng nhận thanh toán hoặc hoàn tiền merchant thật. Đăng nhập/phân quyền online, tải lớn và phục hồi sau mất điện chưa được nghiệm thu.
