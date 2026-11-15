# Kế hoạch kiểm chứng bản nộp ngày15/11/2026

Môi trường: JDK21,MySQL Community Server8.4.0,cinema_chain_test,cinema_app. Demo cinema_chain chỉ được fingerprint trước/sau; mọi fixture ghi của tests/GUI/2-JVM chạy ở testDB.

1. Kiểm đúng instance và baseline demo.
2. Xóa XML cũ; Maven clean verify. Danh sách suite bắt buộc lấy từ mọi *Test/*IT trong source, không hardcode8suite cũ. Từ chối XML cũ,trùng,thiếu suite,fail/error/skip hoặc0test.
3. Thực thi năm màn hình: nhập đúng/sai,lịch chồng,ghế chưa chọn/đã giữ,ngày tìm kiếm/ngày báo cáo sai;xuất hai PDF.
4. Đóng/mở hai JVM;so IDs,ghế,trạng thái,giá và tổng báo cáo.
5. Chạy SQL chức năng R5–R7 trên test với rollback ví dụ ghi.
6. Đối chiếu lại SHA demo;chỉ ghi last-run.json nếu mọi gate hoàn thành.
7. Kiểm tĩnh ERD và công cụ Python;render Word/PDF,xem từng trang;đối chiếu SQL/tổng PDF.

Lệnh: `powershell -NoProfile -ExecutionPolicy Bypass -File database/verify_all.ps1`. Kết quả hiện hành tại `verification/last-run.json` và `verification/submission-2026-11-15/`.

Phạm vi bổ sung: booking2JVM/múi giờ;UIworker không chặnEDT;revenue snapshot khi writer commit giữa haiquery;PDF nhiều trang/tên dài/lỗi ghi;backup/restore/refreshtrigger trênclone và gatefail-closed.

Chưa chứng nhận merchantVNPay/refund thật,crashserver/mất điện hoặc tải100K/1M. Các quy tắc tuổi/quyền online chưa được nghiệm thu trong đồ án desktop.
