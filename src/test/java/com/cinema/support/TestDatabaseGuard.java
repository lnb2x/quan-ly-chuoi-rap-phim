package com.cinema.support;

import com.cinema.shared.config.AppConfig;
import com.cinema.shared.db.ConnectionProvider;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Chốt chặn an toàn thực thi (Executable Test Guardrail) bảo vệ CSDL vận hành/demo {@code cinema_chain}.
 * <p>
 * Mọi bài kiểm thử có thay đổi/xóa dữ liệu ({@code *IT}, {@code GuiVerificationRunner}, kiểm thử lưu trữ qua khởi động lại)
 * bắt buộc phải đi qua chốt chặn này trước khi thực hiện bất kỳ thao tác nào:
 * <ul>
 *   <li>Từ chối ngay lập tức nếu URL hoặc {@code SELECT DATABASE()} trỏ vào {@code cinema_chain} hoặc khác {@code cinema_chain_test}.</li>
 *   <li>Từ chối nếu hệ quản trị đích không phải là MySQL Server 8.x thật (chặn MariaDB).</li>
 *   <li>Từ chối nếu kết nối ứng dụng sử dụng tài khoản đặc quyền {@code root}.</li>
 * </ul>
 */
public final class TestDatabaseGuard {

    public static final String EXPECTED_TEST_SCHEMA = "cinema_chain_test";
    public static final String PROTECTED_DEMO_SCHEMA = "cinema_chain";

    private TestDatabaseGuard() {
    }

    /**
     * Kiểm tra tĩnh cấu hình {@link AppConfig} trước khi mở kết nối CSDL kiểm thử.
     */
    public static void assertSafeTestConfig(AppConfig config) {
        if (config == null) {
            throw new SecurityException("Cấu hình kiểm thử CSDL không được phép null.");
        }
        String url = config.jdbcUrl();
        if (url == null || url.isBlank()) {
            throw new SecurityException("JDBC URL kiểm thử không được để trống.");
        }
        String lowerUrl = url.trim().toLowerCase(Locale.ROOT);
        if (!lowerUrl.startsWith("jdbc:mysql://")) {
            throw new SecurityException("Bắt buộc sử dụng trình điều khiển MySQL chính thức (jdbc:mysql://). URL hiện tại: "
                    + AppConfig.sanitizeJdbcUrl(url));
        }

        String schema = extractSchemaFromJdbcUrl(url);
        if (PROTECTED_DEMO_SCHEMA.equalsIgnoreCase(schema)) {
            throw new SecurityException(
                    "CHẶN AN TOÀN: Cấm tuyệt đối chạy kiểm thử thay đổi/xóa dữ liệu trên CSDL demo cần bảo toàn '"
                            + PROTECTED_DEMO_SCHEMA + "'!");
        }
        if (!EXPECTED_TEST_SCHEMA.equals(schema)) {
            throw new SecurityException(
                    "CHẶN AN TOÀN: Kiểm thử tích hợp chỉ được phép chạy trên '" + EXPECTED_TEST_SCHEMA
                            + "', nhưng cấu hình đang trỏ tới: '" + schema + "'.");
        }

        String user = config.username();
        if (user == null || user.isBlank() || "root".equalsIgnoreCase(user.trim())) {
            throw new SecurityException(
                    "CHẶN AN TOÀN: Ứng dụng và bộ kiểm thử không được dùng tài khoản 'root'; bắt buộc dùng tài khoản phân quyền tối thiểu (cinema_app).");
        }
    }

    /**
     * Mở kết nối kiểm thử thực tế tới MySQL Server và xác minh đồng thời cả cấu hình lẫn siêu dữ liệu phiên chạy thật.
     */
    public static ConnectionProvider verifyAndCreateTestProvider() {
        AppConfig testConfig = AppConfig.loadTest();
        assertSafeTestConfig(testConfig);
        ConnectionProvider cp = new ConnectionProvider(testConfig);
        verifyLiveTestConnection(cp);
        return cp;
    }

    public static void verifyLiveTestConnection(ConnectionProvider cp) {
        try (Connection conn = cp.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DATABASE() AS db_name, VERSION() AS ver, @@version_comment AS ver_comment, CURRENT_USER() AS cur_user")) {
            if (!rs.next()) {
                throw new SecurityException("Không đọc được siêu dữ liệu phiên kết nối MySQL.");
            }
            String liveDb = rs.getString("db_name");
            String version = rs.getString("ver");
            String comment = rs.getString("ver_comment");
            String currentUser = rs.getString("cur_user");

            if (PROTECTED_DEMO_SCHEMA.equalsIgnoreCase(liveDb) || !EXPECTED_TEST_SCHEMA.equals(liveDb)) {
                throw new SecurityException(
                        "CHẶN AN TOÀN RUNTIME: Phiên kết nối thực tế đang trỏ vào '" + liveDb
                                + "', không phải '" + EXPECTED_TEST_SCHEMA + "'!");
            }
            if (version == null || version.toLowerCase(Locale.ROOT).contains("mariadb")
                    || comment == null || !comment.toLowerCase(Locale.ROOT).contains("mysql")) {
                throw new SecurityException(
                        "CHẶN HỆ QUẢN TRỊ: Phiên kết nối không phải MySQL Server chính thức: " + version + " | " + comment);
            }
            int major = Integer.parseInt(version.split("\\.")[0]);
            if (major < 8) {
                throw new SecurityException("Yêu cầu MySQL Server >= 8.0, thực tế: " + version);
            }
            if (currentUser != null && currentUser.toLowerCase(Locale.ROOT).startsWith("root@")) {
                throw new SecurityException("CHẶN BẢO MẬT: Không cho phép chạy kiểm thử ứng dụng bằng tài khoản root (" + currentUser + ").");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không thể xác minh kết nối tới MySQL kiểm thử: " + ex.getMessage(), ex);
        }
    }

    public static String extractSchemaFromJdbcUrl(String jdbcUrl) {
        String stripped = jdbcUrl.trim();
        if (stripped.regionMatches(true, 0, "jdbc:", 0, 5)) {
            stripped = stripped.substring(5);
        }
        URI uri = URI.create(stripped);
        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            return "";
        }
        return path.substring(1);
    }
}
