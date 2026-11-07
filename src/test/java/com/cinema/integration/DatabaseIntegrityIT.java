package com.cinema.integration;

import com.cinema.shared.config.AppConfig;
import com.cinema.shared.db.ConnectionProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        connectionProvider = new ConnectionProvider(AppConfig.loadTest());
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

    private int countRows(Connection conn, String tableName) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
