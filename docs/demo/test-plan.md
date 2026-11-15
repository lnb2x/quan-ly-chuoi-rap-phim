# Kế hoạch kiểm thử

Môi trường: JDK 21, MySQL Community Server 8.4, tài khoản ứng dụng `cinema_app`. Các kiểm thử có ghi dữ liệu phải dùng `cinema_chain_test`.

1. Kiểm tra đúng instance MySQL và chụp fingerprint của database demo.
2. Chạy Maven với profile `mysql-it`; kiểm tra XML mới, đủ mọi lớp `*Test`/`*IT`, không failure/error/skip.
3. Thử hai form nhập với dữ liệu hợp lệ và các trường hợp bị từ chối: thiếu ghế, ghế đã giữ, thời lượng sai, giá sai, lịch chồng.
4. Thử bộ lọc tìm kiếm, số dòng và phân trang.
5. Đóng/mở ứng dụng qua hai JVM, đối chiếu dữ liệu và giá.
6. Đối chiếu hai báo cáo với SQL độc lập; kiểm tra PDF tiếng Việt và phân trang.
7. Đối chiếu lại fingerprint demo; chỉ ghi manifest thành công khi mọi bước hoàn tất.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/verify_all.ps1
python -X utf8 -B tools/verify_erd.py
python -X utf8 -B -m unittest discover -s tools/tests -v
```

Log và XML được sinh cục bộ trong `verification/`; kết quả lần chạy đã ghi nhận nằm ở `docs/demo/verification-results.md`.

Các kiểm thử bổ sung gồm định danh đơn qua hai JVM, múi giờ, snapshot doanh thu khi có writer đồng thời, tác vụ Swing không chặn EDT và lỗi khi ghi PDF. Kiểm thử dataset bổ sung dùng `verification/dataset-sample/fixture.tsv` và `expected.properties`.

Chưa chứng nhận thanh toán/hoàn tiền thật, tải lớn hoặc phục hồi sau mất điện.
