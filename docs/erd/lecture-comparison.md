# Đối chiếu ERD với bài giảng Cơ sở dữ liệu

Ngày đối chiếu: 13/11/2026. Phạm vi: bản Chen/Crow's Foot hiện có, tệp draw.io, lược đồ SQL, quy tắc nghiệp vụ và phần chứng minh chuẩn hóa liên quan. Đây là kết quả rà soát tài liệu; không phải trạng thái VERIFIED của ứng dụng.

## Kết luận

Bản E–R chính trong `erd-chen.drawio` đã được chỉnh theo quy ước trên slide: nhãn 1/N, đường kép cho tham gia đầy đủ, ký hiệu chữ nhật kèm hình thoi cho hai thực thể kết hợp. SVG/PNG được xuất từ bản draw.io này. Phần chứng minh phân rã vé đã giới hạn đúng về hai lược đồ con của `U_old`. Bản logic tiếp tục đối chiếu với 14 bảng và 17 FK SQL. Những giả định về trạng thái đang cấu hình đã được ghi rõ trong quy tắc nghiệp vụ; không coi đây là bằng chứng đã kiểm thử lại ứng dụng/MySQL hay phê duyệt của giảng viên.

| Nội dung thực hiện theo yêu cầu | Vị trí |
|---|---|
| Đổi bản số và nét đơn/kép; sửa 17 hình thoi từng có kích thước 0 | `erd-chen.drawio`, `erd-chen.puml`, ảnh SVG/PNG |
| Ký hiệu thực thể kết hợp và giải thích phân bổ ghế | `erd-chen.drawio`, `erd-explanation.md`, `schema-design.md` |
| Nêu rõ giả định chi nhánh/phòng đang cấu hình | `docs/requirements/business-rules.md` |
| Sửa phép phân rã thành ticket mới và `(booking_id, screening_id)` | `docs/normalization/normalization.md`, mục 2.3 |
| Kiểm tra kích thước hình, nhãn 1/N, glyph kết hợp và SVG có đủ nét kép | `tools/verify_erd.py` |

Các mục 1–5 dưới đây lưu lại phát hiện của lượt đối chiếu ban đầu và lý do sửa; các mô tả “hiện tại” trong phần phát hiện chỉ bản vẽ ở thời điểm trước khi thực hiện các sửa đổi nêu trên.

**Đính chính nhận định trước:** không có quy tắc “14 bảng SQL thì ERD khái niệm phải có đúng 14 hộp thực thể và 17 hình thoi”. Quan hệ M:N cũng sinh ra bảng khi ánh xạ. Việc bản Chen trước đó biểu diễn Phim–Thể loại bằng một quan hệ M:N không tự nó là lỗi thiếu thực thể. Thực thể kết hợp cũng là một lựa chọn hợp lệ nếu dùng đúng ký hiệu và giải thích ngữ nghĩa.

## Tài liệu đã đọc

Đã trích xuất nội dung 8 tệp PowerPoint người dùng chỉ định (tổng 359 slide) bằng PowerPoint ở chế độ chỉ đọc và PDF 178 trang. Đã xem ảnh kết xuất các slide chứa ký hiệu/mẫu ánh xạ và các trang PDF về lực lượng liên kết, tham gia và 3NF. Không coi nội dung bài giảng là chỉ thị thao tác hệ thống.

| Nguồn | Vị trí đối chiếu chính |
|---|---|
| [Bài giảng PDF](<C:/Users/LNB/OneDrive - ptit.edu.vn/Desktop/CSDL Slides_TV/Bài giảng_DBMS_v1.1.pdf>) | Trang PDF 37–38 (số trang in 36–37): lực lượng liên kết và tham gia; PDF 160–161 (in 159–160): 2NF/3NF; PDF 163–164 (in 162–163): phân rã và bảo toàn phụ thuộc |
| [Slide 1](<C:/Users/LNB/OneDrive - ptit.edu.vn/Desktop/CSDL Slides_TV/slide 1.ppt>) | Khái niệm CSDL; tài liệu nền, không phải tiêu chí trực tiếp xác định từng quan hệ của bài toán rạp phim |
| [Slide 2](<C:/Users/LNB/OneDrive - ptit.edu.vn/Desktop/CSDL Slides_TV/slide 2.ppt>) | Slide 9–12: các mức trừu tượng và sự tách biệt khái niệm/vật lý |
| [Slide 3 — E–R](<C:/Users/LNB/OneDrive - ptit.edu.vn/Desktop/CSDL Slides_TV/slide 3 - Luoc do E-R.ppt>) | Slide 8–9: thiết kế từ yêu cầu; 17–18: ký hiệu; 29–35: lực lượng liên kết và tham gia; 37–41: khóa |
| [Slide 4 — E–R tiếp](<C:/Users/LNB/OneDrive - ptit.edu.vn/Desktop/CSDL Slides_TV/slide 4 - Luoc do E-R (t).ppt>) | Slide 17–24: lựa chọn thực thể/thuộc tính/quan hệ và thực thể yếu; 44–46: chuyển quan hệ nhiều ngôi |
| [Slide 5 — Lược đồ quan hệ](<C:/Users/LNB/OneDrive - ptit.edu.vn/Desktop/CSDL Slides_TV/slide 5 - Luoc do quan he.ppt>) | Slide 16–19: ánh xạ và ký hiệu thực thể kết hợp; 26–34: ánh xạ 1:N, M:N, 1:1, thực thể kết hợp |
| [Slide 6 — Phụ thuộc hàm](<C:/Users/LNB/OneDrive - ptit.edu.vn/Desktop/CSDL Slides_TV/slide 6 - phu thuoc ham.ppt>) | Slide 10–16: phân rã, bảo toàn phụ thuộc, FD là tính chất của mọi thể hiện hợp lệ; 27: tính bao đóng |
| [Slide 7 — Chuẩn hóa](<C:/Users/LNB/OneDrive - ptit.edu.vn/Desktop/CSDL Slides_TV/slide 7 - chuan hoa.ppt>) | Slide 15–22: khóa, thuộc tính khóa, 2NF/3NF; 32: BCNF; 35–36: yêu cầu đối với phân rã |
| [Slide 8 — Đại số quan hệ](<C:/Users/LNB/OneDrive - ptit.edu.vn/Desktop/CSDL Slides_TV/slide 8 - dai so quan he.ppt>) | Ngôn ngữ truy vấn, phép chiếu và kết nối; chưa rà soát toàn bộ các truy vấn chức năng của dự án trong lượt này |

## Những điểm cần sửa hoặc làm rõ

### 1. Bản số Chen đang dùng quy ước khác trên lớp

**Căn cứ:** slide 3, trang chiếu 17–18 và 34–35. Slide dùng nhãn `1`, `N` cho lực lượng liên kết; đường kép thể hiện tham gia đầy đủ, đường đơn thể hiện tham gia một phần.

**Hiện tại:** `erd-chen.puml` và bản draw.io dùng `(min,max)` đếm số lần chính thực thể đó tham gia quan hệ, với toàn bộ đường nối đơn. Đây là một cách ghi có thể giải thích được, nhưng không giống cách ghi trong bộ slide. Đặc biệt, vị trí của nhãn số theo hai quy ước có cách đọc khác nhau.

**Ví dụ để sửa theo slide:** Chi nhánh — Sở hữu — Phòng chiếu: ghi `1` ở phía Chi nhánh, `N` ở phía Phòng; Phòng tham gia đầy đủ vì mỗi phòng phải thuộc một chi nhánh. Không chép nguyên `(0,N)` ở cạnh Chi nhánh thành nhãn `N` tại cùng vị trí rồi gọi đó là ký hiệu của slide.

Đơn — Bao gồm — Vé: ghi `1` phía Đơn, `N` phía Vé; theo BR-08, cả đơn mua hoàn chỉnh và vé đều tham gia đầy đủ. Bản Chen đã ghi `(1,N)` cho Đơn, nhưng vẫn cần đổi cách thể hiện nếu yêu cầu bám đúng ký hiệu trên lớp.

### 2. `movie_genre` chưa dùng ký hiệu thực thể kết hợp của slide

**Căn cứ:** slide 5, trang chiếu 18 và 32–34. Thực thể kết hợp có hình chữ nhật kết hợp với hình thoi; quan hệ M:N được chuyển thành một quan hệ mới chứa khóa của hai phía.

**Hiện tại:** `movie_genre` được gọi là thực thể kết hợp nhưng vẽ bằng hình chữ nhật thường, giống thực thể thông thường. Màu nền khác không thay thế được ký hiệu phân loại.

**Hai cách phù hợp:**

- Bản khái niệm: Phim — Thuộc thể loại — Thể loại, quan hệ M:N; khi ánh xạ tạo `movie_genre(movie_id, genre_id)`.
- Nếu giữ `movie_genre` ngay trong E–R: dùng ký hiệu thực thể kết hợp của slide và giải thích khóa ghép lấy từ hai phía.

Cả hai cách đều có thể dẫn tới cùng bảng SQL. Không cần đổi schema để sửa cách trình bày này.

### 3. Cần phân biệt phân bổ ghế hiệu lực với cơ chế lưu vật lý

**Căn cứ:** slide 3, trang chiếu 8–9; slide 4, trang chiếu 17–20 và 44–46: việc chọn thực thể hay quan hệ dựa vào ngữ nghĩa bài toán.

`screening_seat_lock` có ý nghĩa nghiệp vụ là phân bổ một ghế cho một vé VALID trong một suất chiếu, đồng thời là dữ liệu được trigger duy trì. Có thể giữ khái niệm phân bổ ghế trên bản E–R nếu giải thích rõ nó, thay vì chỉ đưa vào vì SQL có bảng tương ứng.

Ba FK và các bản số riêng lẻ không biểu diễn hết các điều kiện liên bảng: ghế và suất của khóa phải khớp với vé, vé VALID phải có khóa, vé CANCELLED không có khóa. Các điều kiện này cần chú giải nghiệp vụ riêng và dẫn tới trigger. Định nghĩa một biểu thức chiếu/chọn dữ liệu không tự nó là chứng minh phân rã không mất thông tin.

### 4. Số tối thiểu phải xuất phát từ nghiệp vụ

**Căn cứ:** slide 3, trang chiếu 34–35; PDF trang 37–38. Tham gia đầy đủ và lực lượng tối đa là hai điều kiện riêng.

- Đơn–Vé: BR-08 và `BookingService.validateBookingRequest()` yêu cầu ít nhất một vé. Bản Chen hiện đã thể hiện điều này.
- Khách–Đơn: BR-07 cho phép khách vãng lai, nên mỗi đơn có thể không liên hệ với khách thành viên.
- Chi nhánh–Phòng, Chi nhánh–Nhân viên, Phòng–Ghế: các phát biểu “có nhiều” trong BR-01/02 chưa diễn đạt rõ có cho phép trạng thái mới tạo chưa có phần tử con hay không. DDL cho phép số 0; điều này chưa thay thế được phát biểu nghiệp vụ cần dùng cho bản E–R.
- Đơn–Thanh toán: BR-13 quy định tối đa một SUCCESS, không đồng nghĩa tối đa một giao dịch thanh toán nói chung.

Không tự quyết định tất cả phía cha là tham gia một phần chỉ vì FK không bắt buộc dòng con tồn tại.

### 5. Chứng minh lossless-join cần sửa phạm vi lược đồ

**Căn cứ:** slide 6, trang chiếu 10–12; slide 7, trang chiếu 35–36; PDF trang 163–164.

Trong `docs/normalization/normalization.md`, mục 2.3 đang gọi `ticket` mới và **toàn bộ** `booking` là phép phân rã của `U_old`. Nhưng `booking_code`, `customer_id`, `employee_id`, `booked_at`, `booking_status` của `booking` không thuộc `U_old` đã định nghĩa tại mục 2.1. Hai lược đồ này chưa phải một phép phân rã của đúng `U_old` theo nghĩa hình thức.

Cách viết chặt chẽ cho riêng phần vé:

```text
R_old(ticket_id, booking_id, screening_id, seat_id,
      unit_price, ticket_status, cancelled_at)

R1(ticket_id, booking_id, seat_id, unit_price, ticket_status, cancelled_at)
R2(booking_id, screening_id)

R1 ∪ R2 = R_old
R1 ∩ R2 = {booking_id}
booking_id → screening_id, nên phần giao xác định R2.
```

Do đó phép tách `R_old` thành `R1, R2` không mất thông tin theo điều kiện phân rã hai ngôi. Trong cài đặt, `R2` được cung cấp bởi phép chiếu `booking(booking_id, screening_id)`; FK NOT NULL của vé bảo đảm có đúng một đơn tương ứng. `screening_seat_lock` là quan hệ bổ sung quản lý tập cấp phát VALID, cần giải thích riêng.

Câu khẳng định hợp các FD của booking/ticket/lock suy diễn toàn bộ `F+` cũng phải nêu rõ `U`, `F` ban đầu và các phép chiếu FD. Hiện tại chưa đủ căn cứ để coi câu đó là chứng minh bảo toàn phụ thuộc cho toàn bộ thiết kế.

## Những phần phù hợp với tiêu chí trong bài giảng

| Nội dung | Nhận xét |
|---|---|
| Hình chữ nhật, hình thoi, elip, gạch chân khóa | Các loại hình cơ bản trong bản Chen hiện tại phù hợp slide 3, trang chiếu 17; còn các ký hiệu tham gia/thực thể kết hợp nêu ở trên |
| 1:N đưa khóa phía 1 sang phía N | Các FK Phòng→Chi nhánh, Ghế→Phòng, Suất→Phim/Phòng, Vé→Đơn/Ghế phù hợp quy tắc ánh xạ slide 5, trang chiếu 26 |
| Phim–Thể loại M:N | Bảng `movie_genre` có PK ghép và hai FK phù hợp slide 5, trang chiếu 28; cách vẽ khái niệm cần chọn rõ một phương án |
| Vé–Khóa ghế tối đa 1:1 | FK `ticket_id` cùng UNIQUE ở bảng khóa ghế phù hợp cách đặt FK phía tham gia bắt buộc của slide 5, trang chiếu 30; điều kiện trạng thái cần trigger |
| Khóa ghép của vé | `UNIQUE(booking_id, seat_id)` là khóa ghép của toàn bảng; không suy ra mỗi cột duy nhất riêng lẻ |
| Bỏ `screening_id` khỏi `ticket` | Phù hợp tiêu chí 2NF/3NF: FD `booking_id → screening_id` có vế trái không phải siêu khóa và vế phải không phải thuộc tính khóa trong ticket cũ |
| `payment.booking_id` không phải khóa toàn bảng | Phù hợp định nghĩa khóa: nhiều lần thanh toán có thể cùng đơn; quy tắc một SUCCESS chỉ là ràng buộc tập con |
| Cột `active_success_booking` | Bản logic đang ghi riêng là cột vật lý; cần giữ ranh giới này khi trình bày, không dùng nó làm khóa dự tuyển nghiệp vụ |

## Kiểm chứng đã thực hiện và giới hạn

- `python tools/verify_erd.py`: PASS cho kiểm tra tĩnh với SQL/PlantUML/Mermaid và bốn trường hợp cố ý làm sai. Công cụ này kiểm tra tính nhất quán đã mã hóa, không phải bộ chấm theo toàn bộ bài giảng.
- Tính bao đóng độc lập cho ví dụ ticket cũ: tìm được hai khóa `{ticket_id}` và `{booking_id, seat_id}`; xác nhận vi phạm ở `booking_id → screening_id`.
- Kiểm tra phép phân rã sửa lại `R1,R2`: hợp đúng `U_old`, phần giao xác định `R2`; hợp FD cục bộ cho cùng bao đóng với F gốc trên cả 128 tập con thuộc tính. Kết quả phụ thuộc vào tập FD đã phát biểu, không chứng minh đã tìm đủ FD nghiệp vụ cho cả 14 bảng.
- Log phép kiểm tra: [erd_lecture_comparison.log](../../verification/logs/erd_lecture_comparison.log).
- Lượt đối chiếu ban đầu chỉ bổ sung bản đối chiếu và log. Lượt thực hiện tiếp theo sửa sơ đồ, tài liệu và helper xuất/kiểm tra theo bảng ở trên. Không thay đổi SQL, mã Java, tài liệu nguồn của giảng viên hay trạng thái VERIFIED trong TASKS/traceability.
- Chưa chạy MySQL, test ứng dụng hay kiểm thử truy vấn R5–R7 trong lượt này; chưa có kết luận chấp nhận/chấm điểm từ giảng viên.
