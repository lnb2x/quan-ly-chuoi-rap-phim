# Lập luận chứng minh tính đúng đắn của thiết kế ERD

Ngày đối chiếu: **13/11/2026**. Đối tượng: bản Chen, Crow's Foot, DDL và quy tắc nghiệp vụ đang có trong workspace.

**Kết luận có điều kiện:** ERD có căn cứ phù hợp với các quy tắc nghiệp vụ đã phát biểu; bản logic nhất quán với 14 bảng và 17 FK trong DDL qua kiểm tra tĩnh. Phép bỏ `screening_id` khỏi vé được chứng minh không mất thông tin và bảo toàn tập FD đã khai báo. Chưa đủ căn cứ tuyên bố mọi quy tắc đều được DBMS cưỡng chế trong mọi đường ghi dữ liệu; các giới hạn ở mục 7 là một phần của kết luận này.

## 1. Phải chứng minh điều gì?

Tính đúng của ERD được đánh giá so với **yêu cầu và giả định nghiệp vụ**. Việc sơ đồ có 14 hộp, chương trình chạy được hoặc một bộ dữ liệu mẫu không có lỗi chưa tự chứng minh thiết kế đúng.

Chuỗi lập luận dùng trong báo cáo:

1. Mỗi dữ liệu cần quản lý có thực thể/thuộc tính hoặc cách suy ra rõ ràng.
2. Mỗi liên kết có căn cứ nghiệp vụ, xác định riêng bản số tối thiểu và tối đa.
3. Khóa định danh và quy tắc ánh xạ ERD sang quan hệ giữ đúng ngữ nghĩa.
4. Lược đồ quan hệ đạt 3NF theo tập FD nghiệp vụ đã công bố; phép phân rã liên quan không mất thông tin.
5. Phân biệt điều ERD biểu diễn, điều DDL bảo vệ và điều cần trigger/giao dịch; dùng kiểm thử thực thi để bổ sung bằng chứng cho phần cài đặt.

Nguồn đối chiếu trực tiếp: [yêu cầu](../requirements/requirements.md), [BR-01–BR-16](../requirements/business-rules.md), [Chen](erd-chen.puml), [Crow's Foot](erd-logical.puml), [DDL](../../database/schema/01_schema.sql), [FK](../../database/schema/01b_foreign_keys.sql), [trigger](../../database/schema/02_triggers.sql), [chuẩn hóa](../normalization/normalization.md).

## 2. Chứng minh mô hình bao phủ dữ liệu nghiệp vụ

| Nhóm yêu cầu | Thực thể/quan hệ lưu dữ liệu | Vì sao cần tách như vậy? |
|---|---|---|
| Quản lý hạ tầng chuỗi rạp | `cinema_branch`, `screening_room`, `seat`, `seat_type` | Chi nhánh có nhiều phòng; ghế thuộc phòng; phụ thu được quản lý theo hạng ghế, tránh lặp tên hạng/phụ thu trên từng ghế. |
| Quản lý phim, phân loại | `movie`, `genre`, `movie_genre` | Một phim có nhiều thể loại và một thể loại có nhiều phim; bảng kết hợp lưu từng cặp riêng, không nhét danh sách thể loại vào một cột. |
| Lập lịch chiếu | `screening` liên kết `movie`, `screening_room` | Phim và phòng tồn tại độc lập với từng lần chiếu; giờ chiếu, giá cơ sở và trạng thái thuộc suất chiếu. |
| Ghi nhận người mua/người bán | `customer`, `employee`, các FK của `booking` | Đơn có đúng nhân viên thực hiện; khách vãng lai không bắt buộc có hồ sơ thành viên. |
| Một lần mua nhiều ghế | `booking`, `ticket` | Thông tin chung của lần mua nằm ở đơn; mỗi ghế và đơn giá lịch sử nằm ở một vé. |
| Giữ ghế và lưu lịch sử hủy | `screening_seat_lock` kết hợp suất, ghế và vé | Tách phân bổ đang hiệu lực khỏi lịch sử vé; hủy vé giải phóng phân bổ nhưng giữ vé lịch sử. |
| Lưu các lần thanh toán | `payment` liên kết `booking` | Một đơn có thể có lần thất bại rồi thành công; không ép toàn bộ lịch sử thành quan hệ 1:1. |

Sức chứa phòng được tính từ số ghế, tổng tiền vé được tính từ các vé phù hợp và doanh thu được suy ra qua điều kiện đơn/vé/thanh toán. Vì vậy các đại lượng này không đòi hỏi thêm thực thể hay cột dư thừa trong `booking`/`screening_room`.

Đây là chứng minh bao phủ **phạm vi yêu cầu hiện có**, không phải khẳng định hệ thống rạp phim ngoài thực tế chỉ cần 14 bảng. Các nghiệp vụ ngoài phạm vi chưa được dùng để suy ra thêm thực thể.

## 3. Chứng minh bản số của đủ 17 liên kết

Trong bảng dưới, cột thứ hai đọc là: **với một A, có bao nhiêu B?**; cột thứ ba đọc chiều ngược lại. Các giá trị tối thiểu dựa trên giả định đã ghi trong BR: cho phép danh mục/hạ tầng mới chưa được sử dụng.

| A → B | Số B trên một A | Số A trên một B | Căn cứ |
|---|---|---|---|
| Chi nhánh → Phòng | 0..N | 1 | `screening_room.branch_id` là FK NOT NULL; tên phòng chỉ duy nhất trong chi nhánh. |
| Chi nhánh → Nhân viên | 0..N | 1 | `employee.branch_id` là FK NOT NULL. |
| Phòng → Ghế | 0..N | 1 | `seat.room_id` là FK NOT NULL; tọa độ ghế duy nhất trong phòng. |
| Hạng ghế → Ghế | 0..N | 1 | `seat.seat_type_code` là FK NOT NULL. |
| Phim → Phim–Thể loại | 0..N | 1 | Mỗi cặp phân loại có đúng một phim. |
| Thể loại → Phim–Thể loại | 0..N | 1 | Mỗi cặp phân loại có đúng một thể loại; PK ghép ngăn lặp cặp. |
| Phim → Suất chiếu | 0..N | 1 | `screening.movie_id` là FK NOT NULL. |
| Phòng → Suất chiếu | 0..N | 1 | `screening.room_id` là FK NOT NULL; điều kiện không chồng lịch là ràng buộc bổ sung. |
| Khách hàng → Đơn | 0..N | 0..1 | `booking.customer_id` được NULL để biểu diễn khách vãng lai. |
| Nhân viên → Đơn | 0..N | 1 | `booking.employee_id` là FK NOT NULL. |
| Suất chiếu → Đơn | 0..N | 1 | `booking.screening_id` là FK NOT NULL; một đơn mua cho một suất. |
| Đơn → Vé | 1..N với đơn mua hoàn chỉnh; DDL cho 0..N | 1 | BR-08; `BookingService` từ chối ghế rỗng và tạo đơn/vé trong cùng giao dịch. FK riêng lẻ không bắt buộc có vé. |
| Ghế → Vé lịch sử | 0..N | 1 | Một ghế có thể được bán ở nhiều suất hoặc bán lại sau hủy; `ticket.seat_id` là FK NOT NULL. |
| Suất chiếu → Phân bổ hiệu lực | 0..N | 1 | `screening_seat_lock.screening_id` là FK NOT NULL. |
| Ghế → Phân bổ hiệu lực | 0..N | 1 | Một ghế có thể được phân bổ ở nhiều suất; PK ghép giới hạn một phân bổ trên mỗi cặp suất–ghế. |
| Vé → Phân bổ hiệu lực | 0..1 | 1 | `ticket_id NOT NULL UNIQUE` trong bảng phân bổ. Điều kiện VALID có 1, CANCELLED có 0 cần cơ chế đồng bộ. |
| Đơn → Thanh toán | 0..N | 1 | `payment.booking_id` là FK NOT NULL nhưng không UNIQUE; giới hạn một SUCCESS chỉ áp dụng trên tập con. |

**Giải thích ký hiệu khi bảo vệ:** Nhãn 1/N trong Chen biểu diễn lực lượng tối đa; nét kép biểu diễn tham gia đầy đủ. FK NOT NULL buộc một dòng con có cha, không buộc một cha có dòng con. Vì thế chi nhánh mới chưa có phòng vẫn hợp lệ theo giả định cấu hình hiện tại.

Chen thể hiện đơn mua hoàn chỉnh phải có vé; Crow's Foot hiện phản ánh DDL cho phép đơn chưa có vé. Hai bản đang mô tả hai phạm vi ràng buộc khác nhau và phải được giải thích như trên, không nói chúng giống tuyệt đối về mức tối thiểu.

## 4. Chứng minh khóa và cách ánh xạ

- Liên kết 1:N được ánh xạ bằng FK ở phía N, ví dụ `screening_room.branch_id → cinema_branch.branch_id`.
- Liên kết Phim–Thể loại M:N được ánh xạ bằng `movie_genre(movie_id, genre_id)`, hai cột cùng tạo PK. Không cột nào duy nhất riêng lẻ.
- Liên kết Vé–Phân bổ là 1:0..1 nhờ FK `ticket_id` đặt ở phía phân bổ và có UNIQUE NOT NULL.
- `UNIQUE(booking_id, seat_id)` định danh vé trong cùng đơn; nó không ngăn hai đơn khác nhau cùng mua một ghế của cùng suất. Điều đó do PK `(screening_id, seat_id)` ở bảng phân bổ bảo vệ.
- `payment.booking_id` không phải khóa dự tuyển: một đơn có nhiều lần thanh toán. Cột sinh `active_success_booking` phục vụ chỉ mục duy nhất có điều kiện, không được dùng làm khóa dự tuyển của quan hệ logic.

Ví dụ khóa ghép tối thiểu của phòng: `branch_id` không xác định phòng vì một chi nhánh có nhiều phòng; `room_name` không xác định phòng vì các chi nhánh được trùng tên phòng. Cả cặp `(branch_id, room_name)` mới xác định một phòng, đúng với UNIQUE trong DDL.

## 5. Chứng minh chuẩn hóa và không mất thông tin

### 5.1. Điều kiện áp dụng

3NF là tính chất của **lược đồ quan hệ cùng tập phụ thuộc hàm F**, không phải tính chất suy ra từ hình vẽ đẹp hay từ số bảng. Với mỗi FD không tầm thường `X → A` trong F+, cần `X` là siêu khóa hoặc `A` là thuộc tính khóa.

Tài liệu [normalization.md, mục 4](../normalization/normalization.md) liệt kê FD, khóa và lập luận cho từng quan hệ. Kết luận chuẩn hóa phải gắn với tập FD đó; nếu bổ sung quy tắc nghiệp vụ làm phát sinh FD mới thì phải xét lại. Các thuộc tính SQL nullable cần được giải thích theo ngữ nghĩa nghiệp vụ, không dùng UNIQUE có NULL như khóa định danh toàn bảng.

### 5.2. Vì sao không lưu lại suất chiếu trong vé?

Thiết kế cũ có:

```text
T_old(ticket_id, booking_id, screening_id, seat_id,
      unit_price, ticket_status, cancelled_at)
```

Theo nghiệp vụ:

```text
ticket_id → booking_id, seat_id, unit_price, ticket_status, cancelled_at
(booking_id, seat_id) → ticket_id
booking_id → screening_id
```

Khóa dự tuyển của thiết kế cũ theo F này là `{ticket_id}` và `{booking_id, seat_id}`. `booking_id` riêng lẻ không phải siêu khóa vì một đơn có nhiều vé. `screening_id` không thuộc khóa dự tuyển nào. Vì thế FD `booking_id → screening_id` vi phạm điều kiện 3NF; đồng thời là phụ thuộc bộ phận vào khóa ghép, vi phạm 2NF.

Thiết kế hiện tại loại `screening_id` khỏi `ticket`; suất chiếu được xác định qua `booking`. `unit_price` là giá bán lịch sử, không phải giá hiện hành được tính lại mỗi khi thay bảng giá.

### 5.3. Chứng minh phép phân rã vé không mất thông tin

Đặt:

```text
R1 = {ticket_id, booking_id, seat_id, unit_price, ticket_status, cancelled_at}
R2 = {booking_id, screening_id}
```

Ta có `R1 ∪ R2 = U_old`, `R1 ∩ R2 = {booking_id}` và `booking_id → R2`. Theo điều kiện phân rã hai ngôi, phần giao xác định một thành phần nên phép phân rã **không mất thông tin**. Với mọi thể hiện hợp lệ `r` thỏa F:

```text
π_R1(r) ⋈ π_R2(r) = r
```

Trong cài đặt, R2 được cung cấp bởi phép chiếu hai cột của `booking`. FK NOT NULL cùng PK của `booking` bảo đảm mỗi vé kết nối đúng một đơn, khôi phục đúng một `screening_id` cho mỗi vé.

Các FD cục bộ của R1 giữ thông tin vé và khóa ghép; R2 giữ `booking_id → screening_id`. Tính bắc cầu khôi phục `ticket_id → screening_id`, nên các FD gốc được bảo toàn. Bộ kiểm tra tĩnh đã đối chiếu bao đóng trước/sau cho toàn bộ **128 tập con** của bảy thuộc tính.

Kết luận này chứng minh **riêng phép phân rã vé**, không tự chứng minh một phép phân rã quan hệ phổ quát của toàn bộ 14 bảng. `screening_seat_lock` là quan hệ phân bổ đang hiệu lực bổ sung, không phải phép chiếu của toàn bộ lịch sử vé.

### 5.4. Vì sao bảng phân bổ không lặp lại lỗi 3NF của vé cũ?

Trong `L(screening_id, seat_id, ticket_id)`:

```text
(screening_id, seat_id) → ticket_id
ticket_id → screening_id, seat_id
```

Cả hai vế trái là khóa dự tuyển, được thể hiện bằng PK ghép và UNIQUE NOT NULL. Theo tập FD đã phát biểu, L đạt BCNF, suy ra 3NF. Một `screening_id` hoặc một `seat_id` riêng lẻ không xác định được vé.

Giả sử hai vé VALID khác nhau giữ cùng ghế G trong suất S. Khi đồng bộ, chúng đòi hỏi hai dòng khóa `(S,G,t1)` và `(S,G,t2)`, trái với PK `(S,G)`. Vì vậy mô hình cùng ràng buộc duy nhất và cơ chế đồng bộ ngăn trạng thái này. Tính đúng của cơ chế đồng bộ trên MySQL thật vẫn cần kiểm thử thực thi.

## 6. Các tình huống dùng để giải thích mô hình

Đây là các ví dụ lập luận, **không phải kết quả chạy SQL trong lượt này**.

| Tình huống | Mô hình phải cho kết quả gì? | Căn cứ |
|---|---|---|
| Một phim vừa Hành động vừa Hài | Cho phép hai dòng phân loại | PK ghép của `movie_genre` chỉ cấm lặp cùng cặp. |
| Hai chi nhánh đều có Phòng 01 | Cho phép | UNIQUE theo `(branch_id, room_name)`, không theo riêng tên phòng. |
| Khách vãng lai mua hai ghế | Một đơn có `customer_id NULL`, hai vé | Quan hệ khách–đơn tùy chọn; quan hệ đơn–vé nhiều. |
| Ghế A1 bán cho hai suất khác nhau | Cho phép | Hai cặp `(screening_id, seat_id)` khác nhau. |
| Ghế A1 bán hai lần và cả hai vé còn VALID trong cùng suất | Từ chối | Trùng PK bảng phân bổ hiệu lực. |
| Hủy vé rồi bán lại ghế trong đơn mới | Cho phép, giữ vé cũ | Vé cũ CANCELLED, khóa cũ được giải phóng, vé mới nhận khóa. |
| Một lần FAILED rồi một lần SUCCESS cho cùng đơn | Cho phép | `booking_id` trong `payment` được lặp. |
| Hai thanh toán cùng SUCCESS cho một đơn | Từ chối | `uq_payment_one_success` trên cột sinh có điều kiện. |

## 7. Điểm cần nói chính xác và phần chưa được chứng minh

| Nội dung | Kết quả đối chiếu source hiện tại | Hệ quả khi bảo vệ |
|---|---|---|
| BR-08: đơn mua có ít nhất một vé | Java kiểm tra danh sách ghế và tạo đơn/vé trong transaction; DDL vẫn cho phép chèn riêng đơn không vé. | Nêu rõ phạm vi quy trình ứng dụng; không khẳng định FK buộc mọi đơn SQL có vé. |
| BR-11: hủy đơn đồng bộ vé và hoàn tiền | `BookingRepository.cancelBookingRecords()` thực hiện các UPDATE trong transaction. Không có trigger tên `trg_booking_status_sync` như bảng BR đang ghi. | Không trình bày việc chỉ UPDATE trạng thái đơn bằng SQL trực tiếp là đủ để tự hủy vé/hoàn tiền. |
| BR-13: thanh toán phải khớp tổng vé và đơn chưa hủy | Hai trigger `trg_payment_validate_ins/upd` hiện chỉ kiểm tra số tiền dương và bổ sung `paid_at` khi SUCCESS; không truy vấn tổng vé hay trạng thái đơn. Java có kiểm tra trạng thái ở luồng chuyển thanh toán và tính tổng khi tạo đơn. | Chưa có căn cứ nói DBMS đã bảo vệ toàn bộ BR-13 cho mọi lệnh SQL trực tiếp. |
| Toàn bộ 14 bảng vật lý đều 3NF | Chứng minh `payment` đang xét quan hệ logic bảy thuộc tính; cột generated là chi tiết cài đặt được loại khỏi tập thuộc tính logic đó. | Nói rõ mức mô hình. Nếu xét cột generated như thuộc tính quan hệ, phải xét thêm FD từ biểu thức sinh; không dùng kết luận logic để bỏ qua FD vật lý. |
| Cài đặt đúng trong mọi tình huống đồng thời/cập nhật | Đọc ràng buộc và mã nguồn chưa thay thế thực thi MySQL thật. | Chưa kết luận VERIFIED cho DB/ứng dụng từ lượt kiểm tra tĩnh này. |
| Khớp toàn bộ bài giảng của giảng viên | Có bản đối chiếu lịch sử tại `lecture-comparison.md`; lượt này không đọc lại tài liệu giảng viên gốc. | Không coi bản đối chiếu cũ là kiểm chứng học liệu mới hay sự phê duyệt của giảng viên. |

Các sai khác BR-11/BR-13 là hạn chế của mô tả/cơ chế thực thi, chưa tự chứng minh cấu trúc thực thể–liên kết sai; chúng ngăn kết luận mạnh hơn rằng toàn bộ quy tắc đều đã được DBMS bảo vệ.

## 8. Bằng chứng chạy mới và phạm vi của nó

Lệnh đã chạy trong lượt này:

```powershell
$env:PYTHONIOENCODING = 'utf-8'
python -B tools/verify_erd.py
```

Kết quả **PASS** cho: danh sách 14 thực thể; thuộc tính, kiểu, NULL, PK, FK, UQ của bản logic so với DDL; 17 liên kết và bản số Chen/Crow's Foot/Mermaid theo phạm vi đã mã hóa; cấu trúc/nét kép/ký hiệu kết hợp của Chen draw.io/SVG và fingerprint nguồn SVG; phép phân rã vé và bảo toàn bao đóng. Bộ kiểm tra cũng bắt được bốn lỗi ngữ nghĩa và hai lỗi hình học/ký hiệu được cố ý đưa vào bản sao trong bộ nhớ.

Log mới và SHA-256 của các tệp nguồn đối chiếu: [erd_correctness_proof.log](../../verification/logs/erd_correctness_proof.log). Log này không phải dấu vân tay dữ liệu `cinema_chain`.

Đã xem trực tiếp ảnh Chen PNG để đối chiếu cách thể hiện tổng thể. Bộ kiểm tra không chứng minh mọi chi tiết hình ảnh, không kiểm tra đầy đủ mọi trigger/đường cập nhật, không tự tìm hết FD nghiệp vụ, không đối chiếu schema đang chạy và không thay thế kiểm thử MySQL/JUnit.

Không chạy lại Maven/MySQL/GUI, không thay SQL/Java hay dữ liệu; không đổi `TASKS.md` hoặc `REQUIREMENTS_TRACEABILITY.md` sang VERIFIED.

## 9. Đoạn trình bày ngắn khi bảo vệ

> Em xây dựng ERD từ các quy tắc nghiệp vụ đã phát biểu và giải thích riêng bản số tối thiểu, tối đa của từng quan hệ. Chẳng hạn, khách vãng lai làm cho quan hệ từ đơn tới khách là tùy chọn; phim nhiều thể loại được biểu diễn bằng thực thể kết hợp; một đơn nhiều ghế được tách thành đơn và vé. Khi ánh xạ, các quan hệ 1:N dùng FK ở phía N, quan hệ M:N dùng khóa ghép và quan hệ vé–phân bổ dùng FK UNIQUE. Vé không lưu lại suất chiếu vì đơn đã xác định suất chiếu; phép tách này không mất thông tin do thuộc tính giao booking_id xác định toàn bộ quan hệ đơn–suất. Bảng phân bổ dùng hai khóa dự tuyển để chống trùng ghế mà vẫn giữ lịch sử vé hủy. Bản logic đã qua kiểm tra tĩnh với 14 bảng, 17 FK. Các ràng buộc liên bảng và đồng thời cần bằng chứng MySQL riêng; em phân biệt rõ phần do khóa/FK bảo vệ và phần do trigger/giao dịch ứng dụng thực hiện.
