package com.cinema.feature.search;

import com.cinema.domain.ScreeningDetailView;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tầng truy cập dữ liệu cho màn hình Tra cứu Suất chiếu (Screen 3 - R6).
 * Tất cả các điều kiện lọc đều truyền qua PreparedStatement tham số hóa,
 * hỗ trợ phân trang tường minh (LIMIT / OFFSET) thay vì cắt ngầm LIMIT 1000.
 */
public final class ScreeningSearchRepository {

    public record SearchSummary(int totalCount, int totalAvailableSeats, int totalSoldSeats) {
    }

    private final ConnectionProvider connectionProvider;

    public ScreeningSearchRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<ScreeningDetailView> searchScreenings(
            String movieKeyword,
            Integer movieId,
            Integer branchId,
            LocalDate screeningDate,
            LocalTime fromTime,
            LocalTime toTime,
            boolean onlyOpenWithAvailableSeats
    ) {
        return querySearchInternal(
                movieKeyword, movieId, branchId, screeningDate, fromTime, toTime,
                onlyOpenWithAvailableSeats, null, null);
    }

    public SearchSummary summarizeSearch(
            String movieKeyword,
            Integer movieId,
            Integer branchId,
            LocalDate screeningDate,
            LocalTime fromTime,
            LocalTime toTime,
            boolean onlyOpenWithAvailableSeats
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COUNT(*) AS total_count,
                    COALESCE(SUM(available_seats), 0) AS sum_available,
                    COALESCE(SUM(sold_seats), 0) AS sum_sold
                FROM vw_screening_search
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        appendSearchFilters(
                sql, params, movieKeyword, movieId, branchId, screeningDate,
                fromTime, toTime, onlyOpenWithAvailableSeats);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SearchSummary(
                            rs.getInt("total_count"),
                            rs.getInt("sum_available"),
                            rs.getInt("sum_sold")
                    );
                }
                return new SearchSummary(0, 0, 0);
            }
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public List<ScreeningDetailView> searchScreeningsPage(
            String movieKeyword,
            Integer movieId,
            Integer branchId,
            LocalDate screeningDate,
            LocalTime fromTime,
            LocalTime toTime,
            boolean onlyOpenWithAvailableSeats,
            int limit,
            int offset
    ) {
        return querySearchInternal(
                movieKeyword, movieId, branchId, screeningDate, fromTime, toTime,
                onlyOpenWithAvailableSeats, limit, offset);
    }

    private List<ScreeningDetailView> querySearchInternal(
            String movieKeyword,
            Integer movieId,
            Integer branchId,
            LocalDate screeningDate,
            LocalTime fromTime,
            LocalTime toTime,
            boolean onlyOpenWithAvailableSeats,
            Integer limit,
            Integer offset
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    screening_id, movie_id, movie_code, movie_title, duration_minutes, age_rating,
                    room_id, room_name, room_type, branch_id, branch_code, branch_name,
                    start_time, end_time, base_price, screening_status,
                    total_seats, sold_seats, available_seats
                FROM vw_screening_search
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        appendSearchFilters(
                sql, params, movieKeyword, movieId, branchId, screeningDate,
                fromTime, toTime, onlyOpenWithAvailableSeats);

        sql.append(" ORDER BY start_time ASC, branch_id ASC, room_id ASC, screening_id ASC");
        if (limit != null && offset != null) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);
        }

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

    private static void appendSearchFilters(
            StringBuilder sql,
            List<Object> params,
            String movieKeyword,
            Integer movieId,
            Integer branchId,
            LocalDate screeningDate,
            LocalTime fromTime,
            LocalTime toTime,
            boolean onlyOpenWithAvailableSeats
    ) {
        if (movieKeyword != null && !movieKeyword.isBlank()) {
            sql.append(" AND (LOWER(movie_title) LIKE ? OR LOWER(movie_code) LIKE ?)");
            String likePattern = "%" + movieKeyword.trim().toLowerCase() + "%";
            params.add(likePattern);
            params.add(likePattern);
        }
        if (movieId != null) {
            sql.append(" AND movie_id = ?");
            params.add(movieId);
        }
        if (branchId != null) {
            sql.append(" AND branch_id = ?");
            params.add(branchId);
        }
        if (screeningDate != null) {
            sql.append(" AND screening_date = ?");
            params.add(Date.valueOf(screeningDate));
        }
        if (fromTime != null) {
            sql.append(" AND TIME(start_time) >= ?");
            params.add(Time.valueOf(fromTime));
        }
        if (toTime != null) {
            sql.append(" AND TIME(start_time) <= ?");
            params.add(Time.valueOf(toTime));
        }
        if (onlyOpenWithAvailableSeats) {
            sql.append(" AND screening_status = 'OPEN' AND available_seats > 0");
        }
    }
}
