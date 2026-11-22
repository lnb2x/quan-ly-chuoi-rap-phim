# Luyện bảo vệ hệ thống quản lý chuỗi rạp

Mở [slide 15 phút](Bao-ve-chuoi-rap.pptx) ở chế độ Presenter View để xem lời dẫn. Tập một lượt có bấm giờ. Đến phút thứ 7 chuyển sang Swing, phút thứ 11 mở báo cáo, phút thứ 14 quay lại slide cuối.

## Tập demo với một đơn duy nhất

1. Mở sẵn [ERD Chen](../erd/ER.drawio.pdf), [ERD logic](../erd/luoc-do-quan-he.drawio.pdf) và [hai PDF mẫu](../../reports/exports/). Với bản source, dùng lệnh mở Swing trong [hướng dẫn demo](../../DEMO_GUIDE.md). Để tập ghi dữ liệu, dùng môi trường đã cấu hình trỏ tới `cinema_chain_test` và xác nhận đúng tên CSDL trước khi thao tác.
2. Chọn một phòng hoạt động, một phim và một khung giờ tương lai còn trống, đủ thời lượng phim. Tạo suất rồi thử thêm một suất chồng giờ cùng phòng. Chỉ vào thông báo từ chối và giải thích tại sao hai phòng khác nhau được phép chiếu cùng giờ.
3. Tìm suất vừa tạo. Ghi ra giấy **ngày chiếu, chi nhánh, ghế trống trước bán, mã ghế, giá vé**. Đặt một ghế tại quầy với thanh toán thành công. Ghi **mã đơn, ngày thanh toán**. Tìm lại suất với cùng bộ lọc. Với dữ liệu tập riêng và một vé vừa bán, số ghế trống phải giảm một.
4. Mở doanh thu theo **ngày thanh toán**, bán vé theo **ngày chiếu**. Nếu muốn chứng minh chênh lệch, ghi số đầu kỳ ngay trước khi bán, giữ nguyên bộ lọc và làm mới. Khi chỉ có giao dịch vừa demo, doanh thu tăng đúng đơn giá đã bán. Xuất PDF và chỉ một con số trùng với màn hình. Nếu thời gian ngắn, ưu tiên chứng minh một chênh lệch này.
5. Nếu demo trực tiếp gián đoạn, dùng [ảnh ghế trước bán](../../verification/screenshots/03_ticket_booking_seat_selection.png), [ảnh đặt thành công](../../verification/screenshots/04_ticket_booking_success.png), [ảnh ghế đã giữ bị từ chối](../../verification/screenshots/04b_ticket_booking_occupied_rejected.png) và [PDF doanh thu mẫu](../../reports/exports/BaoCaoDoanhThu_Mau.pdf). Giới thiệu đúng là bằng chứng đã lưu.

## Hỏi xoáy và đáp miệng

| Câu giảng viên có thể hỏi | Cách trả lời trong 20–30 giây |
|---|---|
| **1. Vì sao một đơn cần nhiều vé?** | Một gia đình có thể mua ba ghế cho cùng một suất và trả tiền một lần. Đơn chứa thông tin chung về suất, khách và nhân viên. Mỗi vé ghi ghế và giá bán riêng. Một người mua vãng lai vẫn tạo được đơn vì khách hàng trên đơn là tùy chọn. |
| **2. Quan hệ nhiều–nhiều chuyển sang bảng thế nào?** | Một phim có nhiều thể loại, một thể loại chứa nhiều phim. Mỗi dòng của `movie_genre` ghi một cặp phim–thể loại. Khóa ghép cấm gán cùng thể loại hai lần cho cùng phim. Trên ERD Chen em chỉ quan hệ, trên lược đồ logic em chỉ hai FK của bảng nối. |
| **3. Có khóa chính tự tăng rồi, tại sao vé cũ vẫn vi phạm 3NF?** | Chuẩn hóa phải xét mọi phụ thuộc hàm và mọi khóa dự tuyển. Trong vé cũ, `booking_id → screening_id`; một đơn có nhiều vé nên `booking_id` không là siêu khóa của vé. `screening_id` lại không thuộc khóa dự tuyển nào. Phụ thuộc này vi phạm 3NF, đồng thời phụ thuộc vào một phần khóa ghép `(booking_id, seat_id)` nên cũng vi phạm 2NF. |
| **4. Chứng minh tách không mất thông tin bằng một câu?** | Tách vé cũ thành phần vé và cặp `(booking_id, screening_id)` được lưu trong đơn. Phần giao là `booking_id`, xác định toàn bộ cặp đó. Theo tiêu chuẩn nối không mất mát cho phép tách nhị phân, nối lại sẽ khôi phục đúng các bộ vé cũ. Kết luận có phạm vi là phép tách này. |
| **5. Bỏ suất khỏi vé rồi lại thêm bảng giữ ghế có mâu thuẫn không?** | Vé giữ lịch sử, kể cả vé đã hủy. Bảng giữ ghế chỉ chứa phân bổ còn hiệu lực. Khi hủy vé A5, dòng giữ chỗ được giải phóng, vé cũ vẫn còn và một đơn khác có thể mua lại A5. Trong bảng giữ ghế, cặp suất–ghế và riêng `ticket_id` đều xác định được một dòng. |
| **6. Hai quầy cùng thấy A5 trống thì ai thắng?** | Giao dịch lấy khóa dòng suất trước được kiểm tra và ghi trước. Nếu nó commit, giao dịch chờ sẽ thấy ghế đã giữ. Nếu nó rollback, giao dịch sau có thể mua. PK suất–ghế là ràng buộc duy nhất ở DB. Nếu được hỏi về chi phí, hai quầy mua hai ghế khác nhau trong cùng suất cũng có thể phải chờ. |
| **7. Ghi được vé đầu nhưng lỗi khi ghi thanh toán thì sao?** | Các bước ghi đơn, vé và thanh toán dùng cùng kết nối và cùng giao dịch. Lỗi khiến giao dịch rollback, bao gồm dòng giữ ghế do trigger tạo. Có thể chỉ ngay thứ tự thao tác trong `BookingService.createBooking` và ranh giới giao dịch trong `TransactionTemplate`. |
| **8. Vì sao FK chưa đủ để chặn sai ghế hoặc chồng lịch?** | Hai FK có thể cùng hợp lệ dù ghế A5 thuộc phòng 1 còn suất lại ở phòng 2. Trigger kiểm tra quan hệ giữa hai phòng. Với lịch, dự án xét hai suất OPEN cùng phòng: bắt đầu cũ < kết thúc mới và kết thúc cũ > bắt đầu mới. Suất kết thúc 10 giờ và suất sau bắt đầu 10 giờ không chồng theo quy tắc hiện tại. |
| **9. Hai lần thanh toán, một lần thất bại, có nhân đôi doanh thu?** | View chỉ nối payment SUCCESS. Cột sinh `active_success_booking` cùng UNIQUE giới hạn một SUCCESS trên mỗi đơn, còn các lần thất bại vẫn lưu được. Sau đó view cộng giá vé VALID của đơn CONFIRMED. |
| **10. Giá cơ sở thay đổi thì doanh thu cũ thế nào?** | Tại thời điểm đặt, hệ thống lưu `ticket.unit_price` bằng giá cơ sở cộng phụ thu loại ghế. Báo cáo cộng giá đã chốt trên từng vé. Hai vé cùng loại có thể có giá lịch sử khác nhau. |
| **11. Phòng 100 ghế, còn 35 ghế trống, sao chỉ lấp đầy 60%?** | Ví dụ có 60 vé bán đủ điều kiện và 5 ghế của đơn đang chờ. Cả 65 ghế đều đã bị giữ nên chỉ còn 35 ghế bán được. Tử số của tỷ lệ lấp đầy là 60 vé đã bán. |
| **12. Hai báo cáo cùng chọn một ngày mà tiền khác nhau thì kiểm gì trước?** | Xem ngày lọc. Khách trả tiền thứ Hai để xem phim thứ Sáu. Doanh thu xuất hiện ở thứ Hai trên báo cáo kế toán, còn doanh số của suất được nhóm vào thứ Sáu trên báo cáo bán vé. Sau đó mới so bộ lọc chi nhánh và trạng thái. |
| **13. Có bằng chứng gì ngoài ảnh giao diện?** | Tệp tổng hợp ngày 22/11/2026 ghi 109 test trong 15 suite, gồm kiểm thử đồng thời, với 0 thất bại, 0 lỗi và 0 bỏ qua. Biên bản cũng ghi đối soát SQL trên bộ seed cho 125 đơn, 292 vé, 35.165.000 đồng. Khi trình bày số hiện tại, đọc từ đúng môi trường và kỳ lọc đang mở. |
| **14. Hệ thống đã sẵn sàng bán vé online thật chưa?** | Phạm vi đã trình bày là ứng dụng Swing và MySQL. Dự án có nhánh SePay sandbox. Thanh toán merchant thật, hoàn tiền thật, tải lớn và phục hồi sau mất điện còn cần bằng chứng riêng. |

## Bằng chứng nên đánh dấu trước giờ bảo vệ

- **“Chỉ cho cô khóa chống trùng ghế.”** Mở [DDL bảng](../../database/schema/01_schema.sql), tìm `CREATE TABLE IF NOT EXISTS screening_seat_lock`. Tiếp theo tìm `trg_ticket_lock_after_ins` trong [trigger đồng bộ](../../database/schema/02_triggers.sql).
- **“Chỉ cho cô giao dịch bắt đầu từ đâu.”** Mở [BookingService](../../src/main/java/com/cinema/feature/booking/BookingService.java), tìm `lockScreeningForUpdate` bên trong `transactionTemplate.execute`. Khi cần chỉ câu SQL, mở [BookingRepository](../../src/main/java/com/cinema/feature/booking/BookingRepository.java).
- **“Chỉ điều kiện tính doanh thu.”** Mở [định nghĩa view](../../database/schema/03_views.sql), tìm `vw_verified_booking_revenue` và chỉ ba điều kiện trạng thái. Muốn chỉ số thu được từ lần chạy, mở [biên bản](verification-results.md) và [summary.json](../../verification/test-results/summary.json).
- **“Sao hình có nhiều đường hơn 17 FK?”** DDL có 17 khai báo FK. Biên bản ngày 21/11 ghi công cụ đếm 30 cạnh trên bản vẽ trình bày và đối chiếu khớp 17 cặp trên bản logic. Dùng [SVG logic](../erd/luoc-do-quan-he.drawio.pdf) cùng [DDL khóa ngoại](../../database/schema/01b_foreign_keys.sql) khi giải thích bản số.

**Tự kiểm trước khi lên nói.** Đóng tài liệu rồi trả lời ba câu: “Hủy vé thì dòng nào còn?”, “Quầy đầu rollback thì A5 thuộc về ai?”, “Thu tiền hôm nay cho suất ngày mai thì mở báo cáo nào?”. Nếu trả lời được bằng một ví dụ cho mỗi câu, bạn đã nắm được mạch chính của buổi bảo vệ.
