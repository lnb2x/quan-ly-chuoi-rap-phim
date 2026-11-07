package com.cinema.feature.booking;

import com.cinema.domain.BookingSummaryView;
import com.cinema.domain.Customer;
import com.cinema.domain.Employee;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.SeatAvailabilityView;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.validation.BusinessRuleException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tầng truy cập dữ liệu cho phân hệ Đặt vé & Quản lý Đơn vé (Screen 2 - R5).
 * Tất cả các câu lệnh SQL đều dùng PreparedStatement và hỗ trợ khóa dòng suất chiếu
 * (SELECT ... FOR UPDATE) để chống bán trùng ghế dưới tải đồng thời cao.
 */
public final class BookingRepository {

    public record LockedScreeningInfo(int screeningId, int roomId, BigDecimal basePrice, String status) {
    }

    public record SeatPriceInfo(int seatId, int roomId, String seatLabel, BigDecimal surcharge, boolean alreadyOccupied) {
    }

    private final ConnectionProvider connectionProvider;

    public BookingRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<ScreeningDetailView> findOpenScreenings(Integer branchId, Integer movieId) {
        StringBuilder sql = new StringBuilder("""
                SELECT screening_id, movie_id, movie_code, movie_title, duration_minutes, age_rating,
                       room_id, room_name, room_type, branch_id, branch_code, branch_name,
                       start_time, end_time, base_price, screening_status, total_seats, sold_seats, available_seats
                FROM vw_screening_search
                WHERE screening_status = 'OPEN'
                """);
        List<Object> params = new ArrayList<>();
        if (branchId != null) {
            sql.append(" AND branch_id = ?");
            params.add(branchId);
        }
        if (movieId != null) {
            sql.append(" AND movie_id = ?");
            params.add(movieId);
        }
        sql.append(" ORDER BY start_time ASC, screening_id ASC LIMIT 1000");

        List<ScreeningDetailView> list = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(ScreeningRepository.mapScreeningDetail(rs));
                }
            }
            return list;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public List<Customer> findAllCustomers() {
        String sql = """
                SELECT customer_id, full_name, phone, email, birth_date, created_at
                FROM customer
                ORDER BY customer_id ASC
                """;
        List<Customer> list = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date bd = rs.getDate("birth_date");
                Timestamp ca = rs.getTimestamp("created_at");
                list.add(new Customer(
                        rs.getInt("customer_id"),
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        bd != null ? bd.toLocalDate() : null,
                        ca != null ? ca.toLocalDateTime() : null
                ));
            }
            return list;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public List<Employee> findAllEmployees() {
        String sql = """
                SELECT employee_id, branch_id, employee_code, full_name, phone, role, hired_date
                FROM employee
                ORDER BY employee_id ASC
                """;
        List<Employee> list = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date hd = rs.getDate("hired_date");
                list.add(new Employee(
                        rs.getInt("employee_id"),
                        rs.getInt("branch_id"),
                        rs.getString("employee_code"),
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getString("role"),
                        hd != null ? hd.toLocalDate() : null
                ));
            }
            return list;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public List<SeatAvailabilityView> findSeatsForScreening(int screeningId) {
        String sql = """
                SELECT
                    st.seat_id,
                    st.room_id,
                    st.seat_row,
                    st.seat_number,
                    st.seat_type_code,
                    tp.type_name,
                    tp.surcharge,
                    (sc.base_price + tp.surcharge) AS final_unit_price,
                    CASE
                        WHEN EXISTS (
                            SELECT 1
                            FROM ticket tk
                            WHERE tk.screening_id = sc.screening_id
                              AND tk.seat_id = st.seat_id
                              AND tk.ticket_status = 'VALID'
                        ) THEN 1
                        ELSE 0
                    END AS is_occupied
                FROM screening sc
                JOIN seat st      ON st.room_id = sc.room_id
                JOIN seat_type tp ON tp.seat_type_code = st.seat_type_code
                WHERE sc.screening_id = ?
                ORDER BY st.seat_row ASC, st.seat_number ASC
                """;
        List<SeatAvailabilityView> list = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, screeningId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SeatAvailabilityView(
                            rs.getInt("seat_id"),
                            rs.getInt("room_id"),
                            rs.getString("seat_row"),
                            rs.getInt("seat_number"),
                            rs.getString("seat_type_code"),
                            rs.getString("type_name"),
                            rs.getBigDecimal("surcharge"),
                            rs.getBigDecimal("final_unit_price"),
                            rs.getInt("is_occupied") == 1
                    ));
                }
            }
            return list;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public LockedScreeningInfo lockScreeningForUpdate(Connection conn, int screeningId) throws SQLException {
        String sql = """
                SELECT screening_id, room_id, base_price, status
                FROM screening
                WHERE screening_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, screeningId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BusinessRuleException("Không tìm thấy suất chiếu #" + screeningId + ".");
                }
                return new LockedScreeningInfo(
                        rs.getInt("screening_id"),
                        rs.getInt("room_id"),
                        rs.getBigDecimal("base_price"),
                        rs.getString("status")
                );
            }
        }
    }

    public SeatPriceInfo inspectSeatForBooking(Connection conn, int screeningId, int seatId) throws SQLException {
        String sql = """
                SELECT
                    st.seat_id,
                    st.room_id,
                    CONCAT(st.seat_row, LPAD(st.seat_number, 2, '0')) AS seat_label,
                    tp.surcharge,
                    CASE
                        WHEN EXISTS (
                            SELECT 1 FROM ticket tk
                            WHERE tk.screening_id = ?
                              AND tk.seat_id = st.seat_id
                              AND tk.ticket_status = 'VALID'
                        ) THEN 1
                        ELSE 0
                    END AS already_occupied
                FROM seat st
                JOIN seat_type tp ON tp.seat_type_code = st.seat_type_code
                WHERE st.seat_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, screeningId);
            ps.setInt(2, seatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BusinessRuleException("Không tìm thấy ghế có mã #" + seatId + ".");
                }
                return new SeatPriceInfo(
                        rs.getInt("seat_id"),
                        rs.getInt("room_id"),
                        rs.getString("seat_label"),
                        rs.getBigDecimal("surcharge"),
                        rs.getInt("already_occupied") == 1
                );
            }
        }
    }

    public int insertBooking(
            Connection conn,
            String bookingCode,
            int screeningId,
            Integer customerId,
            int employeeId,
            LocalDateTime bookedAt,
            String bookingStatus
    ) throws SQLException {
        String sql = """
                INSERT INTO booking (booking_code, screening_id, customer_id, employee_id, booked_at, booking_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, bookingCode);
            ps.setInt(2, screeningId);
            if (customerId != null) {
                ps.setInt(3, customerId);
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setInt(4, employeeId);
            ps.setTimestamp(5, Timestamp.valueOf(bookedAt));
            ps.setString(6, bookingStatus);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("Không nhận được booking_id sau khi tạo đơn đặt vé.");
            }
        }
    }

    public void insertTicket(
            Connection conn,
            int bookingId,
            int screeningId,
            int seatId,
            BigDecimal unitPrice,
            String ticketStatus
    ) throws SQLException {
        String sql = """
                INSERT INTO ticket (booking_id, screening_id, seat_id, unit_price, ticket_status)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setInt(2, screeningId);
            ps.setInt(3, seatId);
            ps.setBigDecimal(4, unitPrice);
            ps.setString(5, ticketStatus);
            ps.executeUpdate();
        }
    }

    public int insertPayment(
            Connection conn,
            int bookingId,
            BigDecimal amount,
            String paymentMethod,
            String paymentStatus,
            LocalDateTime paidAt,
            String transactionRef
    ) throws SQLException {
        String sql = """
                INSERT INTO payment (booking_id, amount, payment_method, payment_status, paid_at, transaction_ref)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, bookingId);
            ps.setBigDecimal(2, amount);
            ps.setString(3, paymentMethod);
            ps.setString(4, paymentStatus);
            if (paidAt != null) {
                ps.setTimestamp(5, Timestamp.valueOf(paidAt));
            } else {
                ps.setNull(5, Types.TIMESTAMP);
            }
            ps.setString(6, transactionRef);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("Không nhận được payment_id sau khi thêm thanh toán.");
            }
        }
    }

    public void cancelBookingRecords(Connection conn, int bookingId) throws SQLException {
        String lockSql = "SELECT booking_id, booking_status FROM booking WHERE booking_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BusinessRuleException("Không tìm thấy đơn đặt vé #" + bookingId + ".");
                }
                if ("CANCELLED".equals(rs.getString("booking_status"))) {
                    throw new BusinessRuleException("Đơn đặt vé #" + bookingId + " đã ở trạng thái hủy trước đó.");
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE booking SET booking_status = 'CANCELLED' WHERE booking_id = ?")) {
            ps.setInt(1, bookingId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ticket SET ticket_status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE booking_id = ?")) {
            ps.setInt(1, bookingId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE payment SET payment_status = 'REFUNDED' WHERE booking_id = ? AND payment_status = 'SUCCESS'")) {
            ps.setInt(1, bookingId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE payment SET payment_status = 'FAILED' WHERE booking_id = ? AND payment_status = 'PENDING'")) {
            ps.setInt(1, bookingId);
            ps.executeUpdate();
        }
    }

    public void transitionPendingPayment(Connection conn, int bookingId, String targetPaymentStatus) throws SQLException {
        String lockSql = "SELECT booking_id, booking_status FROM booking WHERE booking_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BusinessRuleException("Không tìm thấy đơn đặt vé #" + bookingId + ".");
                }
                String currentStatus = rs.getString("booking_status");
                if (!"PENDING".equals(currentStatus)) {
                    throw new BusinessRuleException(
                            "Lỗi nghiệp vụ [BR-13]: Chỉ được chuyển tiếp thanh toán cho đơn đang ở trạng thái PENDING (trạng thái hiện tại: "
                                    + currentStatus + ").");
                }
            }
        }

        if ("SUCCESS".equals(targetPaymentStatus)) {
            int updatedPayments;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE payment SET payment_status = 'SUCCESS', paid_at = CURRENT_TIMESTAMP WHERE booking_id = ? AND payment_status = 'PENDING'")) {
                ps.setInt(1, bookingId);
                updatedPayments = ps.executeUpdate();
            }
            if (updatedPayments != 1) {
                throw new BusinessRuleException(
                        "Lỗi nghiệp vụ [BR-13]: Không tìm thấy giao dịch thanh toán PENDING hợp lệ cho đơn #" + bookingId + ".");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE booking SET booking_status = 'CONFIRMED' WHERE booking_id = ?")) {
                ps.setInt(1, bookingId);
                ps.executeUpdate();
            }
        } else if ("FAILED".equals(targetPaymentStatus)) {
            cancelBookingRecords(conn, bookingId);
        } else {
            throw new BusinessRuleException("Trạng thái chuyển tiếp thanh toán không hợp lệ: " + targetPaymentStatus);
        }
    }

    public List<BookingSummaryView> findRecentBookings(Integer screeningId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    bk.booking_id,
                    bk.booking_code,
                    bk.screening_id,
                    mv.title AS movie_title,
                    br.branch_name,
                    rm.room_name,
                    sc.start_time,
                    bk.customer_id,
                    COALESCE(cu.full_name, 'Khách vãng lai (Tại quầy)') AS customer_name,
                    em.full_name AS employee_name,
                    bk.booked_at,
                    bk.booking_status,
                    GROUP_CONCAT(CONCAT(st.seat_row, LPAD(st.seat_number, 2, '0')) ORDER BY st.seat_row, st.seat_number SEPARATOR ', ') AS seat_labels,
                    COUNT(tk.ticket_id) AS ticket_count,
                    COALESCE(SUM(tk.unit_price), 0.00) AS total_amount,
                    (SELECT pm.payment_method FROM payment pm WHERE pm.booking_id = bk.booking_id ORDER BY pm.payment_id DESC LIMIT 1) AS latest_method,
                    (SELECT pm.payment_status FROM payment pm WHERE pm.booking_id = bk.booking_id ORDER BY pm.payment_id DESC LIMIT 1) AS latest_payment_status
                FROM booking bk
                JOIN screening sc      ON sc.screening_id = bk.screening_id
                JOIN movie mv          ON mv.movie_id = sc.movie_id
                JOIN screening_room rm ON rm.room_id = sc.room_id
                JOIN cinema_branch br  ON br.branch_id = rm.branch_id
                LEFT JOIN customer cu  ON cu.customer_id = bk.customer_id
                JOIN employee em       ON em.employee_id = bk.employee_id
                LEFT JOIN ticket tk    ON tk.booking_id = bk.booking_id
                LEFT JOIN seat st      ON st.seat_id = tk.seat_id
                """);
        if (screeningId != null) {
            sql.append(" WHERE bk.screening_id = ?\n");
        }
        sql.append("""
                 GROUP BY
                     bk.booking_id, bk.booking_code, bk.screening_id, mv.title, br.branch_name, rm.room_name,
                     sc.start_time, bk.customer_id, cu.full_name, em.full_name, bk.booked_at, bk.booking_status
                 ORDER BY bk.booking_id DESC
                 LIMIT ?
                """);

        List<BookingSummaryView> list = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (screeningId != null) {
                ps.setInt(idx++, screeningId);
            }
            ps.setInt(idx, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp st = rs.getTimestamp("start_time");
                    Timestamp ba = rs.getTimestamp("booked_at");
                    int cid = rs.getInt("customer_id");
                    Integer customerIdObj = rs.wasNull() ? null : cid;
                    list.add(new BookingSummaryView(
                            rs.getInt("booking_id"),
                            rs.getString("booking_code"),
                            rs.getInt("screening_id"),
                            rs.getString("movie_title"),
                            rs.getString("branch_name"),
                            rs.getString("room_name"),
                            st != null ? st.toLocalDateTime() : null,
                            customerIdObj,
                            rs.getString("customer_name"),
                            rs.getString("employee_name"),
                            ba != null ? ba.toLocalDateTime() : null,
                            rs.getString("booking_status"),
                            rs.getString("seat_labels"),
                            rs.getInt("ticket_count"),
                            rs.getBigDecimal("total_amount"),
                            rs.getString("latest_method"),
                            rs.getString("latest_payment_status")
                    ));
                }
            }
            return list;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }
}
