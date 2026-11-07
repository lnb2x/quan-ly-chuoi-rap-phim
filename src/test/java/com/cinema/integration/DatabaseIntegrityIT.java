package com.cinema.integration;

import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.validation.BusinessRuleException;
import com.cinema.support.TestDatabaseGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Tích hợp Toàn vẹn Cơ sở dữ liệu (Gate C — Database Integrity Verification).
 * Thực thi trực tiếp trên CSDL kiểm thử (cinema_chain_test) thông qua tài khoản
 * phân quyền tối thiểu (cinema_app) để chứng minh:
 * - Quy mô dữ liệu khởi tạo (>= 4 chi nhánh, >= 12 phòng, >= 960 ghế, >= 20 phim, >= 80 suất, >= 150 đơn).
 * - Khóa ngoại, khóa duy nhất, ràng buộc CHECK và các Trigger ở tầng CSDL.
 * - Bảo toàn giá vé lịch sử khi giá hiện tại thay đổi.
 */
class DatabaseIntegrityIT {

    private static ConnectionProvider connectionProvider;

    @BeforeAll
    static void init() {
        connectionProvider = TestDatabaseGuard.verifyAndCreateTestProvider();
    }

    @Test
    @DisplayName("Gate C.0: Kiểm chứng Hệ quản trị CSDL thực thi bắt buộc là MySQL Server 8.x (không phải MariaDB)")
    void verifyRunningDatabaseEngineIsGenuineMySql8() throws SQLException {
        try (Connection conn = connectionProvider.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT VERSION() AS ver, @@version_comment AS ver_comment")) {
            assertTrue(rs.next());
            String version = rs.getString("ver");
            String comment = rs.getString("ver_comment");
            assertNotNull(version);
            assertFalse(version.toLowerCase().contains("mariadb"),
                    "CSDL thực thi phải là MySQL Server, không được dùng MariaDB. Thực tế: " + version);
            assertTrue(comment != null && comment.toLowerCase().contains("mysql"),
                    "@@version_comment phải xác nhận phân phối MySQL chính thức. Thực tế: " + comment);
            int majorVersion = Integer.parseInt(version.split("\\.")[0]);
            assertTrue(majorVersion >= 8, "Phiên bản MySQL Server phải từ 8.0 trở lên. Thực tế: " + version);
        }
    }

    @Test
    @DisplayName("Gate C.1: Kiểm chứng quy mô dữ liệu khởi tạo đáp ứng và vượt mức yêu cầu")
    void verifySeedDataScale() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            assertTrue(countRows(conn, "cinema_branch") >= 4, "Phải có ít nhất 4 chi nhánh rạp");
            assertTrue(countRows(conn, "screening_room") >= 12, "Phải có ít nhất 12 phòng chiếu");
            assertTrue(countRows(conn, "seat") >= 960, "Phải có ít nhất 960 ghế vật lý");
            assertTrue(countRows(conn, "movie") >= 20, "Phải có ít nhất 20 bộ phim");
            assertTrue(countRows(conn, "screening") >= 80, "Phải có ít nhất 80 suất chiếu");
            assertTrue(countRows(conn, "booking") >= 150, "Phải có ít nhất 150 đơn đặt vé");
        }
    }

    @Test
    @DisplayName("Gate C.2: Trigger CSDL phải từ chối trực tiếp câu lệnh SQL chèn suất chiếu trùng lịch trong cùng phòng [BR-06]")
    void databaseTriggerMustRejectOverlappingScreeningOnDirectSqlWrite() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Phòng 1 đã có suất chiếu #1 vào 2026-11-04 13:00:00 -> 15:31:00
                String sql = """
                        INSERT INTO screening (movie_id, room_id, start_time, end_time, base_price, status)
                        VALUES (1, 1, '2026-11-04 13:30:00', '2026-11-04 15:45:00', 95000.00, 'OPEN')
                        """;
                SQLException ex = assertThrows(SQLException.class, () -> {
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate(sql);
                    }
                });
                assertTrue(ex.getMessage().contains("BR-06"), "Thông báo lỗi Trigger phải chỉ rõ BR-06: " + ex.getMessage());
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Gate C.3: Trigger CSDL chuẩn 3NF phải từ chối vé có ghế thuộc phòng chiếu khác với phòng của suất chiếu [BR-09]")
    void databaseTriggerMustRejectSeatFromWrongRoom() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Đơn #11 thuộc suất chiếu #1 (Phòng 1: seat_id 1..80). Ghế #85 thuộc Phòng 2!
                String sql = """
                        INSERT INTO ticket (booking_id, seat_id, unit_price, ticket_status)
                        VALUES (11, 85, 95000.00, 'VALID')
                        """;
                SQLException ex = assertThrows(SQLException.class, () -> {
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate(sql);
                    }
                });
                assertTrue(ex.getMessage().contains("BR-09"), "Trigger phải chặn ghế sai phòng chiếu [BR-09]: " + ex.getMessage());
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Gate C.4: Khóa chính PK(screening_id, seat_id) trên screening_seat_lock và uq_ticket_booking_seat chặn tuyệt đối bán trùng ghế [BR-10]")
    void databaseUniqueIndexMustRejectDuplicateValidSeat() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int screeningId;
                int seatId;
                int bookingId;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("""
                             SELECT tk.booking_id, bk.screening_id, tk.seat_id
                             FROM ticket tk
                             JOIN booking bk ON bk.booking_id = tk.booking_id
                             WHERE tk.ticket_status = 'VALID'
                             LIMIT 1
                             """)) {
                    assertTrue(rs.next());
                    bookingId = rs.getInt("booking_id");
                    screeningId = rs.getInt("screening_id");
                    seatId = rs.getInt("seat_id");
                }

                // 1. Trùng ghế ngay trong cùng 1 booking -> bị chặn bởi uq_ticket_booking_seat (booking_id, seat_id)
                String sameBookingDupSql = "INSERT INTO ticket (booking_id, seat_id, unit_price, ticket_status) VALUES (?, ?, 95000.00, 'VALID')";
                assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(sameBookingDupSql)) {
                        ps.setInt(1, bookingId);
                        ps.setInt(2, seatId);
                        ps.executeUpdate();
                    }
                });

                // 2. Tạo một booking KHÁC nhưng thuộc CÙNG suất chiếu, đặt CÙNG ghế VALID -> bị chặn bởi PK(screening_id, seat_id) trên screening_seat_lock
                int secondBookingId;
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO booking (booking_code, screening_id, customer_id, employee_id, booked_at, booking_status)
                        VALUES ('BK-3NF-DUP-SEAT-TEST', ?, 1, 3, CURRENT_TIMESTAMP, 'CONFIRMED')
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, screeningId);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        assertTrue(keys.next());
                        secondBookingId = keys.getInt(1);
                    }
                }
                assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(sameBookingDupSql)) {
                        ps.setInt(1, secondBookingId);
                        ps.setInt(2, seatId);
                        ps.executeUpdate();
                    }
                });
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Gate C.5: Chỉ mục uq_payment_one_success phải chặn giao dịch thanh toán SUCCESS thứ hai cho cùng một đơn [BR-13]")
    void databaseMustRejectSecondSuccessfulPaymentForSameBooking() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int paidBookingId;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT booking_id FROM payment WHERE payment_status = 'SUCCESS' LIMIT 1")) {
                    assertTrue(rs.next());
                    paidBookingId = rs.getInt(1);
                }

                String sql = """
                        INSERT INTO payment (booking_id, amount, payment_method, payment_status, paid_at, transaction_ref)
                        VALUES (?, 190000.00, 'CASH', 'SUCCESS', CURRENT_TIMESTAMP, 'TXN-DUP-TEST')
                        """;
                assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, paidBookingId);
                        ps.executeUpdate();
                    }
                });
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Gate C.6: Bảo toàn giá vé lịch sử ngay cả khi thay đổi giá cơ sở của suất chiếu hoặc phụ thu hạng ghế [BR-12]")
    void historicalTicketPriceMustRemainUnchangedWhenCurrentPricingChanges() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal beforeRevenue;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT SUM(total_paid_revenue) FROM vw_branch_daily_revenue")) {
                    assertTrue(rs.next());
                    beforeRevenue = rs.getBigDecimal(1);
                }

                // Thay đổi giá cơ sở của tất cả suất chiếu và phụ thu hạng ghế VIP
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("UPDATE screening SET base_price = base_price + 50000.00");
                    st.executeUpdate("UPDATE seat_type SET surcharge = surcharge + 15000.00 WHERE seat_type_code = 'VIP'");
                }

                BigDecimal afterRevenue;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT SUM(total_paid_revenue) FROM vw_branch_daily_revenue")) {
                    assertTrue(rs.next());
                    afterRevenue = rs.getBigDecimal(1);
                }

                assertEquals(0, beforeRevenue.compareTo(afterRevenue),
                        "Doanh thu lịch sử tuyệt đối không được thay đổi khi bảng giá hiện hành thay đổi [BR-12]");
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Gate C.7: Kiểm chứng mã hóa tiếng Việt UTF-8 (utf8mb4) chính xác 100% trong CSDL")
    void databaseVietnameseUtf8DiacriticsMustBeIntact() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT branch_name, city FROM cinema_branch WHERE branch_id = 1")) {
                assertTrue(rs.next());
                assertEquals("CineViet Vincom Bà Triệu", rs.getString("branch_name"));
                assertEquals("Hà Nội", rs.getString("city"));
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT city FROM cinema_branch WHERE branch_id = 3")) {
                assertTrue(rs.next());
                assertEquals("Đà Nẵng", rs.getString("city"));
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT city FROM cinema_branch WHERE branch_id = 4")) {
                assertTrue(rs.next());
                assertEquals("Hải Phòng", rs.getString("city"));
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT title FROM movie WHERE movie_id = 1")) {
                assertTrue(rs.next());
                assertEquals("Đào, Phở và Piano", rs.getString("title"));
            }
        }
    }

    @Test
    @DisplayName("Gate C.8: Trigger CSDL phải từ chối lệnh UPDATE suất chiếu gây trùng lịch với suất khác trong cùng phòng [BR-06]")
    void databaseTriggerMustRejectOverlappingScreeningOnUpdate() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int targetScreeningId;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT screening_id FROM screening WHERE room_id = 1 AND screening_id <> 1 AND status = 'OPEN' LIMIT 1")) {
                    assertTrue(rs.next());
                    targetScreeningId = rs.getInt(1);
                }
                // Dời suất chiếu này đè lên khung giờ của suất #1 (2026-11-04 13:00:00 -> 15:31:00) trong Phòng 1
                String sql = "UPDATE screening SET start_time = '2026-11-04 13:15:00', end_time = '2026-11-04 16:00:00' WHERE screening_id = ?";
                SQLException ex = assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, targetScreeningId);
                        ps.executeUpdate();
                    }
                });
                assertTrue(ex.getMessage().contains("BR-06"), "Trigger UPDATE phải chặn trùng lịch [BR-06]: " + ex.getMessage());
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Gate C.9: Cấm thay đổi phòng chiếu (room_id) của suất chiếu đã có vé phát hành để bảo toàn ghế thuộc đúng phòng [BR-09]")
    void databaseTriggerMustRejectChangingRoomOfScreeningWithExistingTickets() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int screeningWithTickets;
                int originalRoomId;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("""
                             SELECT s.screening_id, s.room_id
                             FROM screening s
                             WHERE EXISTS (
                                 SELECT 1
                                 FROM ticket tk
                                 JOIN booking bk ON bk.booking_id = tk.booking_id
                                 WHERE bk.screening_id = s.screening_id
                             )
                             ORDER BY s.screening_id ASC
                             LIMIT 1
                             """)) {
                    assertTrue(rs.next());
                    screeningWithTickets = rs.getInt("screening_id");
                    originalRoomId = rs.getInt("room_id");
                }
                int differentRoomId = (originalRoomId == 1) ? 2 : 1;
                // Dời sang ngày tương lai trống lịch ở phòng khác để chứng minh bị chặn đúng vì lý do đổi phòng khi đã có vé [BR-09]
                String sql = """
                        UPDATE screening
                        SET room_id = ?, start_time = '2026-12-28 09:00:00', end_time = '2026-12-28 12:00:00'
                        WHERE screening_id = ?
                        """;
                SQLException ex = assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, differentRoomId);
                        ps.setInt(2, screeningWithTickets);
                        ps.executeUpdate();
                    }
                });
                assertTrue(ex.getMessage().contains("BR-09"),
                        "Trigger CSDL phải từ chối đổi phòng của suất chiếu đã có vé [BR-09]: " + ex.getMessage());
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Gate C.10: Trigger CSDL phải từ chối UPDATE chuyển suất chiếu vào phòng đang tạm ngưng hoạt động [BR-01]")
    void databaseTriggerMustRejectUpdatingScreeningToInactiveRoom() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int emptyScreeningId;
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("""
                            INSERT INTO screening (movie_id, room_id, start_time, end_time, base_price, status)
                            VALUES (1, 1, '2026-12-29 09:00:00', '2026-12-29 11:30:00', 95000.00, 'OPEN')
                            """);
                    try (ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
                        assertTrue(rs.next());
                        emptyScreeningId = rs.getInt(1);
                    }
                    st.executeUpdate("UPDATE screening_room SET active = 0 WHERE room_id = 2");
                }

                SQLException ex = assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE screening SET room_id = 2 WHERE screening_id = ?")) {
                        ps.setInt(1, emptyScreeningId);
                        ps.executeUpdate();
                    }
                });
                assertTrue(ex.getMessage().contains("BR-01"),
                        "Trigger UPDATE phải từ chối phòng chiếu tạm ngưng hoạt động [BR-01]: " + ex.getMessage());
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Gate C.11: Chuyển trạng thái thanh toán không được phép kích hoạt lại đơn đã CANCELLED và tổng tiền phải nhất quán [BR-13, BR-14]")
    void paymentTransitionMustRejectCancelledBookingAndMaintainConsistentTotals() throws Exception {
        BookingService bookingService = new BookingService(
                new BookingRepository(connectionProvider),
                new TransactionTemplate(connectionProvider)
        );
        int cancelledBookingId;
        try (Connection conn = connectionProvider.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT booking_id FROM booking WHERE booking_status = 'CANCELLED' LIMIT 1")) {
            assertTrue(rs.next());
            cancelledBookingId = rs.getInt(1);
        }

        try {
            assertThrows(BusinessRuleException.class,
                    () -> bookingService.transitionPendingPayment(cancelledBookingId, "SUCCESS"),
                    "Không được phép chuyển đơn đã CANCELLED sang CONFIRMED khi không còn thanh toán PENDING hợp lệ");
        } finally {
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE booking SET booking_status = 'CANCELLED' WHERE booking_id = ? AND NOT EXISTS (SELECT 1 FROM payment pm WHERE pm.booking_id = booking.booking_id AND pm.payment_status = 'SUCCESS')")) {
                ps.setInt(1, cancelledBookingId);
                ps.executeUpdate();
            }
        }

        // Kiểm chứng toàn bộ đơn CONFIRMED có đúng 1 thanh toán SUCCESS và số tiền khớp 100% tổng vé VALID
        try (Connection conn = connectionProvider.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT
                         (SELECT COUNT(*) FROM booking bk
                          WHERE bk.booking_status = 'CONFIRMED'
                            AND NOT EXISTS (SELECT 1 FROM payment pm WHERE pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS')) AS confirmed_missing_pay,
                         (SELECT COUNT(*) FROM payment pm
                          JOIN booking bk ON bk.booking_id = pm.booking_id
                          WHERE pm.payment_status = 'SUCCESS' AND bk.booking_status <> 'CONFIRMED') AS success_on_non_confirmed,
                         (SELECT COUNT(*) FROM payment pm
                          WHERE pm.payment_status = 'SUCCESS'
                            AND pm.amount <> (SELECT COALESCE(SUM(tk.unit_price), 0)
                                              FROM ticket tk
                                              WHERE tk.booking_id = pm.booking_id AND tk.ticket_status = 'VALID')) AS amount_mismatch,
                         (SELECT COUNT(*) FROM ticket tk
                          JOIN booking bk ON bk.booking_id = tk.booking_id
                          WHERE bk.booking_status = 'CANCELLED' AND tk.ticket_status = 'VALID') AS valid_on_cancelled
                     """)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("confirmed_missing_pay"), "Mọi đơn CONFIRMED phải có thanh toán SUCCESS");
            assertEquals(0, rs.getInt("success_on_non_confirmed"), "Thanh toán SUCCESS chỉ được gắn với đơn CONFIRMED");
            assertEquals(0, rs.getInt("amount_mismatch"), "Tổng thanh toán SUCCESS phải khớp 100% tổng unit_price vé VALID");
            assertEquals(0, rs.getInt("valid_on_cancelled"), "Đơn CANCELLED không được còn vé VALID");
        }
    }

    @Test
    @DisplayName("Gate C.12: Kiểm chứng FK, CHECK constraint và cơ chế ROLLBACK nguyên tử khi vi phạm ràng buộc")
    void foreignKeyCheckConstraintAndTransactionRollbackMustEnforceIntegrity() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Vi phạm FK: movie_id không tồn tại
                assertThrows(SQLException.class, () -> {
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate("""
                                INSERT INTO screening (movie_id, room_id, start_time, end_time, base_price, status)
                                VALUES (999999, 1, '2026-12-30 09:00:00', '2026-12-30 11:30:00', 95000.00, 'OPEN')
                                """);
                    }
                });

                // 2. Vi phạm CHECK constraint: payment_method ngoài tập cho phép
                assertThrows(SQLException.class, () -> {
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate("""
                                INSERT INTO payment (booking_id, amount, payment_method, payment_status)
                                VALUES (1, 95000.00, 'CRYPTO_INVALID', 'PENDING')
                                """);
                    }
                });

                // 3. Kiểm chứng ROLLBACK: chèn booking hợp lệ nhưng vé thứ 2 sai phòng -> rollback phải xóa sạch booking tạm
                int bookingsBefore = countRows(conn, "booking");
                Savepoint sp = conn.setSavepoint("before_atomic_test");
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("""
                            INSERT INTO booking (booking_code, screening_id, customer_id, employee_id, booked_at, booking_status)
                            VALUES ('BK-ROLLBACK-PROOF', 1, 1, 3, CURRENT_TIMESTAMP, 'CONFIRMED')
                            """);
                    int tempBookingId;
                    try (ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
                        assertTrue(rs.next());
                        tempBookingId = rs.getInt(1);
                    }
                    assertThrows(SQLException.class, () -> st.executeUpdate(
                            "INSERT INTO ticket (booking_id, seat_id, unit_price, ticket_status) VALUES ("
                                    + tempBookingId + ", 85, 95000.00, 'VALID')"));
                    conn.rollback(sp);
                }
                assertEquals(bookingsBefore, countRows(conn, "booking"),
                        "Sau khi rollback giao dịch lỗi, không được tồn tại booking dở dang");
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Gate C.13: Kiểm chứng lược đồ 14 bảng đạt chuẩn 3NF (ticket không chứa screening_id/active_seat_key) và chống sửa trái phép khóa ghế")
    void verifyTrue3nfTicketSchemaAndSeatLockTamperProtection() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            // 1. Bảng ticket tuyệt đối không còn cột bắc cầu screening_id hay cột sinh active_seat_key
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                         SELECT COUNT(*)
                         FROM information_schema.COLUMNS
                         WHERE TABLE_SCHEMA = DATABASE()
                           AND TABLE_NAME = 'ticket'
                           AND COLUMN_NAME IN ('screening_id', 'active_seat_key')
                         """)) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1),
                        "Bảng ticket chuẩn 3NF tuyệt đối không được chứa cột phụ thuộc bắc cầu screening_id hoặc active_seat_key");
            }

            // 2. Đủ 14 bảng cơ sở và nối không mất mát (lossless join) ticket ⋈ booking + đồng bộ 1-1 screening_seat_lock
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                         SELECT
                             (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE') AS base_table_count,
                             (SELECT COUNT(*) FROM ticket) AS total_tickets,
                             (SELECT COUNT(*) FROM ticket tk INNER JOIN booking bk ON bk.booking_id = tk.booking_id) AS joined_tickets,
                             (SELECT COUNT(*) FROM ticket WHERE ticket_status = 'VALID') AS valid_tickets,
                             (SELECT COUNT(*) FROM screening_seat_lock) AS active_locks
                         """)) {
                assertTrue(rs.next());
                assertEquals(14, rs.getInt("base_table_count"), "Lược đồ chuẩn 3NF phải có đúng 14 bảng cơ sở");
                assertEquals(rs.getInt("total_tickets"), rs.getInt("joined_tickets"),
                        "Phép nối tự nhiên ticket ⋈ booking phải bảo toàn không mất mát 100% số dòng vé");
                assertEquals(rs.getInt("valid_tickets"), rs.getInt("active_locks"),
                        "Số dòng khóa ghế trong screening_seat_lock phải bằng đúng số vé VALID");
            }

            // 3. Kiểm chứng các Trigger bảo vệ bất biến trên booking và screening_seat_lock
            conn.setAutoCommit(false);
            try {
                int bookingWithTickets;
                int currentScrId;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("""
                             SELECT bk.booking_id, bk.screening_id
                             FROM booking bk
                             WHERE EXISTS (SELECT 1 FROM ticket tk WHERE tk.booking_id = bk.booking_id)
                             LIMIT 1
                             """)) {
                    assertTrue(rs.next());
                    bookingWithTickets = rs.getInt("booking_id");
                    currentScrId = rs.getInt("screening_id");
                }
                int otherScrId = (currentScrId == 1) ? 2 : 1;
                SQLException updBookingEx = assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE booking SET screening_id = ? WHERE booking_id = ?")) {
                        ps.setInt(1, otherScrId);
                        ps.setInt(2, bookingWithTickets);
                        ps.executeUpdate();
                    }
                });
                assertTrue(updBookingEx.getMessage().contains("BR-08"));

                // Cấm xóa trực tiếp khóa ghế trong screening_seat_lock khi vé vẫn đang VALID
                int activeLockedTicketId;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT ticket_id FROM screening_seat_lock LIMIT 1")) {
                    assertTrue(rs.next());
                    activeLockedTicketId = rs.getInt(1);
                }
                SQLException delLockEx = assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM screening_seat_lock WHERE ticket_id = ?")) {
                        ps.setInt(1, activeLockedTicketId);
                        ps.executeUpdate();
                    }
                });
                assertTrue(delLockEx.getMessage().contains("BR-10"));

                // Cấm chèn khóa ghế giả cho vé đã CANCELLED
                int cancelledTicketId;
                int cancelledSeatId;
                int cancelledScrId;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("""
                             SELECT tk.ticket_id, tk.seat_id, bk.screening_id
                             FROM ticket tk
                             JOIN booking bk ON bk.booking_id = tk.booking_id
                             WHERE tk.ticket_status = 'CANCELLED'
                             LIMIT 1
                             """)) {
                    assertTrue(rs.next());
                    cancelledTicketId = rs.getInt("ticket_id");
                    cancelledSeatId = rs.getInt("seat_id");
                    cancelledScrId = rs.getInt("screening_id");
                }
                SQLException insLockEx = assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO screening_seat_lock (screening_id, seat_id, ticket_id) VALUES (?, ?, ?)")) {
                        ps.setInt(1, cancelledScrId);
                        ps.setInt(2, cancelledSeatId);
                        ps.setInt(3, cancelledTicketId);
                        ps.executeUpdate();
                    }
                });
                assertTrue(insLockEx.getMessage().contains("BR-10"));
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Gate C.14: Công cụ dấu vân tay SHA-256 phát hiện ngay sửa đổi nội dung 1 dòng dù số dòng và tổng doanh thu không đổi")
    void pkOrderedFingerprintToolMustDetectSingleRowContentMutation() throws Exception {
        Path tempFp = Files.createTempFile("test_db_fp_", ".json");
        String originalName;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT full_name FROM customer WHERE customer_id = 1");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            originalName = rs.getString(1);
        }

        try {
            Process capProc = new ProcessBuilder(
                    "python", "tools/db_fingerprint.py",
                    "--db", "cinema_chain_test",
                    "--file", tempFp.toAbsolutePath().toString(),
                    "--mode", "capture"
            ).redirectErrorStream(true).start();
            String capOut = new String(capProc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, capProc.waitFor(), "Chụp dấu vân tay phải thành công: " + capOut);

            // Sửa tên của 1 khách hàng (số dòng của mọi bảng và tổng doanh thu 35.165.000 VNĐ giữ nguyên không đổi!)
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE customer SET full_name = ? WHERE customer_id = 1")) {
                ps.setString(1, originalName + " (Đã sửa thử nghiệm)");
                ps.executeUpdate();
            }

            Process verifyMutated = new ProcessBuilder(
                    "python", "tools/db_fingerprint.py",
                    "--db", "cinema_chain_test",
                    "--file", tempFp.toAbsolutePath().toString(),
                    "--mode", "verify"
            ).redirectErrorStream(true).start();
            String mutatedOut = new String(verifyMutated.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertNotEquals(0, verifyMutated.waitFor(),
                    "Dấu vân tay SHA-256 bắt buộc phải báo lỗi khi nội dung 1 dòng thay đổi dù số dòng và doanh thu không đổi!");
            assertTrue(mutatedOut.contains("customer"), "Thông báo sai lệch phải chỉ đích danh bảng customer: " + mutatedOut);
        } finally {
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE customer SET full_name = ? WHERE customer_id = 1")) {
                ps.setString(1, originalName);
                ps.executeUpdate();
            }
            Process verifyRestored = new ProcessBuilder(
                    "python", "tools/db_fingerprint.py",
                    "--db", "cinema_chain_test",
                    "--file", tempFp.toAbsolutePath().toString(),
                    "--mode", "verify"
            ).redirectErrorStream(true).start();
            String restoredOut = new String(verifyRestored.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int restoredExit = verifyRestored.waitFor();
            Files.deleteIfExists(tempFp);
            assertEquals(0, restoredExit, "Sau khi khôi phục giá trị gốc, dấu vân tay phải khớp lại 100%: " + restoredOut);
        }
    }

    private int countRows(Connection conn, String tableName) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
