# Khác biệt trigger live và source

Chỉ đọc; không thực thi nội dung diff. Bỏ qua khoảng trắng và hoa/thường khi xác định đối tượng khác nhau.

## trg_booking_validate_upd

```diff
--- LIVE cinema_chain/trg_booking_validate_upd
+++ SOURCE database/schema/02_triggers.sql/trg_booking_validate_upd
@@ -1,5 +1,21 @@
 BEGIN
     DECLARE v_ticket_count INT DEFAULT 0;
+    DECLARE v_success_count INT DEFAULT 0;
+    DECLARE v_gateway_count INT DEFAULT 0;
+
+    IF OLD.booking_status = 'CANCELLED' AND NEW.booking_status <> 'CANCELLED' THEN
+        SIGNAL SQLSTATE '45000'
+            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: Khong duoc kich hoat lai don da huy.';
+    END IF;
+
+    IF NEW.booking_status = 'CONFIRMED' AND OLD.booking_status <> 'CONFIRMED' THEN
+        SELECT COUNT(*) INTO v_success_count FROM payment
+        WHERE booking_id = OLD.booking_id AND payment_status = 'SUCCESS';
+        IF v_success_count <> 1 THEN
+            SIGNAL SQLSTATE '45000'
+                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: Chi xac nhan don khi co mot thanh toan thanh cong.';
+        END IF;
+    END IF;
 
     IF NEW.screening_id <> OLD.screening_id THEN
         SELECT COUNT(*) INTO v_ticket_count
@@ -11,4 +27,20 @@
                 SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the doi suat chieu cua don dat ve da phat sinh ve.';
         END IF;
     END IF;
+
+    IF NEW.booking_status = 'CANCELLED' AND OLD.booking_status <> 'CANCELLED' THEN
+        SELECT COUNT(*) INTO v_gateway_count FROM payment
+        WHERE booking_id = OLD.booking_id AND payment_status = 'SUCCESS'
+          AND transaction_ref LIKE 'VNPAY-%';
+        IF v_gateway_count > 0 THEN
+            SIGNAL SQLSTATE '45000'
+                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: Can doi soat hoan tien VNPay truoc khi huy don.';
+        END IF;
+        UPDATE ticket SET ticket_status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP
+        WHERE booking_id = OLD.booking_id AND ticket_status = 'VALID';
+        UPDATE payment SET payment_status = 'REFUNDED'
+        WHERE booking_id = OLD.booking_id AND payment_status = 'SUCCESS';
+        UPDATE payment SET payment_status = 'FAILED'
+        WHERE booking_id = OLD.booking_id AND payment_status = 'PENDING';
+    END IF;
 END
```

## trg_payment_validate_ins

```diff
--- LIVE cinema_chain/trg_payment_validate_ins
+++ SOURCE database/schema/02_triggers.sql/trg_payment_validate_ins
@@ -1,10 +1,27 @@
 BEGIN
+    DECLARE v_booking_status VARCHAR(20) DEFAULT NULL;
+    DECLARE v_ticket_total DECIMAL(12,2) DEFAULT 0;
+
     IF NEW.amount <= 0 THEN
         SIGNAL SQLSTATE '45000'
             SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: So tien thanh toan phai lon hon 0.';
     END IF;
 
-    IF NEW.payment_status = 'SUCCESS' AND NEW.paid_at IS NULL THEN
-        SET NEW.paid_at = CURRENT_TIMESTAMP;
+    IF NEW.payment_status = 'SUCCESS' THEN
+        SELECT booking_status INTO v_booking_status
+        FROM booking WHERE booking_id = NEW.booking_id FOR UPDATE;
+        IF v_booking_status IS NULL OR v_booking_status = 'CANCELLED' THEN
+            SIGNAL SQLSTATE '45000'
+                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: Khong the thanh toan thanh cong cho don khong ton tai hoac da huy.';
+        END IF;
+        SELECT COALESCE(SUM(unit_price), 0) INTO v_ticket_total
+        FROM ticket WHERE booking_id = NEW.booking_id AND ticket_status = 'VALID' FOR UPDATE;
+        IF NEW.amount <> v_ticket_total THEN
+            SIGNAL SQLSTATE '45000'
+                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: So tien thanh toan phai khop tong tien ve hop le.';
+        END IF;
+        IF NEW.paid_at IS NULL THEN
+            SET NEW.paid_at = CURRENT_TIMESTAMP;
+        END IF;
     END IF;
 END
```

## trg_payment_validate_upd

```diff
--- LIVE cinema_chain/trg_payment_validate_upd
+++ SOURCE database/schema/02_triggers.sql/trg_payment_validate_upd
@@ -1,10 +1,27 @@
 BEGIN
+    DECLARE v_booking_status VARCHAR(20) DEFAULT NULL;
+    DECLARE v_ticket_total DECIMAL(12,2) DEFAULT 0;
+
     IF NEW.amount <= 0 THEN
         SIGNAL SQLSTATE '45000'
             SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: So tien thanh toan phai lon hon 0.';
     END IF;
 
-    IF NEW.payment_status = 'SUCCESS' AND NEW.paid_at IS NULL THEN
-        SET NEW.paid_at = CURRENT_TIMESTAMP;
+    IF NEW.payment_status = 'SUCCESS' THEN
+        SELECT booking_status INTO v_booking_status
+        FROM booking WHERE booking_id = NEW.booking_id FOR UPDATE;
+        IF v_booking_status IS NULL OR v_booking_status = 'CANCELLED' THEN
+            SIGNAL SQLSTATE '45000'
+                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: Khong the thanh toan thanh cong cho don khong ton tai hoac da huy.';
+        END IF;
+        SELECT COALESCE(SUM(unit_price), 0) INTO v_ticket_total
+        FROM ticket WHERE booking_id = NEW.booking_id AND ticket_status = 'VALID' FOR UPDATE;
+        IF NEW.amount <> v_ticket_total THEN
+            SIGNAL SQLSTATE '45000'
+                SET MESSAGE_TEXT = 'Loi nghiep vu [BR-13]: So tien thanh toan phai khop tong tien ve hop le.';
+        END IF;
+        IF NEW.paid_at IS NULL THEN
+            SET NEW.paid_at = CURRENT_TIMESTAMP;
+        END IF;
     END IF;
 END
```

## trg_screening_validate_ins

```diff
--- LIVE cinema_chain/trg_screening_validate_ins
+++ SOURCE database/schema/02_triggers.sql/trg_screening_validate_ins
@@ -22,9 +22,9 @@
             SET MESSAGE_TEXT = 'Loi nghiep vu [BR-05]: Khoang thoi gian suat chieu nho hon thoi luong phim.';
     END IF;
 
-    SELECT active INTO v_room_active
-    FROM screening_room
-    WHERE room_id = NEW.room_id;
+    SELECT (r.active AND b.active) INTO v_room_active
+    FROM screening_room r JOIN cinema_branch b ON b.branch_id = r.branch_id
+    WHERE r.room_id = NEW.room_id FOR UPDATE;
 
     IF v_room_active = 0 THEN
         SIGNAL SQLSTATE '45000'
@@ -37,7 +37,7 @@
         WHERE s.room_id = NEW.room_id
           AND s.status = 'OPEN'
           AND s.start_time < NEW.end_time
-          AND s.end_time > NEW.start_time;
+          AND s.end_time > NEW.start_time FOR SHARE;
 
         IF v_overlap_count > 0 THEN
             SIGNAL SQLSTATE '45000'

```

## trg_screening_validate_upd

```diff
--- LIVE cinema_chain/trg_screening_validate_upd
+++ SOURCE database/schema/02_triggers.sql/trg_screening_validate_upd
@@ -15,9 +15,9 @@
             SET MESSAGE_TEXT = 'Loi nghiep vu [BR-04]: Gia ve co so cua suat chieu phai lon hon 0.';
     END IF;
 
-    SELECT active INTO v_room_active
-    FROM screening_room
-    WHERE room_id = NEW.room_id;
+    SELECT (r.active AND b.active) INTO v_room_active
+    FROM screening_room r JOIN cinema_branch b ON b.branch_id = r.branch_id
+    WHERE r.room_id = NEW.room_id FOR UPDATE;
 
     IF v_room_active = 0 THEN
         SIGNAL SQLSTATE '45000'
@@ -65,7 +65,7 @@
           AND s.screening_id <> NEW.screening_id
           AND s.status = 'OPEN'
           AND s.start_time < NEW.end_time
-          AND s.end_time > NEW.start_time;
+          AND s.end_time > NEW.start_time FOR SHARE;
 
         IF v_overlap_count > 0 THEN
             SIGNAL SQLSTATE '45000'

```

## trg_ticket_validate_ins

```diff
--- LIVE cinema_chain/trg_ticket_validate_ins
+++ SOURCE database/schema/02_triggers.sql/trg_ticket_validate_ins
@@ -3,10 +3,11 @@
     DECLARE v_scr_room_id INT DEFAULT NULL;
     DECLARE v_scr_status VARCHAR(20) DEFAULT NULL;
     DECLARE v_seat_room_id INT DEFAULT NULL;
+    DECLARE v_booking_status VARCHAR(20) DEFAULT NULL;
 
-    SELECT screening_id INTO v_booking_scr_id
+    SELECT screening_id, booking_status INTO v_booking_scr_id, v_booking_status
     FROM booking
-    WHERE booking_id = NEW.booking_id;
+    WHERE booking_id = NEW.booking_id FOR SHARE;
 
     IF v_booking_scr_id IS NULL THEN
         SIGNAL SQLSTATE '45000'
@@ -31,6 +32,11 @@
             SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the phat hanh ve hop le cho suat chieu da bi huy.';
     END IF;
 
+    IF NEW.ticket_status = 'VALID' AND v_booking_status = 'CANCELLED' THEN
+        SIGNAL SQLSTATE '45000'
+            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the phat hanh ve hop le cho don da huy.';
+    END IF;
+
     IF NEW.ticket_status = 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
         SET NEW.cancelled_at = CURRENT_TIMESTAMP;
     END IF;

```

## trg_ticket_validate_upd

```diff
--- LIVE cinema_chain/trg_ticket_validate_upd
+++ SOURCE database/schema/02_triggers.sql/trg_ticket_validate_upd
@@ -3,10 +3,16 @@
     DECLARE v_scr_room_id INT DEFAULT NULL;
     DECLARE v_scr_status VARCHAR(20) DEFAULT NULL;
     DECLARE v_seat_room_id INT DEFAULT NULL;
+    DECLARE v_booking_status VARCHAR(20) DEFAULT NULL;
 
-    SELECT screening_id INTO v_booking_scr_id
+    IF NEW.unit_price <> OLD.unit_price THEN
+        SIGNAL SQLSTATE '45000'
+            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-12]: Khong duoc thay doi gia ve da phat hanh.';
+    END IF;
+
+    SELECT screening_id, booking_status INTO v_booking_scr_id, v_booking_status
     FROM booking
-    WHERE booking_id = NEW.booking_id;
+    WHERE booking_id = NEW.booking_id FOR SHARE;
 
     SELECT room_id, status INTO v_scr_room_id, v_scr_status
     FROM screening
@@ -26,6 +32,11 @@
             SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the khoi phuc ve hop le cho suat chieu da bi huy.';
     END IF;
 
+    IF NEW.ticket_status = 'VALID' AND v_booking_status = 'CANCELLED' THEN
+        SIGNAL SQLSTATE '45000'
+            SET MESSAGE_TEXT = 'Loi nghiep vu [BR-08]: Khong the kich hoat lai ve cua don da huy.';
+    END IF;
+
     IF NEW.ticket_status = 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
         SET NEW.cancelled_at = CURRENT_TIMESTAMP;
     END IF;

```
