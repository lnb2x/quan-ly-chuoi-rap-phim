package com.cinema.feature.report.revenue;

import com.cinema.domain.RevenueReportData;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tầng truy cập dữ liệu cho Báo cáo Doanh thu Chuỗi rạp (Screen 4 - R7).
 * Tuân thủ nghiêm ngặt quy tắc ghi nhận doanh thu BR-14 & BR-15:
 * Chỉ cộng vé VALID thuộc đơn CONFIRMED có giao dịch thanh toán SUCCESS,
 * tuyệt đối không nhân bản doanh thu khi một đơn có nhiều lần thử thanh toán.
 */
public final class RevenueReportRepository {

    private final ConnectionProvider connectionProvider;

    public RevenueReportRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<RevenueReportData.BranchRevenueRow> findBranchSummaries(
            LocalDate fromDate,
            LocalDate toDate,
            Integer branchId
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    br.branch_id,
                    br.branch_code,
                    br.branch_name,
                    br.city,
                    COUNT(DISTINCT vr.booking_id)              AS paid_bookings,
                    COALESCE(SUM(vr.valid_ticket_count), 0)    AS valid_tickets,
                    COALESCE(SUM(vr.recognized_revenue), 0.00) AS paid_revenue
                FROM cinema_branch br
                LEFT JOIN vw_verified_booking_revenue vr
                    ON vr.branch_id = br.branch_id
                   AND vr.revenue_date BETWEEN ? AND ?
                WHERE br.active = 1
                """);
        if (branchId != null) {
            sql.append(" AND br.branch_id = ?\n");
        }
        sql.append("""
                 GROUP BY br.branch_id, br.branch_code, br.branch_name, br.city
                 ORDER BY paid_revenue DESC, br.branch_id ASC
                """);

        List<RevenueReportData.BranchRevenueRow> rows = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setDate(1, Date.valueOf(fromDate));
            ps.setDate(2, Date.valueOf(toDate));
            if (branchId != null) {
                ps.setInt(3, branchId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new RevenueReportData.BranchRevenueRow(
                            rs.getInt("branch_id"),
                            rs.getString("branch_code"),
                            rs.getString("branch_name"),
                            rs.getString("city"),
                            rs.getInt("paid_bookings"),
                            rs.getInt("valid_tickets"),
                            rs.getBigDecimal("paid_revenue")
                    ));
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public List<RevenueReportData.DailyRevenueRow> findDailyDetails(
            LocalDate fromDate,
            LocalDate toDate,
            Integer branchId
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    revenue_date,
                    branch_code,
                    branch_name,
                    paid_booking_count,
                    paid_ticket_count,
                    total_paid_revenue
                FROM vw_branch_daily_revenue
                WHERE revenue_date BETWEEN ? AND ?
                """);
        if (branchId != null) {
            sql.append(" AND branch_id = ?");
        }
        sql.append(" ORDER BY revenue_date ASC, branch_code ASC");

        List<RevenueReportData.DailyRevenueRow> rows = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setDate(1, Date.valueOf(fromDate));
            ps.setDate(2, Date.valueOf(toDate));
            if (branchId != null) {
                ps.setInt(3, branchId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("revenue_date");
                    rows.add(new RevenueReportData.DailyRevenueRow(
                            d != null ? d.toLocalDate() : null,
                            rs.getString("branch_code"),
                            rs.getString("branch_name"),
                            rs.getInt("paid_booking_count"),
                            rs.getInt("paid_ticket_count"),
                            rs.getBigDecimal("total_paid_revenue")
                    ));
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    /**
     * Truy vấn SQL độc lập trực tiếp từ bảng ticket + booking + EXISTS(payment SUCCESS)
     * để kiểm chứng chéo độ chính xác tuyệt đối của Báo cáo Doanh thu (Gate E).
     */
    public BigDecimal queryIndependentVerifiedRevenue(LocalDate fromDate, LocalDate toDate, Integer branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(tk.unit_price), 0.00) AS total_rev
                FROM ticket tk
                JOIN booking bk        ON bk.booking_id = tk.booking_id
                JOIN screening sc      ON sc.screening_id = bk.screening_id
                JOIN screening_room rm ON rm.room_id = sc.room_id
                WHERE tk.ticket_status = 'VALID'
                  AND bk.booking_status = 'CONFIRMED'
                  AND EXISTS (
                      SELECT 1
                      FROM payment pm
                      WHERE pm.booking_id = bk.booking_id
                        AND pm.payment_status = 'SUCCESS'
                        AND DATE(pm.paid_at) BETWEEN ? AND ?
                  )
                """);
        if (branchId != null) {
            sql.append(" AND rm.branch_id = ?");
        }

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setDate(1, Date.valueOf(fromDate));
            ps.setDate(2, Date.valueOf(toDate));
            if (branchId != null) {
                ps.setInt(3, branchId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("total_rev");
                }
                return BigDecimal.ZERO;
            }
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }
}
