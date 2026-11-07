package com.cinema.unit;

import com.cinema.shared.config.AppConfig;
import com.cinema.support.TestDatabaseGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử đơn vị và hành vi tiến trình cho chốt chặn bảo vệ CSDL demo {@code cinema_chain},
 * tách biệt script kiểm thử khỏi provisioning và chống lộ thông tin bí mật trên tham số dòng lệnh (Section 3).
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

    @Test
    @DisplayName("Kiểm chứng hành vi thực thi: setup.ps1 bắt buộc chặn đứng -CleanReset và chặn đứng -ResetTestDb nhắm vào cinema_chain")
    void setupScriptMustBehaviorallyRejectCleanResetAndRejectDemoDbTarget() throws Exception {
        // 1. Thử chạy setup.ps1 -CleanReset -> bắt buộc trả về mã thoát khác 0
        Process p1 = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", "database/setup.ps1", "-CleanReset"
        ).redirectErrorStream(true).start();
        String out1 = new String(p1.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertNotEquals(0, p1.waitFor(), "setup.ps1 -CleanReset bắt buộc phải bị chặn với mã lỗi khác 0");
        assertTrue(out1.contains("CleanReset") && out1.contains("cinema_chain"),
                "Thông báo lỗi phải giải thích rõ việc vô hiệu hóa CleanReset để bảo vệ cinema_chain: " + out1);

        // 2. Thử chạy setup.ps1 -ResetTestDb -TargetDatabase cinema_chain -ConfirmYes -> bắt buộc trả về mã thoát khác 0
        Process p2 = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", "database/setup.ps1", "-ResetTestDb", "-TargetDatabase", "cinema_chain", "-ConfirmYes"
        ).redirectErrorStream(true).start();
        String out2 = new String(p2.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertNotEquals(0, p2.waitFor(), "setup.ps1 -ResetTestDb -TargetDatabase cinema_chain bắt buộc phải bị chặn với mã lỗi khác 0");
        assertTrue(out2.contains("cinema_chain_test"),
                "Thông báo lỗi phải khẳng định chỉ cho phép reset cinema_chain_test: " + out2);

        // 3. Tệp SQL xóa cinema_chain cũ không còn tồn tại
        assertFalse(Files.exists(Path.of("database", "schema", "00_clean_reset_cinema_chain.sql")),
                "Không được phép giữ lại tệp 00_clean_reset_cinema_chain.sql trong kho mã");

        // 4. Kịch bản chủ động chuẩn bị CSDL demo (prepare_demo_db.ps1) khi không có -ConfirmYes phải yêu cầu xác nhận và hủy an toàn nếu nhập khác
        Process p3 = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", "database/prepare_demo_db.ps1"
        ).redirectErrorStream(true).start();
        p3.getOutputStream().write("CANCEL\n".getBytes(StandardCharsets.UTF_8));
        p3.getOutputStream().close();
        String out3 = new String(p3.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p3.waitFor(), "prepare_demo_db.ps1 khi không nhập đúng mã xác nhận phải thoát an toàn (exit 0)");
        assertTrue(out3.contains("ĐÃ HỦY AN TOÀN"),
                "prepare_demo_db.ps1 phải báo hủy an toàn khi chưa xác nhận chủ động: " + out3);
    }

    @Test
    @DisplayName("Kiểm chứng tách biệt: verify_all.ps1 không được gọi setup.ps1 và phải xóa XML cũ + kiểm tra các lớp test bắt buộc")
    void verifyAllScriptMustBeReadOnlyTowardDemoDbAndEnforceFreshXmlAudit() throws Exception {
        String verifyScript = Files.readString(Path.of("database", "verify_all.ps1"), StandardCharsets.UTF_8);
        // Lọc bỏ các dòng chú thích (#) và chuỗi hiển thị Write-Output để kiểm tra đúng lệnh thực thi
        List<String> executableLines = verifyScript.lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#") && !l.startsWith("Write-Output"))
                .toList();
        for (String line : executableLines) {
            assertFalse(line.contains("setup.ps1") || line.contains("prepare_demo_db.ps1"),
                    "verify_all.ps1 tuyệt đối không được gọi setup.ps1 hay prepare_demo_db.ps1: " + line);
        }
        assertTrue(verifyScript.contains("db_fingerprint.py") && verifyScript.contains("cinema_chain"),
                "verify_all.ps1 phải đối chiếu dấu vân tay SHA-256 đầy đủ cho cinema_chain");
        assertTrue(verifyScript.contains("Remove-Item -Path \"verification\\test-results\\TEST-*.xml\""),
                "verify_all.ps1 phải xóa các tệp TEST-*.xml cũ trước khi chạy kiểm thử mới");
        assertTrue(verifyScript.contains("LastWriteTime -lt $runStart"),
                "verify_all.ps1 phải kiểm tra dấu thời gian của các tệp báo cáo XML mới");
    }

    @Test
    @DisplayName("Kiểm chứng hành vi tiến trình con: setup.ps1 và db_fingerprint.py tuyệt đối không truyền mật khẩu hay IDENTIFIED BY qua dòng lệnh hoặc MYSQL_PWD")
    void provisioningAndFingerprintScriptsMustNeverLeakSecretsInChildProcessArgumentsOrEnv() throws Exception {
        String sentinelAppSecret = "SENTINEL_APP_SECRET_DO_NOT_LEAK_88421";
        String sentinelRootSecret = "SENTINEL_ROOT_SECRET_DO_NOT_LEAK_77319";

        Path tempDir = Files.createTempDirectory("mysql_cli_spy_");
        Path spyLog = tempDir.resolve("captured_invocations.log");
        Path spyPy = tempDir.resolve("mysql_spy.py");
        Path spyCmd = tempDir.resolve("mysql_spy.cmd");

        String userProfile = System.getenv("USERPROFILE") != null ? System.getenv("USERPROFILE") : "";
        String expectedDataDir = (userProfile + "/.mysql/data-8.4").replace('\\', '/');

        // Tiến trình giả lập mysql.exe ghi lại nguyên vẹn tham số dòng lệnh thực tế (sys.argv) và biến môi trường MYSQL_PWD
        Files.writeString(spyPy, """
                import json, os, pathlib, sys
                log_path = pathlib.Path(sys.argv[1])
                cli_args = sys.argv[2:]
                mysql_pwd_env = os.environ.get("MYSQL_PWD", "")
                stdin_sql = sys.stdin.read()
                rec = {
                    "cli_args": cli_args,
                    "mysql_pwd_env": mysql_pwd_env,
                    "stdin_has_identified_by": ("IDENTIFIED BY" in stdin_sql)
                }
                with log_path.open("a", encoding="utf-8") as f:
                    f.write(json.dumps(rec) + "\\n")
                if "VERSION()" in stdin_sql:
                    print("%s")
                elif "SHOW DATABASES" in stdin_sql:
                    print("information_schema\\ncinema_chain")
                elif "information_schema.SCHEMATA" in stdin_sql:
                    print("1")
                elif "information_schema.TABLES" in stdin_sql and "COUNT(*)" in stdin_sql:
                    print("13")
                else:
                    print("0")
                """.formatted("8.4.0|MySQL Community Server - GPL|" + expectedDataDir), StandardCharsets.UTF_8);

        Files.writeString(spyCmd,
                "@echo off\r\npython \"" + spyPy.toAbsolutePath() + "\" \"" + spyLog.toAbsolutePath() + "\" %*\r\n",
                StandardCharsets.UTF_8);

        try {
            // 1. Chạy setup.ps1 ở chế độ Normal nhắm vào cinema_chain (đã có 13 bảng):
            //    Script sẽ thực thi server check + CREATE DATABASE IF NOT EXISTS + Provision-LeastPrivilegeAppUser (chứa IDENTIFIED BY)
            //    rồi dừng an toàn tại chốt chặn bảo vệ cinema_chain!
            ProcessBuilder pbSetup = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", "database/setup.ps1", "-Mode", "Normal", "-TargetDatabase", "cinema_chain"
            );
            pbSetup.environment().put("CINEMA_MYSQL_EXE_OVERRIDE", spyCmd.toAbsolutePath().toString());
            pbSetup.environment().put("CINEMA_DB_USER", "probe_user");
            pbSetup.environment().put("CINEMA_DB_PASSWORD", sentinelAppSecret);
            pbSetup.environment().put("MYSQL_ROOT_PASSWORD", sentinelRootSecret);
            pbSetup.redirectErrorStream(true);
            Process pSetup = pbSetup.start();
            String setupOut = new String(pSetup.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int setupExit = pSetup.waitFor();

            assertNotEquals(0, setupExit,
                    "setup.ps1 trên cinema_chain đã có bảng phải dừng lại ở chốt chặn bảo vệ không ghi đè: " + setupOut);
            assertFalse(setupOut.contains(sentinelAppSecret) || setupOut.contains(sentinelRootSecret),
                    "Đầu ra console của setup.ps1 tuyệt đối không được in lộ mật khẩu");

            assertTrue(Files.exists(spyLog), "Tiến trình con mysql_spy phải ghi nhận các lời gọi thực tế từ setup.ps1");
            List<String> records = Files.readAllLines(spyLog, StandardCharsets.UTF_8);
            assertTrue(records.size() >= 3, "Phải bắt được các lời gọi tiến trình mysql thực tế (bao gồm cả cấp quyền tài khoản)");

            boolean sawProvisioningViaStdin = false;
            for (String line : records) {
                assertFalse(line.contains(sentinelAppSecret),
                        "Tham số dòng lệnh hoặc MYSQL_PWD của tiến trình con bị lộ mật khẩu ứng dụng: " + line);
                assertFalse(line.contains(sentinelRootSecret),
                        "Tham số dòng lệnh hoặc MYSQL_PWD của tiến trình con bị lộ mật khẩu root: " + line);
                assertFalse(line.contains("IDENTIFIED BY") && !line.contains("\"stdin_has_identified_by\": true"),
                        "Câu lệnh IDENTIFIED BY không bao giờ được nằm trong cli_args");
                assertTrue(line.contains("--defaults-extra-file="),
                        "Mọi lời gọi mysql.exe phải truyền thông tin đăng nhập qua --defaults-extra-file: " + line);
                assertTrue(line.contains("\"mysql_pwd_env\": \"\""),
                        "Biến môi trường MYSQL_PWD của tiến trình con phải rỗng: " + line);
                if (line.contains("\"stdin_has_identified_by\": true")) {
                    sawProvisioningViaStdin = true;
                }
            }
            assertTrue(sawProvisioningViaStdin,
                    "Phép thử phải xác nhận nhánh Provision-LeastPrivilegeAppUser đã thực sự chạy và truyền IDENTIFIED BY qua stdin");

            // 2. Chạy tương tự với db_fingerprint.py để xác minh tiến trình con không lộ mật khẩu trên CLI
            Files.deleteIfExists(spyLog);
            ProcessBuilder pbFp = new ProcessBuilder(
                    "python", "tools/db_fingerprint.py",
                    "--db", "cinema_chain_test",
                    "--file", tempDir.resolve("dummy_fp.json").toAbsolutePath().toString(),
                    "--mode", "capture"
            );
            pbFp.environment().put("CINEMA_MYSQL_EXE_OVERRIDE", spyCmd.toAbsolutePath().toString());
            pbFp.environment().put("CINEMA_DB_PASSWORD", sentinelAppSecret);
            pbFp.redirectErrorStream(true);
            Process pFp = pbFp.start();
            pFp.getInputStream().readAllBytes();
            pFp.waitFor();

            assertTrue(Files.exists(spyLog), "db_fingerprint.py phải gọi mysql thông qua tiến trình con");
            for (String line : Files.readAllLines(spyLog, StandardCharsets.UTF_8)) {
                assertFalse(line.contains(sentinelAppSecret),
                        "db_fingerprint.py tuyệt đối không được để lộ mật khẩu trên tham số dòng lệnh hay MYSQL_PWD");
                assertTrue(line.contains("--defaults-extra-file="),
                        "db_fingerprint.py phải dùng --defaults-extra-file cho mọi truy vấn");
            }
        } finally {
            Files.deleteIfExists(spyLog);
            Files.deleteIfExists(spyPy);
            Files.deleteIfExists(spyCmd);
            Files.deleteIfExists(tempDir.resolve("dummy_fp.json"));
            Files.deleteIfExists(tempDir);
        }
    }
}
