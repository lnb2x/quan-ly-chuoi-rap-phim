package com.cinema.integration;

import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.screening.ScreeningService;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.validation.BusinessRuleException;
import com.cinema.support.TestDatabaseGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Đồng thời Đặt vé, Hủy vé & Bán lại ghế và Chuyển tiếp Trạng thái Thanh toán
 * (Gate C & Gate D — Concurrent Booking, Cancellation, Resale & Payment Transitions).
 */
class BookingConcurrencyIT {

    private static ConnectionProvider connectionProvider;
    private static ScreeningService screeningService;
    private static BookingService bookingService;

    @BeforeAll
    static void init() {
        connectionProvider = TestDatabaseGuard.verifyAndCreateTestProvider();
        TransactionTemplate tx = new TransactionTemplate(connectionProvider);
        screeningService = new ScreeningService(new ScreeningRepository(connectionProvider), tx);
        bookingService = new BookingService(new BookingRepository(connectionProvider), tx);
    }

    @Test
    @DisplayName("20 luồng đồng thời đặt cùng 1 ghế, sau đó hủy đơn để bán lại ghế và kiểm chứng chuyển trạng thái thanh toán [BR-10, BR-11, BR-13]")
    void concurrentSeatBookingThenCancelAndResellAndPaymentTransitionMustSucceed() throws Exception {
        int roomId = 1; // Phòng 1 có ghế seat_id 1..80
        int targetSeatId = 45; // Ghế E05 (VIP) thuộc Phòng 1
        int secondSeatId = 46; // Ghế E06 (VIP) thuộc Phòng 1

        cleanUpTestScreening();

        // Tạo 1 suất chiếu mới riêng cho bài test đồng thời vào ngày 2026-12-25
        int testScreeningId = screeningService.createScreening(
                1,
                roomId,
                LocalDateTime.of(2026, 12, 25, 18, 0),
                LocalDateTime.of(2026, 12, 25, 20, 15),
                new BigDecimal("100000.00")
        );

        try {
            // 1. Chạy 20 luồng đồng thời cùng đặt ghế targetSeatId (#45)
            int threads = 20;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch startSignal = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);
            AtomicInteger winningBookingId = new AtomicInteger(0);

            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int customerId = (i % 40) + 1;
                tasks.add(() -> {
                    ready.countDown();
                    startSignal.await();
                    try {
                        int bId = bookingService.createBooking(
                                testScreeningId,
                                customerId,
                                3,
                                List.of(targetSeatId),
                                "CARD",
                                "SUCCESS"
                        );
                        winningBookingId.set(bId);
                        successCount.incrementAndGet();
                    } catch (BusinessRuleException expected) {
                        rejectedCount.incrementAndGet();
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            ready.await();
            startSignal.countDown();
            for (Future<Void> f : futures) {
                f.get();
            }
            pool.shutdown();

            assertEquals(1, successCount.get(), "Chỉ đúng 01 luồng được phép đặt thành công ghế #" + targetSeatId);
            assertEquals(threads - 1, rejectedCount.get(), "19 luồng đồng thời còn lại bắt buộc phải bị từ chối");
            assertTrue(winningBookingId.get() > 0);
            assertEquals(1, countTicketsByStatus(testScreeningId, targetSeatId, "VALID"));
            assertEquals(1, countActiveSeatLocks(testScreeningId, targetSeatId),
                    "Bảng khóa ghế 3NF screening_seat_lock phải giữ đúng 1 dòng khóa cho ghế VALID");
            // Kiểm chứng không để lại bất kỳ booking hay payment dở dang nào từ 19 giao dịch bị từ chối
            assertEquals(1, countBookingsForScreening(testScreeningId),
                    "Tuyệt đối không được để lại booking dở dang sau khi 19 luồng đồng thời bị từ chối");
            assertEquals(1, countPaymentsForScreening(testScreeningId),
                    "Tuyệt đối không được để lại payment dở dang sau khi 19 luồng đồng thời bị từ chối");

            // 2. Hủy đơn đặt vé vừa thắng (Cancellation) -> Thanh toán chuyển sang REFUNDED, vé chuyển sang CANCELLED
            bookingService.cancelBooking(winningBookingId.get());
            assertEquals(0, countTicketsByStatus(testScreeningId, targetSeatId, "VALID"),
                    "Sau khi hủy đơn, số vé VALID của ghế phải về 0");
            assertEquals(0, countActiveSeatLocks(testScreeningId, targetSeatId),
                    "Sau khi hủy đơn, khóa ghế trong screening_seat_lock phải được giải phóng về 0");
            assertEquals(1, countTicketsByStatus(testScreeningId, targetSeatId, "CANCELLED"),
                    "Lịch sử vé CANCELLED phải được giữ nguyên");
            assertEquals("REFUNDED", getPaymentStatus(winningBookingId.get()),
                    "Thanh toán của đơn đã trả tiền sau khi hủy phải chuyển sang REFUNDED");

            // 3. Bán lại chính ghế targetSeatId (#45) vừa hủy cho khách vãng lai (customer_id = NULL)
            int resoldBookingId = bookingService.createBooking(
                    testScreeningId,
                    null, // Khách vãng lai
                    3,
                    List.of(targetSeatId),
                    "CASH",
                    "SUCCESS"
            );
            assertTrue(resoldBookingId > winningBookingId.get());
            assertEquals(1, countTicketsByStatus(testScreeningId, targetSeatId, "VALID"),
                    "Ghế sau khi hủy phải được bán lại thành công (1 vé VALID)");
            assertEquals(1, countActiveSeatLocks(testScreeningId, targetSeatId),
                    "Ghế bán lại thành công phải có đúng 1 khóa hiệu lực mới trong screening_seat_lock");
            assertEquals(1, countTicketsByStatus(testScreeningId, targetSeatId, "CANCELLED"),
                    "Vẫn bảo toàn 1 bản ghi lịch sử vé CANCELLED trước đó");

            // 4. Kiểm chứng chuyển tiếp trạng thái thanh toán: PENDING -> SUCCESS
            int pendingBookingId = bookingService.createBooking(
                    testScreeningId,
                    2,
                    3,
                    List.of(secondSeatId),
                    "TRANSFER",
                    "PENDING"
            );
            assertEquals("PENDING", getPaymentStatus(pendingBookingId));
            bookingService.transitionPendingPayment(pendingBookingId, "SUCCESS");
            assertEquals("SUCCESS", getPaymentStatus(pendingBookingId));

            // 4b. Kiểm chứng chuyển tiếp PENDING -> FAILED: hủy đơn, giải phóng ghế để bán lại
            int failedPendingBookingId = bookingService.createBooking(
                    testScreeningId,
                    3,
                    3,
                    List.of(47),
                    "EWALLET",
                    "PENDING"
            );
            bookingService.transitionPendingPayment(failedPendingBookingId, "FAILED");
            assertEquals("FAILED", getPaymentStatus(failedPendingBookingId));
            assertEquals(0, countTicketsByStatus(testScreeningId, 47, "VALID"),
                    "Khi thanh toán PENDING thất bại (FAILED), ghế phải được giải phóng ngay");

            // 5. Kiểm thử hồi quy truy vấn lịch sử đơn đặt vé theo suất chiếu (listRecentBookings)
            assertFalse(bookingService.listRecentBookings(testScreeningId, 20).isEmpty(),
                    "Truy vấn danh sách đơn đặt vé theo suất chiếu phải trả về kết quả hợp lệ");
            assertFalse(bookingService.listRecentBookings(null, 20).isEmpty(),
                    "Truy vấn danh sách đơn đặt vé toàn hệ thống phải trả về kết quả hợp lệ");
        } finally {
            cleanUpTestScreening();
        }
    }

    private int countBookingsForScreening(int screeningId) throws Exception {
        String sql = "SELECT COUNT(*) FROM booking WHERE screening_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, screeningId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int countPaymentsForScreening(int screeningId) throws Exception {
        String sql = """
                SELECT COUNT(*)
                FROM payment pm
                JOIN booking bk ON bk.booking_id = pm.booking_id
                WHERE bk.screening_id = ?
                """;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, screeningId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int countTicketsByStatus(int screeningId, int seatId, String status) throws Exception {
        String sql = """
                SELECT COUNT(*)
                FROM ticket tk
                JOIN booking bk ON bk.booking_id = tk.booking_id
                WHERE bk.screening_id = ?
                  AND tk.seat_id = ?
                  AND tk.ticket_status = ?
                """;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, screeningId);
            ps.setInt(2, seatId);
            ps.setString(3, status);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int countActiveSeatLocks(int screeningId, int seatId) throws Exception {
        String sql = "SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id = ? AND seat_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, screeningId);
            ps.setInt(2, seatId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private String getPaymentStatus(int bookingId) throws Exception {
        String sql = "SELECT payment_status FROM payment WHERE booking_id = ? ORDER BY payment_id DESC LIMIT 1";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private void cleanUpTestScreening() throws Exception {
        try (Connection conn = connectionProvider.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE ticket tk
                    JOIN booking bk ON bk.booking_id = tk.booking_id
                    JOIN screening sc ON sc.screening_id = bk.screening_id
                    SET tk.ticket_status = 'CANCELLED', tk.cancelled_at = COALESCE(tk.cancelled_at, CURRENT_TIMESTAMP)
                    WHERE DATE(sc.start_time) = '2026-12-25'
                      AND tk.ticket_status = 'VALID'
                    """)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE pm FROM payment pm
                    JOIN booking bk ON bk.booking_id = pm.booking_id
                    JOIN screening sc ON sc.screening_id = bk.screening_id
                    WHERE DATE(sc.start_time) = '2026-12-25'
                    """)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE tk FROM ticket tk
                    JOIN booking bk ON bk.booking_id = tk.booking_id
                    JOIN screening sc ON sc.screening_id = bk.screening_id
                    WHERE DATE(sc.start_time) = '2026-12-25'
                    """)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE bk FROM booking bk
                    JOIN screening sc ON sc.screening_id = bk.screening_id
                    WHERE DATE(sc.start_time) = '2026-12-25'
                    """)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM screening WHERE DATE(start_time) = '2026-12-25'")) {
                ps.executeUpdate();
            }
        }
    }
}
