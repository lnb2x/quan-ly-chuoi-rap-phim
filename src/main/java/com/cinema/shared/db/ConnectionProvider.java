package com.cinema.shared.db;

import com.cinema.shared.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Cung cấp kết nối JDBC tới MySQL Server 8.4 LTS từ cấu hình AppConfig.
 */
public final class ConnectionProvider {

    private final AppConfig config;

    public ConnectionProvider(AppConfig config) {
        this.config = config;
    }

    public Connection getConnection() {
        try {
            return DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
        } catch (SQLException ex) {
            throw new DataAccessException(
                    "Không thể kết nối tới Cơ sở dữ liệu (" + AppConfig.sanitizeJdbcUrl(config.jdbcUrl()) + "): " + ex.getMessage(), ex);
        }
    }

    /**
     * Ghi nhận thông tin phiên kết nối khởi động an toàn (chỉ đọc siêu dữ liệu, ẩn hoàn toàn mật khẩu, không chạy DDL/reset).
     */
    public String logSafeStartupDiagnostics() {
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DATABASE() AS db_name, VERSION() AS mysql_ver, @@port AS port")) {
            String db = rs.next() ? rs.getString("db_name") : "unknown";
            String ver = rs.getString("mysql_ver");
            int port = rs.getInt("port");
            String line = String.format(
                    "[APP-STARTUP] Time=%s | Mode=NORMAL_READ_ONLY_INIT | JDBC=%s | DB=%s | User=%s | MySQL=%s | Port=%d | Java=%s | DestructiveReset=DISABLED",
                    LocalDateTime.now(),
                    AppConfig.sanitizeJdbcUrl(config.jdbcUrl()),
                    db,
                    config.username(),
                    ver,
                    port,
                    System.getProperty("java.version")
            );
            System.out.println(line);
            return line;
        } catch (SQLException ex) {
            throw new DataAccessException("Lỗi kiểm tra kết nối MySQL khi khởi động: " + ex.getMessage(), ex);
        }
    }

    public AppConfig config() {
        return config;
    }
}
