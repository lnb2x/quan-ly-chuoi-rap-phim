# Cập nhật trigger CSDL demo, bảo toàn dữ liệu

Người dùng đã xác nhận tường minh: **“Đồng ý cập nhật trigger, giữ nguyên dữ liệu”**.

Đã chạy `python database/migrations/refresh_triggers.py --db cinema_chain --maintenance-confirmed` với token xác nhận tương ứng, sau khi rehearsal clone thành công và các ứng dụng/client ghi đã đóng.

Kết quả thực thi: chỉ cập nhật 12 trigger. Cả 14 bảng giữ nguyên số dòng, SHA dữ liệu và SHA DDL; SHA các view không đổi. 12 trigger live khớp source. Đối soát độc lập 17 invariant đều bằng 0; doanh thu view, truy vấn vé độc lập, tổng payment SUCCESS và occupancy khớp nhau.

Overall SHA thay đổi do định nghĩa trigger; không dùng overall SHA cũ làm baseline sau upgrade. `before.json` giữ nguyên mốc trước cập nhật; `previous-operational-baseline.json` lưu baseline vận hành cũ nếu có. Baseline vận hành đã được cập nhật có kiểm soát sang `new-operational-baseline.json`.

Bản sao lưu dữ liệu đầy đủ chỉ nằm trong `target/demo-backup/`; không đưa vào thư mục nộp. Thư mục này có hash, metadata, recovery SQL và log. Không chạy reset, seed, sửa bảng, sửa view hay DML dữ liệu demo. Không thay đổi CSDL kiểm thử.
