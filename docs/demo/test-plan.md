# KẾ HOẠCH KIỂM THỬ & KIỂM CHỨNG HỆ THỐNG (TEST PLAN)
**(Tài liệu QA/Verification — Đáp ứng các Cổng chất lượng Gate A, B, C, D, E, F)**

---

## 1. Mục tiêu Kiểm thử (Testing Objectives)

Kế hoạch kiểm thử của dự án **Hệ thống Quản lý Chuỗi Rạp Chiếu Phim** được thiết kế theo phương pháp **Verification-First Engineering**, đảm bảo mọi yêu cầu nghiệp vụ và chấm điểm học thuật (**R1 – R8**) đều được kiểm chứng bằng bằng chứng thực thi thực tế:
1. **Kiểm chứng biên dịch & đóng gói trên Java 21 LTS (Gate A):** Toàn bộ dự án biên dịch sạch, không lỗi cú pháp, chạy tự động qua `mvnw.cmd clean verify`.
2. **Kiểm chứng tuân thủ kiến trúc phân tầng (Gate B):** Kiểm tra tự động bằng **ArchUnit** (`ArchitectureComplianceTest`) xác nhận tầng giao diện Swing (`..ui..`) tuyệt đối không chứa SQL hay gọi trực tiếp JDBC (`java.sql.*`), không gọi trực tiếp `Repository`, và không mã hóa cứng tài khoản quản trị `root` (ứng dụng kết nối bằng tài khoản phân quyền tối thiểu `cinema_app`).
3. **Kiểm chứng toàn vẹn Cơ sở dữ liệu & Xử lý đồng thời (Gate C):**
   - Kiểm chứng quy mô dữ liệu khởi tạo ($\ge 4$ chi nhánh, $\ge 12$ phòng chiếu, $\ge 960$ ghế, $\ge 20$ bộ phim, $\ge 80$ suất chiếu, $\ge 150$ đơn đặt vé).
   - Kiểm chứng Trigger và Ràng buộc CSDL từ chối trực tiếp các thao tác ghi SQL vi phạm quy tắc: trùng lịch chiếu trong cùng phòng (`BR-06`), thời lượng suất chiếu ngắn hơn thời lượng phim (`BR-05`), ghế trên vé không thuộc phòng chiếu của suất chiếu (`BR-09`), bán trùng ghế hợp lệ (`BR-10`), thanh toán `SUCCESS` thứ hai cho cùng một đơn (`BR-13`).
   - Kiểm chứng an toàn giao dịch đồng thời:
     - **12 luồng đồng thời** cùng tạo suất chiếu chồng lấn giờ trong một phòng chiếu $\rightarrow$ chỉ đúng **01 luồng thành công**, 11 luồng bị từ chối (`ScreeningConcurrencyIT`).
     - **20 luồng đồng thời** cùng đặt một ghế trong một suất chiếu $\rightarrow$ chỉ đúng **01 luồng thành công**, 19 luồng bị từ chối (`BookingConcurrencyIT`).
   - Kiểm chứng quy trình **Hủy vé & Bán lại ghế** (`CANCELLED` $\rightarrow$ giải phóng khóa `active_seat_key` $\rightarrow$ khách mới đặt lại thành công cùng ghế đó mà vẫn giữ lịch sử vé đã hủy) và chuyển tiếp trạng thái thanh toán (`PENDING` $\rightarrow$ `SUCCESS`, `SUCCESS` $\rightarrow$ `REFUNDED`).
4. **Kiểm chứng hành vi ứng dụng Desktop & Giao diện tiếng Việt (Gate D):** Khởi chạy ứng dụng thật (`AppShell`), thực thi các luồng nghiệp vụ trên 5 màn hình bắt buộc và chụp ảnh màn hình xác thực (`verification/screenshots/01_*.png` .. `09_*.png`).
5. **Kiểm chứng độ chính xác Báo cáo Tài chính & Xuất PDF tiếng Việt (Gate E):** Đối chiếu 100% số liệu tổng hợp trên màn hình và trong tệp PDF xuất ra (`BaoCaoDoanhThu_Mau.pdf`, `BaoCaoBanVe_Mau.pdf`) với truy vấn SQL độc lập trực tiếp trên bảng gốc, chứng minh không bị nhân bản doanh thu bởi các đơn có nhiều lần thử thanh toán (`FAILED` + `SUCCESS`).

---

## 2. Môi trường Kiểm thử (Test Environment)

| Thành phần | Cấu hình Thực thi |
|---|---|
| Hệ điều hành | Windows 11 (64-bit) |
| Java Runtime & Compiler | Eclipse Temurin OpenJDK **21.0.13 LTS** (`%USERPROFILE%\.jdks\temurin-21`) |
| Công cụ Build | Apache Maven 3.9.16 (qua `mvnw.cmd`) |
| Hệ quản trị CSDL | **MySQL Community Server 8.4.0 LTS** (`%USERPROFILE%\.mysql\mysql-8.4`, InnoDB, `utf8mb4_unicode_ci`, `ONLY_FULL_GROUP_BY`, `STRICT_TRANS_TABLES`) |
| CSDL Kiểm thử độc lập | `cinema_chain_test` (tách biệt hoàn toàn với CSDL vận hành `cinema_chain`) |
| Tài khoản kết nối ứng dụng | `cinema_app` (Least-Privilege User) |
| Khung kiểm thử tự động | JUnit Jupiter 5.11.3, ArchUnit 1.3.0, Apache PDFBox 3.0.3 |
