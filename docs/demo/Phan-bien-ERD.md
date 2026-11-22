# Bảo vệ ERD chuỗi rạp – tập trả lời khi cô chỉ vào sơ đồ

Mở [bản Chen](../erd/ER.drawio.pdf) để giải thích nghiệp vụ và [bản logic](../erd/luoc-do-quan-he.drawio.pdf) để chỉ PK, FK, UNIQUE. Các ví dụ A5, phim P và suất S1 dưới đây là dữ liệu giả định để lập luận.

## 1. Đọc một đường nối thành lời

Lấy tay che các đường khác. Cố định **một** đối tượng bên trái, hỏi bên phải có thể có **ít nhất** và **nhiều nhất** bao nhiêu. Đổi chiều và hỏi lại. Đưa một ví dụ mà mô hình phải cho phép. Sau cùng mới chỉ cột SQL.

**Cô hỏi “Vì sao chi nhánh – phòng là 1–N?”**
“Thưa cô, chi nhánh X có thể mở phòng 1 và phòng 2. Như vậy một chi nhánh có nhiều phòng. Xét ngược lại, phòng vật lý số 1 ở X chỉ thuộc X. Chi nhánh Y cũng có thể gọi phòng của họ là ‘Phòng 1’, nhưng đó là phòng khác. Vì thế phòng mang một mã chi nhánh bắt buộc. Nhiều phòng được phép ghi cùng mã chi nhánh.”

**Cô hỏi tiếp “Thế một chi nhánh bắt buộc có phòng à?”**
“Chi nhánh vừa khai báo có thể chưa cấu hình phòng nào. Từ chi nhánh sang phòng là 0..N, từ phòng về chi nhánh là 1..1. Con số 1–N chỉ mô tả mức tối đa hai phía.”

**Cô chỉ vào ký hiệu.** Trong bản Chen của nhóm, chữ nhật biểu diễn thực thể, hình thoi biểu diễn mối kết hợp, elip biểu diễn thuộc tính, gạch chân đánh dấu khóa. Nét đôi ở phía thực thể nghĩa là mọi thể hiện của thực thể ấy phải tham gia; nét đơn cho phép chưa tham gia. Trong Crow’s Foot, nhìn ký hiệu **tại đầu B** để biết một A liên kết bao nhiêu B: vòng tròn biểu thị tối thiểu 0, vạch biểu thị 1, chân quạ biểu thị nhiều. Ví dụ `||—o{` giữa chi nhánh và phòng đọc là mỗi phòng thuộc đúng một chi nhánh; mỗi chi nhánh có từ không tới nhiều phòng.

## 2. Lần từng quan hệ trên hình

Mỗi hàng là hai câu trả lời: cố định một A → đếm B; cố định một B → đếm A. Hai quan hệ M–N mỗi quan hệ ánh xạ thành hai FK. Cùng với 13 liên kết còn lại, lược đồ có 17 FK.

| A — B | Từ một A sang B | Từ một B về A | Nếu cô hỏi “dựa vào đâu?” |
|---|---|---|---|
| Chi nhánh — Phòng | 0..N phòng | Đúng 1 chi nhánh | Chi nhánh mới có thể chưa có phòng. Mã chi nhánh bắt buộc ở phòng; cùng tên phòng được phép dùng ở hai chi nhánh. |
| Chi nhánh — Nhân viên | 0..N nhân viên | Đúng 1 chi nhánh | Mô hình ghi một chi nhánh quản lý mỗi nhân viên qua `employee.branch_id`. Nếu cần lưu lịch luân chuyển hoặc làm đồng thời nhiều nơi thì cần mô hình phân công theo thời gian. |
| Phòng — Ghế | 0..N ghế | Đúng 1 phòng | Phòng mới có thể chưa nhập sơ đồ. Ghế A5 là một ghế vật lý trong một phòng xác định. |
| Hạng ghế — Ghế | 0..N ghế | Đúng 1 hạng | Hạng VIP có thể khai báo trước, rồi gán cho nhiều ghế. Mỗi ghế có một mã hạng; mức phụ thu chung nằm ở hạng. |
| Phim — Thể loại | 0..N thể loại | 0..N phim | P có thể vừa hài vừa hành động; thể loại hài cũng có phim Q. Mỗi dòng `movie_genre` ghi đúng một phim và một thể loại. DDL cho phép phim chưa được phân loại. |
| Phim — Suất | 0..N suất | Đúng 1 phim | Phim có thể chưa lên lịch hoặc chiếu nhiều lần. Mỗi lần chiếu mang một `movie_id`. |
| Phòng — Suất | 0..N suất | Đúng 1 phòng | Phòng 1 có suất 9 giờ và 12 giờ. Quy tắc tránh chồng lịch xét thêm khoảng thời gian; bản số 1–N tự nó không phát hiện chồng lịch. |
| Khách hàng — Đơn | 0..N đơn | 0..1 hồ sơ khách | Thành viên mới có thể chưa mua. Đơn tại quầy của khách vãng lai để `customer_id = NULL`. Xóa hồ sơ khách thì FK dùng SET NULL để giữ đơn. |
| Nhân viên — Đơn | 0..N đơn | Đúng 1 nhân viên | Mô hình bán tại quầy cần biết ai lập từng đơn. Nhân viên mới có thể chưa lập đơn nào. |
| Suất — Đơn | 0..N đơn | Đúng 1 suất | Cùng suất có nhiều nhóm khách đặt. Một đơn được mua nhiều ghế trong cùng suất; muốn mua hai suất thì lập hai đơn theo mô hình hiện tại. |
| Đơn — Vé | 1..N vé cho đơn mua hoàn chỉnh | Đúng 1 đơn | Một gia đình mua ba ghế sinh ba vé trong một đơn. Chen mô tả đơn hoàn chỉnh. FK trong SQL chỉ buộc vé có đơn; ứng dụng kiểm tra danh sách ghế và ghi cả đơn lẫn vé trong một giao dịch. |
| Ghế — Vé | 0..N vé lịch sử | Đúng 1 ghế | A5 có thể có vé cho suất sáng và vé khác cho suất chiều. Vé cũ bị hủy vẫn giữ lịch sử. |
| Suất — Ghế đang được giữ | 0..N ghế | 0..N suất | S1 giữ A5 và A6; A5 có thể được giữ ở S1 và S2. Mỗi dòng `screening_seat_lock` ghi một lần phân bổ còn hiệu lực. Khi chưa bán vé, ghế có thể chọn được suy từ phòng của suất. |
| Vé — Dòng giữ ghế | 0..1 dòng | Đúng 1 vé | Vé VALID có một dòng, vé CANCELLED có 0. FK `ticket_id NOT NULL` buộc dòng giữ chỗ có vé, UNIQUE giới hạn tối đa một dòng mỗi vé. Trigger đồng bộ theo trạng thái. |
| Đơn — Thanh toán | 0..N lần theo lược đồ | Đúng 1 đơn | Một lần thử thất bại có thể đi trước một lần thành công. Mỗi payment thuộc một đơn. Luồng tạo đơn của ứng dụng có ghi payment; FK riêng lẻ không ép đơn cha có payment. |

## 3. Nếu cô chỉ vào bất kỳ khóa chính nào

**Cách chứng minh.** Nêu một dòng đại diện cho cái gì. Chứng minh khóa phân biệt mọi dòng hợp lệ, luôn có giá trị, và tối thiểu: bỏ bất kỳ thành phần nào thì có thể trùng. Nếu có nhiều khóa dự tuyển, giải thích vì sao chọn một khóa làm PK và các khóa còn lại được bảo vệ ra sao. AUTO_INCREMENT là cơ chế cấp số; ràng buộc PRIMARY KEY mới bảo đảm định danh.

| Một dòng đại diện cho… | PK được chọn | Trả lời nếu cô đề nghị dùng cột khác |
|---|---|---|
| Một chi nhánh `cinema_branch` | `branch_id` | Mã, tên và điện thoại chi nhánh đều UNIQUE NOT NULL theo DDL hiện tại, nên từng cột cũng là khóa dự tuyển. ID cho phép sửa thông tin nghiệp vụ mà các bảng tham chiếu vẫn dùng cùng định danh. |
| Một phòng `screening_room` | `room_id` | “Phòng 1” được lặp giữa các chi nhánh. Cặp (branch_id, room_name) cũng duy nhất; ID giúp suất và ghế chỉ cần tham chiếu một cột. |
| Một hạng ghế `seat_type` | `seat_type_code` | STD, VIP, COUPLE là các mã danh mục ngắn có miền kiểm soát. Dùng trực tiếp được; tên hạng cũng duy nhất. Phụ thu có thể bằng nhau giữa các hạng. |
| Một ghế vật lý `seat` | `seat_id` | Vị trí (room_id, seat_row, seat_number) là khóa dự tuyển. Bỏ phòng thì A5 trùng giữa hai phòng; bỏ hàng thì A5 và B5 trùng; bỏ số thì A5 và A6 trùng. |
| Một phim `movie` | `movie_id` | Tên phim có thể trùng. Mã phim được bảo vệ UNIQUE NOT NULL, cũng có thể định danh phim. |
| Một thể loại `genre` | `genre_id` | Tên thể loại có UNIQUE NOT NULL. ID giữ nguyên khi đổi cách gọi một thể loại. |
| Một lần gán thể loại cho phim `movie_genre` | `(movie_id, genre_id)` | Riêng phim lặp khi có hai thể loại, riêng thể loại lặp khi có hai phim. Cặp xác định đúng một lần gán. Thêm ID riêng vẫn phải giữ UNIQUE trên cặp để ngăn gán trùng. |
| Một lần chiếu `screening` | `screening_id` | Cùng phim, cùng phòng có thể chiếu cả sáng lẫn chiều. Riêng giờ bắt đầu cũng trùng giữa nhiều phòng. DDL chỉ khai báo chỉ mục tìm lịch trên phòng và thời gian; kiểm tra chồng lịch là quy tắc riêng. |
| Một hồ sơ khách `customer` | `customer_id` | Điện thoại và email đều UNIQUE NOT NULL theo lược đồ. Khách có thể đổi thông tin liên lạc; ID giúp các đơn tiếp tục trỏ cùng hồ sơ. |
| Một nhân viên `employee` | `employee_id` | Hai nhân viên có thể trùng tên. Mã nhân viên và điện thoại là hai khóa dự tuyển khác theo DDL. |
| Một lần đặt `booking` | `booking_id` | Hai đơn khác nhau có thể cùng khách, cùng suất. Mã đơn `booking_code` là khóa dự tuyển dùng để tra cứu. Vé và thanh toán tham chiếu ID. |
| Một vé `ticket` | `ticket_id` | (booking_id, seat_id) cũng duy nhất vì một đơn không mua hai lần cùng ghế. Riêng seat_id trùng qua nhiều suất, riêng booking_id trùng trong đơn nhiều ghế. |
| Một ghế đang được giữ ở một suất `screening_seat_lock` | `(screening_id, seat_id)` | Riêng suất lặp khi bán nhiều ghế, riêng ghế lặp qua nhiều suất. Cặp thể hiện trực tiếp điều cần cấm trùng. ticket_id là khóa dự tuyển thứ hai do UNIQUE NOT NULL. |
| Một giao dịch thanh toán `payment` | `payment_id` | Một đơn có thể có nhiều lần trả tiền nên booking_id lặp. transaction_ref có thể NULL và không có UNIQUE. Cột sinh active_success_booking chỉ giới hạn số dòng SUCCESS của một đơn. |

## 4. Sáu câu cô có thể hỏi tiếp ngay sau câu đầu

1. **“FK đặt ở phòng thì biết phòng thuộc chi nhánh. Điều gì cho phép một chi nhánh có hai phòng?”** FK bắt buộc mỗi giá trị branch_id phải tham chiếu chi nhánh tồn tại. Cột đó ở phòng được phép lặp. Nếu thêm UNIQUE riêng trên branch_id, ta sẽ vô tình giới hạn mỗi chi nhánh tối đa một phòng.
2. **“Ghế và vé là 1–N, vậy hai khách mua A5 cùng suất được à?”** Quan hệ này mô tả lịch sử của chiếc ghế. Điều cần cấm là hai vé cùng hiệu lực cho cặp (suất, ghế). Hai đơn khác nhau đều có thể chứa A5 nếu chỉ xét UNIQUE(booking_id, seat_id); vì thế PK của bảng giữ ghế phải xét suất.
3. **“Đã có FK đến ghế và suất, sao cần trigger?”** Giả sử S1 thuộc phòng 1 nhưng A5 lại thuộc phòng 2. Cả hai mã đều tồn tại nên kiểm tra tồn tại riêng lẻ vẫn đạt. Cần so phòng của ghế với phòng của suất; trigger kiểm tra quy tắc liên bảng này.
4. **“Sao không để screening_id ngay trên vé để đỡ nối?”** Mỗi đơn có đúng một suất, tức booking_id → screening_id. Nếu lưu suất trên cả ba vé của một đơn thì có ba nơi có thể ghi lệch. Khi chiếu lại từ mô hình hiện tại, mỗi vé nối tới đúng một đơn, từ đơn tìm được đúng một suất.
5. **“Hễ có hai FK là bảng trung gian M–N à?”** Phải xem **một dòng có ý nghĩa gì**. movie_genre biểu diễn tư cách thành viên của một cặp phim–thể loại; cặp chỉ xuất hiện một lần. screening lại biểu diễn một sự kiện có giờ và giá, nên cùng cặp phim–phòng có thể xuất hiện nhiều lần.
6. **“Tại sao Chen không vẽ branch_id trong elip của phòng? Phòng có phải thực thể yếu?”** Trên Chen, đường sở hữu đã diễn đạt phòng thuộc chi nhánh. Khi ánh xạ mới thêm FK vào bảng phòng. Phòng bắt buộc thuộc chi nhánh nhưng có room_id định danh độc lập; tham gia bắt buộc không đồng nghĩa là thực thể yếu. Hai thực thể kết hợp trên sơ đồ của nhóm là movie_genre và screening_seat_lock.

**Tự vấn đáp trong 5 phút.** Nhờ một bạn chỉ ngẫu nhiên ba đường nối và hai khóa. Với mỗi đường, nói đủ hai chiều và một trường hợp bằng 0. Nếu phía đang xét bắt buộc, giải thích vì sao 0 bị cấm. Với mỗi khóa, thử bỏ một cột hoặc thay bằng tên rồi đưa ví dụ trùng. Kết thúc bằng câu “Ràng buộc nào trong SQL bảo vệ đúng điều em vừa nói?”.

Đối chiếu trong repo: [ký hiệu và quan hệ Chen](../erd/ER.drawio), [bản số logic](../erd/luoc-do-quan-he.drawio), [PK và UNIQUE](../../database/schema/01_schema.sql), [17 FK](../../database/schema/01b_foreign_keys.sql), [trigger](../../database/schema/02_triggers.sql).
