# KỊCH BẢN THUYẾT TRÌNH & DEMO BẢO VỆ BÀI TẬP LỚN CSDL (15 PHÚT)
**(Đề tài: Hệ thống Quản lý Chuỗi Rạp Chiếu Phim CineViet — MySQL Community Server 8.4.0 LTS • Java 21 Swing)**

---

## 0. Chuẩn bị Kỹ thuật Trước Giờ Bảo vệ (5 phút trước buổi trình bày)

### 0.1. Khởi động đúng instance MySQL 8.4.0 LTS
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/start_mysql84.ps1
```

### 0.2. Khởi chạy Ứng dụng Demo (`com.cinema.app.Main`) trên `cinema_chain`
CSDL demo chính thức **`cinema_chain`** đã được hoàn tất với **lược đồ 14 bảng chuẩn 3NF** (`17` FK, `12` Trigger, `4` View) và cấu hình sẵn trong `config/application.properties` (kết nối qua tài khoản phân quyền tối thiểu `cinema_app`).
- **Mở ứng dụng bình thường (Giữ nguyên toàn bộ dữ liệu đã nhập):**
  ```powershell
  .\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"
  ```
  *Lưu ý quan trọng:* Khởi động ứng dụng bình thường chạy ở chế độ `NORMAL_READ_ONLY_INIT` (`DestructiveReset=DISABLED`), **không bao giờ tự xóa hay reset dữ liệu**. Khi đóng ứng dụng và mở lại, mọi suất chiếu và đơn đặt vé vừa thao tác (như Suất `#161` ngày `2026-11-18`, Đơn `#161` `CONFIRMED` ghế `A01, E01`, Đơn `#162` `CANCELLED`) vẫn được bảo toàn nguyên vẹn 100%.
- **Chỉ khi muốn chủ động làm mới lại `cinema_chain` về mốc dữ liệu mẫu gốc sạch trước buổi bảo vệ (`35.165.000 VNĐ`):**
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File database/prepare_demo_db.ps1
  ```
  *(Script sẽ yêu cầu nhập xác nhận tường minh `'PREPARE_DEMO_DB'` hoặc truyền `-ConfirmYes`; chỉ thao tác trên duy nhất `cinema_chain` và giữ nguyên mọi CSDL khác).*

### 0.3. Checklist Diễn tập Thao tác Chuột & Bàn phím Vật lý (3 phút trước giờ gọi tên)
*(Kiểm thử tự động trên Swing EDT đã xác minh logic giao diện, nhưng người thuyết trình cần tự tay bấm thử 1 lượt trên màn hình máy chiếu để kiểm tra độ phân giải DPI và tổ hợp phím)*:
- [ ] Mở sẵn `docs/academic-report/BaoCao_QuanLyChuoiRap.pdf`, `docs/erd/erd-chen.png`, `docs/erd/erd-logical.png` và MySQL CLI / Workbench.
- [ ] Bấm nút `Trang sau »` và `« Trang trước` ở Màn hình 1 và Màn hình 3 để thấy thanh phân trang `50 dòng/trang`.
- [ ] Thử chọn nhanh Chi nhánh + Ngày chiếu trên Màn hình 2 và click chuột chọn/bỏ chọn 2 ghế trên sơ đồ 80 ghế.
- [ ] Mở sẵn thư mục `reports/exports/` để mở ngay tệp PDF vừa xuất khi Giảng viên yêu cầu.

---

## KỊCH BẢN PHÂN BỔ THỜI GIAN CHÍNH XÁC 15 PHÚT (`2 + 3 + 2 + 3 + 1 + 2 + 2 = 15p`)

### Phần 1 (Phút 00:00 – 02:00 | 2 phút): Giới thiệu Bài toán & Dữ liệu Cần Lưu trữ (`R1 — 1,0 điểm`)
- **Nội dung trình bày:**
  1. Bài toán quản lý vận hành chuỗi rạp **CineViet** đa chi nhánh (4 cụm rạp tại Hà Nội, TP.HCM, Đà Nẵng, Hải Phòng; 12 phòng chiếu `2D`/`3D`/`IMAX`; 960 ghế vật lý chia 3 hạng `STD`, `VIP`, `COUPLE`).
  2. Ba nhóm tác nhân vận hành trực tiếp (`CASHIER` Thu ngân, `MANAGER` Quản lý chi nhánh, `ACCOUNTANT` Kế toán) và hai cấp nhận báo cáo (`Giám đốc Kinh doanh`, `Ban Điều hành`).
  3. Tổ chức dữ liệu thành 3 cụm nghiệp vụ rõ ràng (Hạ tầng rạp & ghế; Phim, thể loại & lịch chiếu; Khách hàng, nhân viên, đơn đặt vé, vé, khóa ghế hiệu lực & giao dịch thanh toán) với quy mô mẫu thực tế: `20 phim`, `160 suất chiếu`, `160 đơn đặt vé`, `373 vé`, `175 giao dịch thanh toán`.

---

### Phần 2 (Phút 02:00 – 05:00 | 3 phút): Trình bày ERD & Ánh xạ Mô hình Quan hệ (`R2 = 2,5đ & R3 = 2,0đ`)
- **Tài liệu mở trên màn hình:** `docs/erd/erd-chen.png`, `docs/erd/erd-logical.png` và Chương 2–3 của báo cáo.
- **Điểm nhấn học thuật cần chỉ rõ cho Giảng viên:**
  1. **Sơ đồ Chen & Crow's Foot khớp 1–1 với 14 bảng và 17 khóa ngoại** trong `01_schema.sql` & `01b_foreign_keys.sql`.
  2. Giải thích các bản số quan trọng:
     - `movie` — `genre` ($M:N$) ánh xạ thành bảng `movie_genre(movie_id, genre_id)`.
     - `customer` (`0..1`) — `booking` (`0..N`): `customer_id` cho phép `NULL` để biểu diễn đúng nghiệp vụ **Khách vãng lai mua trực tiếp tại quầy** (`ON DELETE SET NULL`).
     - **`booking` (`1`) — `ticket` (`1..N`)**: `ticket` **không** nối trực tiếp dư thừa sang `screening` mà kế thừa suất chiếu qua `booking.screening_id`.
     - **Thực thể kết hợp `screening_seat_lock(screening_id, seat_id, ticket_id)`**: bản số `ticket (1) — (0..1) screening_seat_lock` (vé `VALID` giữ đúng 1 khóa ghế; vé `CANCELLED` có 0 khóa ghế).
  3. Phân biệt rõ mức logic quan hệ với mức cài đặt vật lý MySQL 8.4 (cột biểu thức chỉ mục `active_success_booking` trên `payment` chỉ phục vụ ràng buộc tập con `SUCCESS`, không phải thuộc tính logic hay khóa dự tuyển của bảng).

---

### Phần 3 (Phút 05:00 – 07:00 | 2 phút): Kiểm tra và Chuẩn hóa 3NF — Nhấn mạnh Bảng `ticket` (`R4 — 1,0 điểm`)
- **Tài liệu mở trên màn hình:** Chương 4 (`docs/normalization/normalization.md`).
- **Lập luận cốt lõi ghi điểm tối đa:**
  1. **Phát hiện và xử lý vi phạm 3NF thực sự trên `ticket`:**
     - Nếu đặt `screening_id` trong `ticket`, vì mỗi đơn đặt vé chỉ thuộc một suất chiếu ($\text{booking\_id} \to \text{screening\_id}$) nhưng một đơn mua nhiều ghế ($\text{booking\_id}$ không phải siêu khóa của `ticket`) và `screening_id` không phải thuộc tính khóa, ta sẽ có phụ thuộc bắc cầu $\text{ticket\_id} \to \text{booking\_id} \to \text{screening\_id}$ (đồng thời phụ thuộc bộ phận vào khóa thay thế $\{\text{booking\_id}, \text{seat\_id}\}$). Dùng Trigger đồng bộ không hề xóa được phụ thuộc hàm này!
  2. **Cách chuẩn hóa tối ưu đã thực hiện:**
     - Loại bỏ `screening_id` khỏi `ticket` $\Rightarrow$ `ticket(ticket_id, booking_id, seat_id, unit_price, ticket_status, cancelled_at)` có 2 khóa dự tuyển toàn bảng là $\{\text{ticket\_id}\}$ và $\{\text{booking\_id}, \text{seat\_id}\}$, đạt **3NF & BCNF**.
     - Chứng minh **Lossless-Join** theo Định lý Heath: $R_{\text{ticket}} \cap R_{\text{booking}} = \{\text{booking\_id}\} \to R_{\text{booking}}$.
     - Tách bảng chuẩn 3NF `screening_seat_lock(screening_id, seat_id, ticket_id)` với `PK(screening_id, seat_id)` và `UQ(ticket_id)` $\Rightarrow$ Mọi thuộc tính đều là thuộc tính khóa, đạt **3NF & BCNF**, đồng thời chống bán trùng ghế ngay ở mức khóa chính B-Tree InnoDB và cho phép giải phóng ghế khi hủy vé!
  3. Giải thích nhanh vì sao `ticket.unit_price` là giá chốt lịch sử lúc giao dịch ($\text{ticket\_id} \to \text{unit\_price}$), không vi phạm 3NF.

---

### Phần 4 (Phút 07:00 – 10:00 | 3 phút): Demo 2 Form Nhập liệu — Cả Dữ liệu Đúng & Sai (`R5 — 1,0 điểm`)

#### 4.1. Form 1 — Màn hình `1. Quản lý Suất chiếu` (1,5 phút)
1. **Demo kiểm tra dữ liệu SAI (bị từ chối):**
   - Nhập giá cơ sở `0` hoặc giờ kết thúc ngắn hơn thời lượng phim (`09:00` $\to$ `10:00` cho phim 105 phút) $\rightarrow$ Hệ thống từ chối bằng tiếng Việt (`[BR-04]`, `[BR-05]`).
   - Nhập khung giờ giao thoa với Suất `#1` tại Phòng 01 ngày `2026-11-04` (`13:30` $\to$ `15:50`) $\rightarrow$ Bị từ chối ngay với mã `[BR-06]` chống trùng lịch phòng.
   - Chọn Suất `#1` (đã bán vé), thử đổi sang Phòng 02 và bấm **Lưu Cập nhật** $\rightarrow$ Bị chặn ngay với mã `[BR-09]`.
2. **Demo nhập dữ liệu ĐÚNG (thành công):**
   - Chọn Chi nhánh Hà Nội, Phòng 01, Phim *"Đào, Phở và Piano"* (105 phút), ngày `2026-11-18`, giờ bắt đầu `09:00`, bấm **"Tính giờ kết thúc (+20p dọn phòng)"** (`11:05`), giá `95000`, bấm **"+ Thêm Suất chiếu mới"** $\rightarrow$ Tạo mới thành công và hiển thị rõ trên bảng phân trang `50 dòng/trang`.

#### 4.2. Form 2 — Màn hình `2. Đặt vé & Chọn ghế` (1,5 phút)
1. Dùng bộ lọc nhanh chọn Chi nhánh Hà Nội $\rightarrow$ chọn suất chiếu vừa tạo (hoặc Suất `#1`).
2. **Demo kiểm tra dữ liệu SAI:** Bấm **"Xác nhận Đặt vé"** khi chưa chọn ghế nào trên sơ đồ $\rightarrow$ Hệ thống từ chối (`[BR-08]`). (Hoặc chỉ vào ghế màu xám đã bán không cho phép chọn trùng `[BR-10]`).
3. **Demo nhập dữ liệu ĐÚNG & Hủy hoàn tiền:**
   - Chọn 2 ghế trống (1 ghế `STD` + 1 ghế `VIP`) $\rightarrow$ Đơn giá tính tự động `base_price + surcharge`, chọn Khách hàng thành viên (hoặc *Khách vãng lai tại quầy*), phương thức `CARD` $\rightarrow$ Bấm **"Xác nhận Đặt vé & Xuất vé"** $\rightarrow$ Tạo nguyên tử `booking` + `ticket` + `screening_seat_lock` + `payment`, 2 ghế đổi màu đã bán.
   - Chọn đơn vừa đặt ở bảng dưới, bấm **"Hủy Đơn đang chọn & Giải phóng ghế"** $\rightarrow$ Đơn chuyển sang `CANCELLED`, thanh toán sang `REFUNDED`, khóa ghế trong `screening_seat_lock` được xóa nên 2 ghế mở trống trở lại ngay lập tức!

---

### Phần 5 (Phút 10:00 – 11:00 | 1 phút): Demo Màn hình Tìm kiếm & Phân trang (`R6 — 0,5 điểm`)
- Chuyển sang **`3. Tra cứu Suất chiếu`**:
  1. Thử nhập ngày sai định dạng (`2026-99-99`) $\rightarrow$ Báo lỗi kiểm tra hợp lệ tiếng Việt.
  2. Lọc theo Chi nhánh Hà Nội, ngày `2026-11-04`, khung giờ `08:00` – `23:00`, tích chọn *"Chỉ suất đang mở & còn ghế trống"* $\rightarrow$ Hiển thị tức thì 6 suất chiếu kèm sức chứa `80`, số vé đã giữ, số ghế trống (`available_seats`) khớp các thẻ KPI tổng hợp và thanh phân trang `50 dòng/trang`.

---

### Phần 6 (Phút 11:00 – 13:00 | 2 phút): Demo 2 Báo cáo Xem trên Màn hình, Xuất PDF & Đóng/Mở Ứng dụng (`R7 — 1,0 điểm`)
1. **Màn hình `4. Báo cáo Doanh thu` (1 phút):**
   - Chỉ rõ trên tiêu đề và chân chữ ký: **Người lập: Kế toán Chuỗi rạp** $\rightarrow$ **Người nhận: Giám đốc Kinh doanh Chuỗi rạp**.
   - Kỳ `2026-11-01` $\to$ `2026-11-18`: bấm **Xem trước Báo cáo (Preview)** (trên mốc seed gốc là `125 đơn`, `292 vé`, `35.165.000 VNĐ`; sau khi cộng Đơn demo `#161` `A01+E01 = 210.000 VNĐ` trên `cinema_chain` là **`126 đơn`, `294 vé`, `35.375.000 VNĐ`** — giải thích rõ chỉ tính đơn `CONFIRMED` + thanh toán `SUCCESS`, không bị nhân đôi bởi 15 đơn có retry `FAILED`, và loại trừ hoàn toàn đơn `#162` vừa hủy hoàn tiền `REFUNDED`). Bấm **Xuất Báo cáo ra PDF** và mở file PDF tiếng Việt (`BaoCaoDoanhThu_CinemaChain_Demo.pdf` / `BaoCaoDoanhThu_Mau.pdf`).
2. **Màn hình `5. Báo cáo Tình hình Bán vé` (40 giây):**
   - Chỉ rõ: **Người lập: Quản lý Chi nhánh** $\rightarrow$ **Người nhận: Ban Điều hành và Quản lý Kinh doanh**.
   - Kỳ `2026-11-04` $\to$ `2026-11-18`: bấm **Xem trước Báo cáo (Preview)** (`161 suất chiếu`, `294 / 12.880 ghế`, lấp đầy TB `2.28%`, doanh thu `35.375.000 VNĐ`) và mở file PDF (`BaoCaoBanVe_CinemaChain_Demo.pdf` / `BaoCaoBanVe_Mau.pdf`).
3. **Chứng minh giữ nguyên dữ liệu khi đóng/mở ứng dụng (20 giây):**
   - Đóng cửa sổ ứng dụng rồi chạy lại `.\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.app.Main"` để Giảng viên thấy trực tiếp Suất chiếu mới và Đơn đặt vé vừa nhập trên `cinema_chain` vẫn còn nguyên vẹn 100%.

---

### Phần 7 (Phút 13:00 – 15:00 | 2 phút): Dự phòng Hỏi – Đáp Kỹ thuật (`R8 — Transaction, Concurrency & SQL`)
Chuẩn bị sẵn câu trả lời ngắn gọn kèm tệp đối chiếu cho 4 câu hỏi vấn đáp thường gặp nhất:
1. **"Hai thu ngân bấm đặt cùng 1 ghế cùng lúc thì sao?"**
   - Mở `BookingService.java` & `01_schema.sql`: Lớp 1 khóa bi quan `SELECT ... FROM screening WHERE screening_id = ? FOR UPDATE`; Lớp 2 khóa chính vật lý `PRIMARY KEY (screening_id, seat_id)` trên bảng `screening_seat_lock` (đã kiểm chứng bằng bài test `BookingConcurrencyIT` chạy **20 luồng đồng thời** qua `CountDownLatch`: đúng 1 luồng thắng, 19 luồng rollback sạch, 0 bản ghi mồ côi).
2. **"Hai quản lý lập lịch chồng giờ cùng 1 phòng cùng lúc thì sao?"**
   - Mở `ScreeningRepository.java` & `02_triggers.sql`: Khóa dòng phòng chiếu `SELECT room_id, active FROM screening_room WHERE room_id = ? FOR UPDATE` + Trigger `trg_screening_validate_ins/upd` (đã kiểm chứng **12 luồng CREATE** và **8 luồng UPDATE** đồng thời trong `ScreeningConcurrencyIT`).
3. **"Tại sao đơn có 1 lần quẹt thẻ lỗi `FAILED` rồi thanh toán lại `SUCCESS` không làm nhân đôi doanh thu?"**
   - Mở `docs/sql-features/functional-sql-mapping.md` & `03_views.sql`: Bảng `payment` có chỉ mục duy nhất có điều kiện `uq_payment_one_success` đảm bảo tối đa 1 dòng `SUCCESS` cho mỗi `booking_id`, và truy vấn đối soát dùng `EXISTS (SELECT 1 FROM payment WHERE ... AND payment_status = 'SUCCESS')` (đã kiểm chứng trên 15 đơn retry và Known-Answer Fixture trong `ReportAccuracyIT`).
4. **"Các câu lệnh SQL của 5 màn hình nằm ở đâu?"**
   - Mở Chương 5 (Mục 5.7) hoặc Phụ lục D1 trong `BaoCao_QuanLyChuoiRap.pdf` và tệp `database/queries/functional_sql_verification.sql`.
