package com.cinema.shared.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Quản lý cấu hình kết nối Cơ sở dữ liệu MySQL Server của ứng dụng.
 * Ưu tiên đọc từ biến môi trường, sau đó đến tệp cấu hình cục bộ config/application.properties
 * (được loại trừ khỏi Git). Không mã hóa cứng mật khẩu trong mã nguồn.
 */
public final class AppConfig {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public AppConfig(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public static AppConfig loadProduction() {
        Properties props = loadLocalPropertiesFile();
        String url = resolve("CINEMA_DB_URL", "db.url", props,
                "jdbc:mysql://127.0.0.1:3306/cinema_chain?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true&useSSL=false");
        String user = resolve("CINEMA_DB_USER", "db.user", props, "cinema_app");
        String pass = resolvePassword("CINEMA_DB_PASSWORD", "db.password", props);
        return new AppConfig(url, user, pass);
    }

    public static AppConfig loadTest() {
        Properties props = loadLocalPropertiesFile();
        String url = resolve("CINEMA_TEST_DB_URL", "test.db.url", props,
                "jdbc:mysql://127.0.0.1:3306/cinema_chain_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true&useSSL=false");
        String user = resolve("CINEMA_TEST_DB_USER", "test.db.user", props, "cinema_app");
        String pass = resolvePassword("CINEMA_TEST_DB_PASSWORD", "test.db.password", props);
        return new AppConfig(url, user, pass);
    }

    private static Properties loadLocalPropertiesFile() {
        Properties props = new Properties();
        Path localConfig = Path.of("config", "application.properties");
        if (Files.exists(localConfig)) {
            try (InputStream in = Files.newInputStream(localConfig)) {
                props.load(in);
            } catch (IOException ignored) {
                // Handled by missing password validation below if env var is also absent
            }
        }
        return props;
    }

    private static String resolve(String envKey, String propKey, Properties props, String fallback) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        String propVal = props.getProperty(propKey);
        if (propVal != null && !propVal.isBlank()) {
            return propVal.trim();
        }
        return fallback;
    }

    private static String resolvePassword(String envKey, String propKey, Properties props) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        String propVal = props.getProperty(propKey);
        if (propVal != null && !propVal.isBlank()) {
            return propVal.trim();
        }
        throw new IllegalStateException(
                "Chưa cấu hình mật khẩu kết nối MySQL. Vui lòng thiết lập biến môi trường "
                        + envKey + " hoặc khai báo '" + propKey + "' trong tệp cục bộ config/application.properties.");
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }
}
