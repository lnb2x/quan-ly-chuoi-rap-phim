package com.cinema.integration;

import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.screening.ScreeningService;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.DataAccessException;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.validation.BusinessRuleException;
import com.cinema.support.TestDatabaseGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Kiểm thử mức 2 (trạng thái) và mức 4 (rollback/phục hồi) trên MySQL thật. */
class PaymentRecoveryIT {
    private static ConnectionProvider cp;
    private static BookingRepository repository;
    private static BookingService service;
    private static TransactionTemplate tx;
    private int screeningId;

    @BeforeAll
    static void init() {
        cp = TestDatabaseGuard.verifyAndCreateTestProvider();
        repository = new BookingRepository(cp);
        tx = new TransactionTemplate(cp);
        service = new BookingService(repository, tx);
    }

    @BeforeEach
    void fixture() {
        var start = LocalDateTime.of(2027, 2, 1, 9, 0);
        screeningId = new ScreeningService(new ScreeningRepository(cp), tx)
                .createScreening(1, 1, start, start.plusHours(4), new BigDecimal("100000"));
    }

    @AfterEach
    void clean() throws Exception {
        if (screeningId == 0) return;
        tx.execute(c -> {
            execute(c, "UPDATE ticket t JOIN booking b ON b.booking_id=t.booking_id SET t.ticket_status='CANCELLED' WHERE b.screening_id=?", screeningId);
            execute(c, "DELETE p FROM payment p JOIN booking b ON b.booking_id=p.booking_id WHERE b.screening_id=?", screeningId);
            execute(c, "DELETE t FROM ticket t JOIN booking b ON b.booking_id=t.booking_id WHERE b.screening_id=?", screeningId);
            execute(c, "DELETE FROM booking WHERE screening_id=?", screeningId);
            execute(c, "DELETE FROM screening WHERE screening_id=?", screeningId);
            return null;
        });
    }

    @Test
    @DisplayName("Mức 2: từ chối INSERT SUCCESS sai tổng tiền vé")
    void rejectWrongSuccessfulAmountOnInsert() throws Exception {
        int booking = pending();
        try (var c = cp.getConnection()) {
            SQLException ex = assertThrows(SQLException.class, () -> repository.insertPayment(c, booking,
                    BigDecimal.ONE, "CASH", "SUCCESS", LocalDateTime.now(), "BAD-AMOUNT"));
            assertEquals("45000", ex.getSQLState());
        }
        assertPending(booking);
    }

    @Test
    @DisplayName("Mức 2: từ chối UPDATE SUCCESS sai tổng tiền vé")
    void rejectWrongSuccessfulAmountOnUpdate() throws Exception {
        int booking = pending();
        try (var c = cp.getConnection()) {
            SQLException ex = assertThrows(SQLException.class, () -> execute(c,
                    "UPDATE payment SET amount=1,payment_status='SUCCESS' WHERE booking_id=?", booking));
            assertEquals("45000", ex.getSQLState());
        }
        assertPending(booking);
    }

    @Test
    @DisplayName("Mức 2: đơn PENDING có số tiền sai không được ứng dụng xác nhận SUCCESS")
    void rejectConfirmationOfMismatchedPendingPayment() throws Exception {
        int booking = pending();
        try (var c = cp.getConnection()) {
            execute(c, "UPDATE payment SET amount=99999 WHERE booking_id=?", booking);
        }
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.transitionPendingPayment(booking, "SUCCESS"));
        assertTrue(ex.getMessage().contains("BR-13"));
        assertEquals(1, count("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'", booking));
        assertEquals(1, count("SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='PENDING' AND amount=99999 AND paid_at IS NULL", booking));
        assertEquals(1, count("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?", screeningId));
    }

    @Test
    @DisplayName("Mức 2: chặn SQL INSERT/UPDATE thanh toán thành công cho đơn đã hủy")
    void rejectSuccessOnCancelledBookingThroughSql() throws Exception {
        int booking = pending();
        service.cancelBooking(booking);
        try (var c = cp.getConnection()) {
            SQLException insert = assertThrows(SQLException.class, () -> repository.insertPayment(c, booking,
                    new BigDecimal("100000"), "CASH", "SUCCESS", LocalDateTime.now(), "CANCELLED"));
            assertEquals("45000", insert.getSQLState());
            SQLException update = assertThrows(SQLException.class, () -> execute(c,
                    "UPDATE payment SET payment_status='SUCCESS' WHERE booking_id=?", booking));
            assertEquals("45000", update.getSQLState());
        }
        assertEquals(0, count("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?", screeningId));
    }

    @Test
    @DisplayName("Mức 2: xác nhận thanh toán hai lần không nhân đôi tiền/vé")
    void rejectRepeatedSuccess() throws Exception {
        int booking = pending();
        service.transitionPendingPayment(booking, "SUCCESS");
        assertThrows(BusinessRuleException.class, () -> service.transitionPendingPayment(booking, "SUCCESS"));
        assertEquals(1, count("SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='SUCCESS'", booking));
        assertEquals(1, count("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='CONFIRMED'", booking));
        assertEquals(1, count("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?", screeningId));
    }

    @Test
    @DisplayName("Mức 2: hủy hai lần và xác nhận đơn đã hủy đều bị chặn, ghế bán lại được")
    void rejectRepeatedCancellationAndResurrection() throws Exception {
        int booking = pending();
        service.cancelBooking(booking);
        assertThrows(BusinessRuleException.class, () -> service.cancelBooking(booking));
        assertThrows(BusinessRuleException.class, () -> service.transitionPendingPayment(booking, "SUCCESS"));
        assertEquals(1, count("SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='FAILED'", booking));
        int resold = service.createBooking(screeningId, null, 1, List.of(1), "CASH", "SUCCESS");
        assertNotEquals(booking, resold);
        assertEquals(1, count("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?", screeningId));
    }

    @Test
    @DisplayName("Mức 2: hủy đơn đã thu tiền hoàn đúng một lần và không cho xác nhận lại")
    void successfulPaymentIsRefundedOnce() throws Exception {
        int booking = service.createBooking(screeningId, null, 1, List.of(1), "CASH", "SUCCESS");
        service.cancelBooking(booking);
        assertThrows(BusinessRuleException.class, () -> service.cancelBooking(booking));
        assertThrows(BusinessRuleException.class, () -> service.transitionPendingPayment(booking, "SUCCESS"));
        assertEquals(1, count("SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='REFUNDED' AND amount=100000", booking));
        assertEquals(0, count("SELECT COUNT(*) FROM ticket WHERE booking_id=? AND ticket_status='VALID'", booking));
        assertEquals(0, count("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?", screeningId));
    }

    @Test
    @DisplayName("Mức 2: trạng thái đích sai giữ nguyên đơn, vé, tiền và khóa ghế")
    void invalidTargetLeavesPendingUnchanged() throws Exception {
        int booking = pending();
        for (String target : new String[] {null, "PENDING", "REFUNDED", "UNKNOWN"}) {
            assertThrows(BusinessRuleException.class, () -> service.transitionPendingPayment(booking, target));
            assertPending(booking);
        }
    }

    @Test
    @DisplayName("Mức 4: lỗi SQL khi ghi payment rollback cả đơn/vé/khóa ghế")
    void paymentSqlFailureRollsBackWholeTransaction() throws Exception {
        DataAccessException error = assertThrows(DataAccessException.class, () -> tx.execute(c -> {
            int booking = writeBookingAndTicket(c, screeningId, "SQL-FAIL");
            repository.insertPayment(c, booking, new BigDecimal("100000"), "INVALID", "SUCCESS", LocalDateTime.now(), "SQL-FAIL");
            return null;
        }));
        assertEquals(3819, assertInstanceOf(SQLException.class, error.getCause()).getErrorCode());
        assertEmpty();
    }

    @Test
    @DisplayName("Mức 4: lỗi Java sau ghi payment rollback toàn bộ giao dịch")
    void runtimeFailureAfterPaymentRollsBackWholeTransaction() throws Exception {
        assertThrows(IllegalStateException.class, () -> tx.execute(c -> {
            writeCompleteBooking(c, screeningId, "RUNTIME-FAIL");
            throw new IllegalStateException("Lỗi giả lập sau khi ghi payment");
        }));
        assertEmpty();
    }

    @Test
    @DisplayName("Mức 4: Error không được khiến finally tự commit dữ liệu dở dang")
    void errorBeforeCommitRollsBackWholeTransaction() throws Exception {
        assertThrows(AssertionError.class, () -> tx.execute(c -> {
            writeCompleteBooking(c, screeningId, "ERROR-FAIL");
            throw new AssertionError("Lỗi giả lập trước commit");
        }));
        assertEmpty();
    }

    @Test
    @DisplayName("Mức 4: cắt đúng kết nối test trước commit, InnoDB rollback và kết nối mới hoạt động")
    void killedConnectionRollsBackWholeTransaction() throws Exception {
        assertThrows(DataAccessException.class, () -> tx.execute(c -> {
            writeCompleteBooking(c, screeningId, "CONNECTION-FAIL");
            long connectionId;
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT CONNECTION_ID()")) { r.next(); connectionId=r.getLong(1); }
            try (var killer = cp.getConnection(); var p = killer.prepareStatement("KILL CONNECTION ?")) {
                p.setLong(1, connectionId); p.execute();
            }
            try (var s = c.createStatement()) { s.executeQuery("SELECT 1"); }
            return null;
        }));
        awaitEmpty();
        assertTrue(service.createBooking(screeningId, null, 1, List.of(1), "CASH", "SUCCESS") > 0);
    }

    @Test
    @DisplayName("Mức 4: dừng JVM thật trước commit không để lại đơn/vé/tiền/khóa ghế")
    void killedJvmBeforeCommitRollsBackWholeTransaction() throws Exception {
        Path ready = Files.createTempFile("cinema-recovery-", ".ready");
        Files.delete(ready);
        String javaExe = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        Process child = new ProcessBuilder(javaExe, "-cp", System.getProperty("java.class.path"),
                PaymentRecoveryIT.class.getName(), Integer.toString(screeningId), ready.toString())
                .redirectErrorStream(true).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!Files.exists(ready) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
            if (!Files.exists(ready)) {
                child.destroyForcibly();
                assertTrue(child.waitFor(10, TimeUnit.SECONDS));
                fail("JVM con chưa ghi xong fixture: " + new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            // The child must have written every row but still be holding its transaction open.
            assertEquals(0, count("SELECT COUNT(*) FROM booking WHERE screening_id=?", screeningId));
            child.destroyForcibly();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
            awaitEmpty();
            assertTrue(service.createBooking(screeningId, null, 1, List.of(1), "CASH", "SUCCESS") > 0);
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); }
            Files.deleteIfExists(ready);
        }
    }

    /** Child process deliberately waits without commit; parent terminates this JVM. */
    public static void main(String[] args) throws Exception {
        init();
        try (var c = cp.getConnection()) {
            c.setAutoCommit(false);
            writeCompleteBooking(c, Integer.parseInt(args[0]), "JVM-FAIL");
            Files.writeString(Path.of(args[1]), "READY");
            System.in.read();
            c.rollback();
        }
    }

    private int pending() { return service.createBooking(screeningId, null, 1, List.of(1), "CARD", "PENDING"); }

    private static int writeBookingAndTicket(Connection c, int screening, String prefix) throws SQLException {
        int booking = repository.insertBooking(c, prefix + "-" + UUID.randomUUID().toString().substring(0, 8), screening,
                null, 1, LocalDateTime.now(), "CONFIRMED");
        repository.insertTicket(c, booking, 1, new BigDecimal("100000"), "VALID");
        return booking;
    }

    private static void writeCompleteBooking(Connection c, int screening, String prefix) throws SQLException {
        int booking = writeBookingAndTicket(c, screening, prefix);
        repository.insertPayment(c, booking, new BigDecimal("100000"), "CASH", "SUCCESS", LocalDateTime.now(), prefix);
    }

    private void assertPending(int booking) throws Exception {
        assertEquals(1, count("SELECT COUNT(*) FROM booking WHERE booking_id=? AND booking_status='PENDING'", booking));
        assertEquals(1, count("SELECT COUNT(*) FROM payment WHERE booking_id=? AND payment_status='PENDING' AND amount=100000 AND paid_at IS NULL", booking));
        assertEquals(1, count("SELECT COUNT(*) FROM ticket WHERE booking_id=? AND ticket_status='VALID'", booking));
        assertEquals(1, count("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?", screeningId));
    }

    private void assertEmpty() throws Exception {
        assertEquals(0, count("SELECT COUNT(*) FROM booking WHERE screening_id=?", screeningId), "Không được còn đơn dở dang");
        assertEquals(0, count("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?", screeningId), "Không được còn khóa ghế");
        // FKs keep tickets and payments attached to bookings; this also checks unexpected orphan rows.
        try (var c = cp.getConnection(); var s = c.createStatement(); var r = s.executeQuery("SELECT (SELECT COUNT(*) FROM ticket t LEFT JOIN booking b ON b.booking_id=t.booking_id WHERE b.booking_id IS NULL) + (SELECT COUNT(*) FROM payment p LEFT JOIN booking b ON b.booking_id=p.booking_id WHERE b.booking_id IS NULL)")) {
            assertTrue(r.next()); assertEquals(0, r.getInt(1));
        }
    }

    private void awaitEmpty() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (count("SELECT COUNT(*) FROM screening_seat_lock WHERE screening_id=?", screeningId) != 0 && System.nanoTime() < deadline) Thread.sleep(50);
        assertEmpty();
    }

    private static int count(String sql, int id) throws Exception {
        try (var c = cp.getConnection(); var p = c.prepareStatement(sql)) {
            p.setInt(1, id);
            try (var r = p.executeQuery()) { assertTrue(r.next()); return r.getInt(1); }
        }
    }

    private static void execute(Connection c, String sql, int id) throws SQLException {
        try (var p = c.prepareStatement(sql)) { p.setInt(1, id); p.executeUpdate(); }
    }
}
