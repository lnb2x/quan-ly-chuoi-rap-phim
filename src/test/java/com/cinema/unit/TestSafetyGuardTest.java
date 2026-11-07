package com.cinema.unit;

import com.cinema.shared.config.AppConfig;
import com.cinema.support.TestDatabaseGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử đơn vị cho chốt chặn bảo vệ CSDL demo {@code cinema_chain} và bảo mật cấu hình (Section 2).
 */
class TestSafetyGuardTest {

    @Test
    @DisplayName("Chốt chặn phải từ chối ngay lập tức nếu cấu hình test trỏ nhầm vào CSDL demo cinema_chain")
    void mustRejectProtectedProductionDatabaseInTestSuite() {
        AppConfig misconfigured = new AppConfig(
                "jdbc:mysql://127.0.0.1:3306/cinema_chain?useSSL=false",
                "cinema_app",
                "dummySecret"
        );
        SecurityException ex = assertThrows(SecurityException.class,
                () -> TestDatabaseGuard.assertSafeTestConfig(misconfigured));
        assertTrue(ex.getMessage().contains("cinema_chain"));
    }

    @Test
    @DisplayName("Chốt chặn phải từ chối nếu cấu hình dùng MariaDB driver hoặc tài khoản đặc quyền root")
    void mustRejectMariaDbDriverAndRootUser() {
        AppConfig mariaConfig = new AppConfig(
                "jdbc:mariadb://127.0.0.1:3306/cinema_chain_test",
                "cinema_app",
                "dummySecret"
        );
        assertThrows(SecurityException.class, () -> TestDatabaseGuard.assertSafeTestConfig(mariaConfig));

        AppConfig rootConfig = new AppConfig(
                "jdbc:mysql://127.0.0.1:3306/cinema_chain_test?useSSL=false",
                "root",
                ""
        );
        assertThrows(SecurityException.class, () -> TestDatabaseGuard.assertSafeTestConfig(rootConfig));
    }

    @Test
    @DisplayName("Chốt chặn chấp nhận cấu hình đúng chuẩn cinema_chain_test với tài khoản cinema_app và ẩn mật khẩu trong toString()")
    void mustAcceptValidTestConfigAndMaskPasswordInLogs() {
        AppConfig validTestConfig = new AppConfig(
                "jdbc:mysql://127.0.0.1:3306/cinema_chain_test?password=SuperSecretLeak&useSSL=false",
                "cinema_app",
                "SuperSecretLeak"
        );
        assertDoesNotThrow(() -> TestDatabaseGuard.assertSafeTestConfig(validTestConfig));
        assertFalse(validTestConfig.toString().contains("SuperSecretLeak"),
                "AppConfig.toString() tuyệt đối không được làm lộ mật khẩu trong log");
        assertEquals("jdbc:mysql://127.0.0.1:3306/cinema_chain_test",
                AppConfig.sanitizeJdbcUrl(validTestConfig.jdbcUrl()));
    }
}
