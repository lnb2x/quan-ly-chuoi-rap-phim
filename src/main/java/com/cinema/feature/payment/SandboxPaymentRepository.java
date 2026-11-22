package com.cinema.feature.payment;

import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SandboxPaymentRepository {

    public record Payment(
            int bookingId,
            int paymentId,
            BigDecimal amount,
            String bookingStatus,
            String paymentStatus,
            String reference,
            LocalDateTime created
    ) {
    }

    private final ConnectionProvider cp;

    public SandboxPaymentRepository(ConnectionProvider cp) {
        this.cp = cp;
    }

    public void assertTestDatabase() {
        try (Connection c = cp.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT DATABASE(),CURRENT_USER(),VERSION(),@@version_comment")) {
            if (!r.next()) {
                throw new SecurityException("Không đọc được thông tin phiên CSDL sandbox.");
            }
            String db = r.getString(1);
            String user = r.getString(2);
            String ver = r.getString(3);
            String comment = r.getString(4);
            boolean genuineMySql84 = ver != null
                    && ver.startsWith("8.4.")
                    && !ver.toLowerCase(java.util.Locale.ROOT).contains("mariadb")
                    && comment != null
                    && !comment.toLowerCase(java.util.Locale.ROOT).contains("mariadb")
                    && (comment.contains("MySQL") || comment.contains("Ubuntu"));
            if (!"cinema_chain_test".equals(db)
                    || user == null
                    || !user.startsWith("cinema_app@")
                    || !genuineMySql84) {
                throw new SecurityException("SePay Sandbox chỉ được chạy bằng cinema_app trên MySQL 8.4 cinema_chain_test.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không xác minh được DB sandbox.", e);
        }
    }

    public void acquireProviderTxLock(Connection c, long sepayTxId) throws SQLException {
        if (!"cinema_chain_test".equals(c.getCatalog())) {
            throw new SecurityException("Kết nối giao dịch không thuộc cinema_chain_test.");
        }
        try (PreparedStatement p = c.prepareStatement("SELECT GET_LOCK(CONCAT('sepay_tx_', ?), 10)")) {
            p.setLong(1, sepayTxId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || r.getInt(1) != 1) {
                    throw new SQLException("Không lấy được khóa đồng bộ cho mã giao dịch SePay #" + sepayTxId, "40001", 1205);
                }
            }
        }
    }

    public Payment lock(Connection c, int bookingId, int paymentId) throws SQLException {
        if (!"cinema_chain_test".equals(c.getCatalog())) {
            throw new SecurityException("Kết nối giao dịch không thuộc cinema_chain_test.");
        }
        try (PreparedStatement p = c.prepareStatement(
                "SELECT booking_status,booked_at FROM booking WHERE booking_id=? FOR UPDATE")) {
            p.setInt(1, bookingId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) {
                    return null;
                }
                String status = r.getString(1);
                LocalDateTime created = r.getObject(2, LocalDateTime.class);
                try (PreparedStatement q = c.prepareStatement(
                        "SELECT payment_id,amount,payment_status,transaction_ref FROM payment "
                                + "WHERE booking_id=? AND (?=0 OR payment_id=?) ORDER BY payment_id DESC FOR UPDATE")) {
                    q.setInt(1, bookingId);
                    q.setInt(2, paymentId);
                    q.setInt(3, paymentId);
                    try (ResultSet t = q.executeQuery()) {
                        if (!t.next()) {
                            return null;
                        }
                        return new Payment(
                                bookingId, t.getInt(1), t.getBigDecimal(2),
                                status, t.getString(3), t.getString(4), created
                        );
                    }
                }
            }
        }
    }

    public boolean isProviderTxUsedElsewhere(Connection c, long sepayTxId, int currentPaymentId) throws SQLException {
        String sql = "SELECT 1 FROM payment WHERE payment_id <> ? AND transaction_ref IN (?, ?) LIMIT 1";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, currentPaymentId);
            p.setString(2, "SEPAY-SUCCESS-" + sepayTxId);
            p.setString(3, "SEPAY-LATE-" + sepayTxId);
            try (ResultSet r = p.executeQuery()) {
                return r.next();
            }
        }
    }

    public Optional<SandboxPaymentService.CheckoutStatus> findCheckoutStatus(int bookingId) {
        String sql = """
                SELECT b.booking_id, b.booking_status, p.payment_status, p.transaction_ref
                FROM booking b
                JOIN payment p ON p.booking_id = b.booking_id
                WHERE b.booking_id = ?
                ORDER BY p.payment_id DESC
                LIMIT 1
                """;
        try (Connection c = cp.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, bookingId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SandboxPaymentService.CheckoutStatus(
                        r.getInt(1), r.getString(2), r.getString(3), r.getString(4)
                ));
            }
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public List<Integer> expired(LocalDateTime cutoff) {
        String sql = "SELECT DISTINCT b.booking_id FROM booking b JOIN payment p ON p.booking_id=b.booking_id "
                + "WHERE b.booking_status='PENDING' AND p.payment_status='PENDING' "
                + "AND p.transaction_ref LIKE 'SEPAY-%' AND b.booked_at<=?";
        try (Connection c = cp.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, cutoff);
            List<Integer> result = new ArrayList<>();
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    result.add(r.getInt(1));
                }
            }
            return result;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public void reference(Connection c, int paymentId, String reference) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE payment SET transaction_ref=? WHERE payment_id=?")) {
            p.setString(1, reference);
            p.setInt(2, paymentId);
            p.executeUpdate();
        }
    }
}
