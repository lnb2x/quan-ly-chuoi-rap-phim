# KẾ HOẠCH KIỂM THỬ & KIỂM CHỨNG HỆ THỐNG (TEST PLAN)
**(Tài liệu QA/Verification — Đáp ứng các Cổng chất lượng Gate A, B, C, D, E, F)**

---

## 1. Mục tiêu Kiểm thử & Phân tách Minh bạch 3 Cấp độ Kiểm chứng

Kế hoạch kiểm thử của dự án **Hệ thống Quản lý Chuỗi Rạp Chiếu Phim** được thiết kế theo phương pháp **Verification-First Engineering**, đảm bảo mọi yêu cầu nghiệp vụ và chấm điểm học thuật (**R1 – R8**) đều được kiểm chứng bằng mã chạy được thay vì chỉ ghi chú bằng lời:

1. **Kiểm chứng biên dịch, xóa sạch XML cũ & chạy tươi trên Java 21 LTS (Gate A):** Trước mỗi lần chạy, kịch bản `verify_all.ps1` xóa sạch các tệp `TEST-*.xml` cũ và đối chiếu trực tiếp 8 bộ test bắt buộc với `Skipped = 0`, `Failures = 0`, `Errors = 0`.
2. **Kiểm chứng tuân thủ kiến trúc & an toàn bảo mật/dữ liệu thực thi (Gate B):**
   - **ArchUnit** (`ArchitectureComplianceTest`): cấm SQL trong UI (`..ui..`), phân tầng nghiêm ngặt, cấm SQL phá hủy (`DROP`/`TRUNCATE`/`DELETE`) và cấm gọi tiến trình hệ thống trong `src/main/java`.
   - **Kiểm thử an toàn hành vi thực thi** (`TestSafetyGuardTest` — 6 bài test): kiểm tra `TestDatabaseGuard` chặn kết nối nhầm vào `cinema_chain`, kiểm tra thực thi `setup.ps1` từ chối mọi yêu cầu `-CleanReset` hoặc reset nhắm vào `cinema_chain`, kiểm tra `verify_all.ps1` không gọi `setup.ps1`, và dùng tiến trình giả lập `mysql_spy` để chứng minh mật khẩu không xuất hiện trên dòng lệnh (`CommandLine`) hay biến môi trường bổ sung của tiến trình con.
3. **Kiểm chứng toàn vẹn CSDL 14 bảng chuẩn 3NF & xử lý đồng thời (Gate C):**
   - Kiểm chứng bảng `ticket` không còn chứa `screening_id` / `active_seat_key` và bảng `screening_seat_lock` bảo vệ khóa ghế hiệu lực bằng `PRIMARY KEY (screening_id, seat_id)` + `UNIQUE (ticket_id)` + bộ Trigger chống sửa sai trực tiếp (`Gate C.13`).
   - Kiểm chứng bộ tính dấu vân tay toàn phần SHA-256 theo thứ tự khóa chính (`tools/db_fingerprint.py`) phát hiện ngay cả thay đổi nội dung 1 dòng giữ nguyên `COUNT(*)` (`Gate C.14`).
   - Kiểm chứng an toàn giao dịch đồng thời: **12 luồng CREATE** + **8 luồng UPDATE** suất chiếu chồng lấn giờ (`ScreeningConcurrencyIT`), và **20 luồng đồng thời** đặt cùng 1 ghế + hủy hoàn tiền & bán lại ghế (`BookingConcurrencyIT`).
4. **Phân biệt Minh bạch 3 Cấp độ Kiểm chứng Chức năng & Giao diện (Gate D):**
   - **Cấp độ (a) — Kiểm thử Tích hợp Tầng `Service` / `Repository` (`*IT.java`):** Gọi trực tiếp các phương thức nghiệp vụ và JDBC trên `cinema_chain_test` (MySQL 8.4.0 LTS thật).
   - **Cấp độ (b) — Kiểm chứng Giao diện Swing Tự động trên EDT (`GuiVerificationRunner`, `AppPersistenceVerificationRunner`):** Khởi tạo cửa sổ thật `AppShell` trên Swing Event Dispatch Thread, điều khiển các component Swing bằng chương trình (bao gồm cả chuyển trang phân trang 50 dòng/trang, nhập dữ liệu hợp lệ & dữ liệu vi phạm quy tắc), xuất PDF, chụp ảnh màn hình thật và kiểm chứng đóng/mở lại qua **02 tiến trình JVM tách biệt**.
   - **Cấp độ (c) — Tương tác Vật lý Bằng Chuột & Bàn phím của Người thật:** Kiểm thử tự động trên EDT không thay thế hoàn toàn thao tác bấm chuột/gõ phím vật lý của con người trên màn hình trình chiếu; do đó `DEMO_GUIDE.md` quy định danh mục diễn tập thủ công bắt buộc trước buổi bảo vệ.
5. **Kiểm chứng độ chính xác Báo cáo Tài chính & Xuất PDF tiếng Việt (Gate E):** Đối chiếu số liệu trên màn hình và trong tệp PDF xuất ra (`BaoCaoDoanhThu_Mau.pdf`, `BaoCaoBanVe_Mau.pdf`) với truy vấn SQL độc lập trực tiếp trên bảng gốc và bộ dữ liệu tính tay (*Known-Answer Fixture*).

---

## 2. Môi trường Kiểm thử (Test Environment)

| Thành phần | Cấu hình Thực thi |
|---|---|
| Hệ điều hành | Windows 11 (64-bit) |
| Java Runtime & Compiler | Eclipse Temurin OpenJDK **21.0.10+ LTS** (`%USERPROFILE%\.jdks\temurin-21`) |
| Công cụ Build | Apache Maven 3.9.x (qua `mvnw.cmd`) |
| Hệ quản trị CSDL | **MySQL Community Server 8.4.0 LTS** (`%USERPROFILE%\.mysql\mysql-8.4`, InnoDB, `utf8mb4_unicode_ci`, `ONLY_FULL_GROUP_BY`, `STRICT_TRANS_TABLES`) |
| CSDL Kiểm thử độc lập | `cinema_chain_test` (14 bảng chuẩn 3NF, tách biệt hoàn toàn với CSDL demo `cinema_chain`) |
| Tài khoản kết nối ứng dụng | `cinema_app` (Least-Privilege User; cấu hình qua `config/application.properties` hoặc biến môi trường) |
| Khung kiểm thử tự động | JUnit Jupiter 5.11.3, ArchUnit 1.3.0, Apache PDFBox 3.0.3 |
