package com.cinema.shared.db;

import com.cinema.shared.validation.BusinessRuleException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Quản lý ranh giới giao dịch (Transaction Boundary) an toàn cho các nghiệp vụ
 * quan trọng như Lập lịch chiếu và Đặt vé / Hủy vé.
 */
public final class TransactionTemplate {

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T doInTransaction(Connection connection) throws SQLException;
    }

    private final ConnectionProvider connectionProvider;

    public TransactionTemplate(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public <T> T execute(TransactionCallback<T> callback) {
        try (Connection conn = connectionProvider.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                T result = callback.doInTransaction(conn);
                conn.commit();
                return result;
            } catch (BusinessRuleException bre) {
                rollbackQuietly(conn);
                throw bre;
            } catch (SQLException sqlEx) {
                rollbackQuietly(conn);
                throw translateSqlException(sqlEx);
            } catch (RuntimeException rex) {
                rollbackQuietly(conn);
                throw rex;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                    // Connection is closing anyway
                }
            }
        } catch (SQLException ex) {
            throw translateSqlException(ex);
        }
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // Ignore secondary rollback failure
        }
    }

    public static RuntimeException translateSqlException(SQLException ex) {
        String state = ex.getSQLState();
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if ("45000".equals(state)) {
            return new BusinessRuleException(msg, ex);
        }
        if ("23000".equals(state)) {
            if (msg.contains("uq_ticket_active_seat")) {
                return new BusinessRuleException(
                        "Lỗi nghiệp vụ [BR-10]: Ghế đã được khách hàng khác đặt trước cho suất chiếu này.", ex);
            }
            if (msg.contains("uq_payment_one_success")) {
                return new BusinessRuleException(
                        "Lỗi nghiệp vụ [BR-13]: Đơn đặt vé này đã có một giao dịch thanh toán thành công.", ex);
            }
            return new BusinessRuleException("Vi phạm ràng buộc toàn vẹn dữ liệu: " + msg, ex);
        }
        if ("40001".equals(state) || ex.getErrorCode() == 1213 || ex.getErrorCode() == 1205) {
            return new BusinessRuleException(
                    "Giao dịch bị xung đột đồng thời trên cùng tài nguyên, vui lòng thử lại: " + msg, ex);
        }
        return new DataAccessException("Lỗi thao tác Cơ sở dữ liệu: " + msg, ex);
    }
}
