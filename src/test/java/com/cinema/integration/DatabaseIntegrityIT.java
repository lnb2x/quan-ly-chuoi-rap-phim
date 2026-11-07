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
    @DisplayName("Gate C.3: Trigger CSDL phải từ chối vé có ghế thuộc phòng chiếu khác với phòng của suất chiếu [BR-09]")
    void databaseTriggerMustRejectSeatFromWrongRoom() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Đơn #11 thuộc suất chiếu #1 (Phòng 1: seat_id 1..80). Ghế #85 thuộc Phòng 2!
                String sql = """
                        INSERT INTO ticket (booking_id, screening_id, seat_id, unit_price, ticket_status)
                        VALUES (11, 1, 85, 95000.00, 'VALID')
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
    @DisplayName("Gate C.4: Chỉ mục duy nhất uq_ticket_active_seat phải chặn bán trùng ghế VALID trong cùng suất chiếu [BR-10]")
    void databaseUniqueIndexMustRejectDuplicateValidSeat() throws SQLException {
        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Tìm 1 vé VALID đang có sẵn
                int screeningId;
                int seatId;
                int bookingId;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT booking_id, screening_id, seat_id FROM ticket WHERE ticket_status = 'VALID' LIMIT 1")) {
                    assertTrue(rs.next());
                    bookingId = rs.getInt("booking_id");
                    screeningId = rs.getInt("screening_id");
                    seatId = rs.getInt("seat_id");
                }

                String dupSql = "INSERT INTO ticket (booking_id, screening_id, seat_id, unit_price, ticket_status) VALUES (?, ?, ?, 95000.00, 'VALID')";
                assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(dupSql)) {
                        ps.setInt(1, bookingId);
                        ps.setInt(2, screeningId);
                        ps.setInt(3, seatId);
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
                             WHERE EXISTS (SELECT 1 FROM ticket tk WHERE tk.screening_id = s.screening_id)
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
                            "INSERT INTO ticket (booking_id, screening_id, seat_id, unit_price, ticket_status) VALUES ("
                                    + tempBookingId + ", 1, 85, 95000.00, 'VALID')"));
                    conn.rollback(sp);
                }
                assertEquals(bookingsBefore, countRows(conn, "booking"),
                        "Sau khi rollback giao dịch lỗi, không được tồn tại booking dở dang");
            } finally {
                conn.rollback();
            }
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
