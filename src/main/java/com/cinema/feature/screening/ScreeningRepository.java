package com.cinema.feature.screening;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.Movie;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.ScreeningRoom;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tầng truy cập dữ liệu cho phân hệ Quản lý Suất chiếu (Screen 1 - R5).
 * Sử dụng PreparedStatement hoàn toàn và hỗ trợ khóa dòng phòng chiếu (FOR UPDATE)
 * để chống trùng lịch chiếu dưới tải đồng thời.
 */
public final class ScreeningRepository {

    private final ConnectionProvider connectionProvider;

    public ScreeningRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public List<CinemaBranch> findAllBranches() {
        String sql = """
                SELECT branch_id, branch_code, branch_name, address, city, phone, active
                FROM cinema_branch
                WHERE active = 1
                ORDER BY branch_id ASC
                """;
        List<CinemaBranch> result = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new CinemaBranch(
                        rs.getInt("branch_id"),
                        rs.getString("branch_code"),
                        rs.getString("branch_name"),
                        rs.getString("address"),
                        rs.getString("city"),
                        rs.getString("phone"),
                        rs.getBoolean("active")
                ));
            }
            return result;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public List<ScreeningRoom> findRoomsByBranch(Integer branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.room_id, r.branch_id, b.branch_name, r.room_name, r.room_type, r.active
                FROM screening_room r
                JOIN cinema_branch b ON b.branch_id = r.branch_id
                WHERE r.active = 1
                """);
        if (branchId != null) {
            sql.append(" AND r.branch_id = ?");
        }
        sql.append(" ORDER BY r.branch_id ASC, r.room_id ASC");

        List<ScreeningRoom> result = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (branchId != null) {
                ps.setInt(1, branchId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ScreeningRoom(
                            rs.getInt("room_id"),
                            rs.getInt("branch_id"),
                            rs.getString("branch_name"),
                            rs.getString("room_name"),
                            rs.getString("room_type"),
                            rs.getBoolean("active")
                    ));
                }
            }
            return result;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public List<Movie> findAllMovies() {
        String sql = """
                SELECT movie_id, movie_code, title, duration_minutes, release_date, age_rating, country, description
                FROM movie
                ORDER BY movie_id ASC
                """;
        List<Movie> result = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date relDate = rs.getDate("release_date");
                result.add(new Movie(
                        rs.getInt("movie_id"),
                        rs.getString("movie_code"),
                        rs.getString("title"),
                        rs.getInt("duration_minutes"),
                        relDate != null ? relDate.toLocalDate() : null,
                        rs.getString("age_rating"),
                        rs.getString("country"),
                        rs.getString("description")
                ));
            }
            return result;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public Optional<Movie> findMovieById(Connection conn, int movieId) throws SQLException {
        String sql = """
                SELECT movie_id, movie_code, title, duration_minutes, release_date, age_rating, country, description
                FROM movie
                WHERE movie_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date relDate = rs.getDate("release_date");
                    return Optional.of(new Movie(
                            rs.getInt("movie_id"),
                            rs.getString("movie_code"),
                            rs.getString("title"),
                            rs.getInt("duration_minutes"),
                            relDate != null ? relDate.toLocalDate() : null,
                            rs.getString("age_rating"),
                            rs.getString("country"),
                            rs.getString("description")
                    ));
                }
                return Optional.empty();
            }
        }
    }

    public void lockRoomAndCheckActive(Connection conn, int roomId) throws SQLException {
        String sql = "SELECT room_id, active FROM screening_room WHERE room_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BusinessRuleException("Lỗi nghiệp vụ [BR-04]: Phòng chiếu không tồn tại.");
                }
                if (!rs.getBoolean("active")) {
                    throw new BusinessRuleException("Lỗi nghiệp vụ [BR-01]: Phòng chiếu đang tạm ngưng hoạt động.");
                }
            }
        }
    }

    public List<ScreeningDetailView> findOverlappingScreenings(
            Connection conn,
            int roomId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer excludeScreeningId
    ) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT screening_id, movie_id, movie_code, movie_title, duration_minutes, age_rating,
                       room_id, room_name, room_type, branch_id, branch_code, branch_name,
                       start_time, end_time, base_price, screening_status, total_seats, sold_seats, available_seats
                FROM vw_screening_search
                WHERE room_id = ?
                  AND screening_status = 'OPEN'
                  AND start_time < ?
                  AND end_time > ?
                """);
        if (excludeScreeningId != null) {
            sql.append(" AND screening_id <> ?");
        }

        List<ScreeningDetailView> conflicts = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, roomId);
            ps.setTimestamp(2, Timestamp.valueOf(endTime));
            ps.setTimestamp(3, Timestamp.valueOf(startTime));
            if (excludeScreeningId != null) {
                ps.setInt(4, excludeScreeningId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    conflicts.add(mapScreeningDetail(rs));
                }
            }
        }
        return conflicts;
    }

    public int insertScreening(
            Connection conn,
            int movieId,
            int roomId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal basePrice,
            String status
    ) throws SQLException {
        String sql = """
                INSERT INTO screening (movie_id, room_id, start_time, end_time, base_price, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, movieId);
            ps.setInt(2, roomId);
            ps.setTimestamp(3, Timestamp.valueOf(startTime));
            ps.setTimestamp(4, Timestamp.valueOf(endTime));
            ps.setBigDecimal(5, basePrice);
            ps.setString(6, status);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("Không nhận được khóa chính screening_id sau khi thêm suất chiếu.");
            }
        }
    }

    public void updateScreening(
            Connection conn,
            int screeningId,
            int movieId,
            int roomId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal basePrice,
            String status
    ) throws SQLException {
        String sql = """
                UPDATE screening
                SET movie_id = ?, room_id = ?, start_time = ?, end_time = ?, base_price = ?, status = ?
                WHERE screening_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            ps.setInt(2, roomId);
            ps.setTimestamp(3, Timestamp.valueOf(startTime));
            ps.setTimestamp(4, Timestamp.valueOf(endTime));
            ps.setBigDecimal(5, basePrice);
            ps.setString(6, status);
            ps.setInt(7, screeningId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new BusinessRuleException("Không tìm thấy suất chiếu #" + screeningId + " để cập nhật.");
            }
        }
    }

    public List<ScreeningDetailView> findScreenings(Integer branchId, Integer roomId, Integer movieId, LocalDate dateFilter) {
        StringBuilder sql = new StringBuilder("""
                SELECT screening_id, movie_id, movie_code, movie_title, duration_minutes, age_rating,
                       room_id, room_name, room_type, branch_id, branch_code, branch_name,
                       start_time, end_time, base_price, screening_status, total_seats, sold_seats, available_seats
                FROM vw_screening_search
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (branchId != null) {
            sql.append(" AND branch_id = ?");
            params.add(branchId);
        }
        if (roomId != null) {
            sql.append(" AND room_id = ?");
            params.add(roomId);
        }
        if (movieId != null) {
            sql.append(" AND movie_id = ?");
            params.add(movieId);
        }
        if (dateFilter != null) {
            sql.append(" AND screening_date = ?");
            params.add(Date.valueOf(dateFilter));
        }
        sql.append(" ORDER BY start_time DESC, screening_id DESC LIMIT 200");

        List<ScreeningDetailView> result = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapScreeningDetail(rs));
                }
            }
            return result;
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public Optional<ScreeningDetailView> findScreeningById(int screeningId) {
        String sql = """
                SELECT screening_id, movie_id, movie_code, movie_title, duration_minutes, age_rating,
                       room_id, room_name, room_type, branch_id, branch_code, branch_name,
                       start_time, end_time, base_price, screening_status, total_seats, sold_seats, available_seats
                FROM vw_screening_search
                WHERE screening_id = ?
                """;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, screeningId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapScreeningDetail(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw TransactionTemplate.translateSqlException(ex);
        }
    }

    public static ScreeningDetailView mapScreeningDetail(ResultSet rs) throws SQLException {
        Timestamp st = rs.getTimestamp("start_time");
        Timestamp et = rs.getTimestamp("end_time");
        return new ScreeningDetailView(
                rs.getInt("screening_id"),
                rs.getInt("movie_id"),
                rs.getString("movie_code"),
                rs.getString("movie_title"),
                rs.getInt("duration_minutes"),
                rs.getString("age_rating"),
                rs.getInt("room_id"),
                rs.getString("room_name"),
                rs.getString("room_type"),
                rs.getInt("branch_id"),
                rs.getString("branch_code"),
                rs.getString("branch_name"),
                st != null ? st.toLocalDateTime() : null,
                et != null ? et.toLocalDateTime() : null,
                rs.getBigDecimal("base_price"),
                rs.getString("screening_status"),
                rs.getInt("total_seats"),
                rs.getInt("sold_seats"),
                rs.getInt("available_seats")
        );
    }
}
