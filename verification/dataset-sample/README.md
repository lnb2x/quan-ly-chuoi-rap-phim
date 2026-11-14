# Kiểm thử bằng Cinema Sample Database 1K

Nguồn: https://databasesample.com/datasets/cinema-sample-database (CC0).
Đây là bài kiểm thử bổ sung, chạy tường minh, không thay dữ liệu mẫu hoặc đáp án
cố định của bộ kiểm thử mặc định. Yêu cầu MySQL 8.4 đang chạy, schema test đã có
14 bảng, trigger, view và các loại ghế STD/VIP của dự án.

## Chạy lại tại gốc repo bằng PowerShell

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/start_mysql84.ps1
python tools/db_fingerprint.py --db cinema_chain --file verification/dataset-sample/cinema_chain_before.json --mode capture --use-root
curl.exe --max-time 40 --retry 2 --fail --location 'https://database-sample-website.s3.eu-north-1.amazonaws.com/datasets/cinema-sample-database/v2/flagship/1k/csv.zip' -o verification/dataset-sample/source-csv.zip
python tools/prepare_dataset_sample.py
.\mvnw.cmd '-Dtest=CinemaDatasetCheck' test
python tools/db_fingerprint.py --db cinema_chain --file verification/dataset-sample/cinema_chain_before.json --mode verify --use-root
```

Lưu baseline mới vào tên khác nếu cần giữ bằng chứng của lượt trước. Dừng khi một
lệnh thất bại; không dùng kết quả cũ để kết luận lượt chạy mới thành công.
Fingerprint dùng truy vấn chỉ đọc; bài test dùng `TestDatabaseGuard` và
`cinema_app`, chỉ chấp nhận `cinema_chain_test`. Không chạy SQL tải từ mạng.

## Dữ liệu và phạm vi

- Nhập 12 chi nhánh, 60 phòng, 10.800 ghế, 120 phim, 80 khách hàng, 15 suất,
  450 đơn và 1.000 vé, cùng 12 nhân viên giả lập và 450 thanh toán: 12.999 dòng.
- Giữ lịch sử ngày giờ; `completed/succeeded` chuyển thành `CONFIRMED/SUCCESS`.
- Giá vé nhân 1.000 chỉ tạo đơn vị tiền kiểm thử, không phải tỷ giá USD/VND.
  Tổng tiền vé kỳ vọng: **13.742.820,00**.
- Payment được tạo lại bằng tổng giá vé trong đơn; bỏ bắp nước và khuyến mãi.
  Không khẳng định tổng payment sau chuyển đổi bằng transactions của nguồn.
- Premium/Recliner chuyển thành VIP; Standard/Accessible thành STD. Giữ giá vé
  lịch sử, không kiểm tra hệ số giá nguồn bằng công thức phụ thu hiện tại.
- Phân loại tuổi P, địa chỉ, điện thoại/email và nhân viên là dữ liệu giả lập;
  không kiểm thử tương đương phân loại tuổi, khả năng tiếp cận hoặc đa chuỗi.
- ID nguồn cộng 1.000.000, mã/tên phim có tiền tố DS1K. PK collision làm rollback
  toàn bộ lượt nhập, không ghi đè bản ghi đang có.
- Giữ nguyên FK/CHECK/trigger. Sau kiểm thử, hủy vé để giải phóng khóa rồi xóa
  đúng các ID đã nhập trong transaction. Auto-increment của **DB test** có thể
  tăng; không khôi phục bộ đếm. Nếu JVM bị kill hoặc cleanup lỗi, xem log trước
  khi chạy lại; không tự xóa bản ghi trùng ID.

## Các bài kiểm thử

`CinemaDatasetCheck` có 3 bài, không chạy mặc định và không truy cập mạng:

1. Báo cáo doanh thu và bán vé từng chi nhánh khớp ledger tính độc lập từ CSV;
   đối chiếu số đơn, số vé và tổng tiền.
2. Tra cứu 15 suất với 4 dòng/trang, không lặp/mất suất; tổng ghế vật lý theo
   từng suất là 2.734, ghế đã giữ là 1.000, còn trống là 1.734.
3. Ghế đã nhập bị ứng dụng từ chối bán lại; chèn SQL cho đơn khác cũng bị PK
   của `screening_seat_lock` chặn (MySQL error 1062).

Bằng chứng: `manifest.json` (SHA-256 nguồn, số dòng và phép chuyển đổi),
`expected.properties`, `dataset-test.log`, `TEST-CinemaDatasetCheck.xml` và
`cinema_chain_before.json`. Bộ test hồi quy/GUI/PDF gốc được chạy riêng bằng
`database/verify_all.ps1`; kết quả đó không chứng minh GUI đã chạy trên dataset.

Chưa kiểm thử quy mô 100K/1M hoặc tốc độ dưới tải. Đây là projection của dataset
sang nghiệp vụ vé hiện tại, không phải nhập nguyên trạng cả 30 bảng.
