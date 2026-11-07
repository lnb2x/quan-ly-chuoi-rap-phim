package com.cinema.feature.report.sales;

import com.cinema.domain.TicketSalesReportData;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tầng truy cập dữ liệu cho Báo cáo Tình hình Bán vé & Lấp đầy Suất chiếu (Screen 5 - R7).
 */
public final class TicketSalesReportRepository {

    private final ConnectionProvider connectionProvider;

    public TicketSalesReportRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<TicketSalesReportData.ScreeningSalesRow> findScreeningSales(
            LocalDate fromDate,
            LocalDate toDate,
            Integer branchId,
            Integer screeningId
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    screening_id,
                    screening_date,
                    start_time,
                    end_time,
                    branch_name,
                    room_name,
                    room_type,
                    movie_title,
                    room_capacity,
                    tickets_sold,
                    available_seats,
                    occupancy_rate_pct,
                    sales_revenue,
                    screening_status
                FROM vw_screening_occupancy
                WHERE screening_date BETWEEN ? AND ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(Date.valueOf(fromDate));
        params.add(Date.valueOf(toDate));

        if (branchId != null) {
            sql.append(" AND branch_id = ?");
            params.add(branchId);
        }
        if (screeningId != null) {
            sql.append(" AND screening_id = ?");
            params.add(screeningId);
        }

        sql.append(" ORDER BY start_time ASC, screening_id ASC");

        List<TicketSalesReportData.ScreeningSalesRow> rows = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date sd = rs.getDate("screening_date");
                    Timestamp st = rs.getTimestamp("start_time");
                    Timestamp et = rs.getTimestamp("end_time");
                    rows.add(new TicketSalesReportData.ScreeningSalesRow(
                            rs.getInt("screening_id"),
                            sd != null ? sd.toLocalDate() : null,
                            st != null ? st.toLocalDateTime() : null,
                            et != null ? et.toLocalDateTime() : null,
                            rs.getString("branch_name"),
                            rs.getString("room_name"),
                            rs.getString("room_type"),
                            rs.getString("movie_title"),
                            rs.getInt("room_capacity"),
                            rs.getInt("tickets_sold"),
                            rs.getInt("available_seats"),
                            rs.getBigDecimal("occupancy_rate_pct"),
                            rs.getBigDecimal("sales_revenue"),
                            rs.getString("screening_status")
                    ));
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    /**
     * Truy vấn SQL độc lập trực tiếp từ bảng ticket + booking + screening + EXISTS(payment SUCCESS)
     * để kiểm chứng chéo độ chính xác tuyệt đối của Báo cáo Bán vé (Gate E).
     */
    public BigDecimal queryIndependentVerifiedSalesRevenue(
            LocalDate fromDate,
            LocalDate toDate,
            Integer branchId,
            Integer screeningId
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(tk.unit_price), 0.00) AS total_sales_rev
                FROM ticket tk
                JOIN booking bk        ON bk.booking_id = tk.booking_id
                JOIN screening sc      ON sc.screening_id = tk.screening_id
                JOIN screening_room rm ON rm.room_id = sc.room_id
                WHERE tk.ticket_status = 'VALID'
                  AND bk.booking_status = 'CONFIRMED'
                  AND DATE(sc.start_time) BETWEEN ? AND ?
                  AND EXISTS (
                      SELECT 1
                      FROM payment pm
                      WHERE pm.booking_id = bk.booking_id
                        AND pm.payment_status = 'SUCCESS'
                  )
                """);
        List<Object> params = new ArrayList<>();
        params.add(Date.valueOf(fromDate));
        params.add(Date.valueOf(toDate));
        if (branchId != null) {
            sql.append(" AND rm.branch_id = ?");
            params.add(branchId);
        }
        if (screeningId != null) {
            sql.append(" AND sc.screening_id = ?");
            params.add(screeningId);
        }

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("total_sales_rev");
                }
                return BigDecimal.ZERO;
            }
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }
}
