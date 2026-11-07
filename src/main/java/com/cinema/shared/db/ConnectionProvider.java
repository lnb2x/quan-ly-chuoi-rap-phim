package com.cinema.shared.db;

import com.cinema.shared.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
                    "Không thể kết nối tới Cơ sở dữ liệu (" + config.jdbcUrl() + "): " + ex.getMessage(), ex);
        }
    }

    public AppConfig config() {
        return config;
    }
}
