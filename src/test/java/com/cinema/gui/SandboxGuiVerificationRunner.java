package com.cinema.gui;

import com.cinema.app.AppShell;
import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.payment.SandboxPaymentRepository;
import com.cinema.feature.payment.SandboxPaymentService;
import com.cinema.feature.payment.SepayGateway;
import com.cinema.feature.payment.SepayWebhookServer;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.screening.ScreeningService;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.config.SepayConfig;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.ui.Theme;
import com.cinema.support.TestDatabaseGuard;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/** Real Swing rendering on cinema_chain_test: verifies unconfigured guard AND VietQR popup + loopback SePay webhook. */
public final class SandboxGuiVerificationRunner {

    public static void main(String[] args) throws Exception {
        ConnectionProvider cp = TestDatabaseGuard.verifyAndCreateTestProvider();
        Theme.setupLookAndFeel();
        verifyUnconfiguredRejection(cp);
        verifyConfiguredVietQrAndWebhookFlow(cp);
    }

    private static void verifyUnconfiguredRejection(ConnectionProvider cp) throws Exception {
        var payments = new SandboxPaymentService(
                new SandboxPaymentRepository(cp),
                new BookingRepository(cp),
                new TransactionTemplate(cp),
                new SepayConfig("", "", "CINEVIET", SepayConfig.AuthMode.HMAC_SHA256, "", 0),
                Clock.systemUTC()
        );
        AppShell[] frame = new AppShell[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new AppShell(AppConfig.loadTest(), payments);
                frame[0].setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame[0].setTitle("CineViet - SePay Sandbox - Chưa cấu hình");
                frame[0].setVisible(true);
                frame[0].showScreen(AppShell.TAB_BOOKING);
                frame[0].getBookingPanel().selectFirstAvailableSeatsForDemo(1);
                JButton button = findComponent(frame[0], JButton.class, "btnSepaySandbox");
                if (button == null) {
                    throw new AssertionError("Thiếu nút Thanh toán SePay VietQR (Sandbox).");
                }
                button.doClick();
            });
            awaitCondition(10, () -> {
                boolean[] ok = {false};
                SwingUtilities.invokeAndWait(() ->
                        ok[0] = frame[0].getBookingPanel().getStatusBanner().currentText().contains("Chưa cấu hình"));
                return ok[0];
            }, "GUI phải báo thiếu cấu hình SePay Sandbox trước khi tạo đơn.");

            Path path = Path.of("verification/business-audit/sandbox-ui-unconfigured.png");
            captureWindowPng(frame[0].getContentPane(), path);
            System.out.println("[SANDBOX-GUI-PASS-1] Nút SePay hiển thị, thiếu cấu hình bị từ chối trước khi tạo đơn; ảnh=" + path);
        } finally {
            if (frame[0] != null) {
                SwingUtilities.invokeAndWait(frame[0]::dispose);
            }
        }
    }

    private static void verifyConfiguredVietQrAndWebhookFlow(ConnectionProvider cp) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(cp);
        LocalDateTime futureStart = LocalDateTime.of(2027, 4, 15, 19, 30);
        int screeningId = new ScreeningService(new ScreeningRepository(cp), tx)
                .createScreening(1, 1, futureStart, futureStart.plusHours(3), new BigDecimal("95000"));

        SepayConfig cfg = new SepayConfig(
                "MB", "0123456789", "CINEVIET SANDBOX",
                SepayConfig.AuthMode.HMAC_SHA256, "sepay-gui-sandbox-secret-2026", 0);
        SepayGateway signer = new SepayGateway(cfg);
        byte[] syntheticQrPng = createSyntheticQrPng();
        var payments = new SandboxPaymentService(
                new SandboxPaymentRepository(cp),
                new BookingRepository(cp),
                tx,
                cfg,
                Clock.systemUTC(),
                uri -> syntheticQrPng
        );

        AppShell[] frame = new AppShell[1];
        JDialog[] qrDialog = new JDialog[1];
        try (SepayWebhookServer server = new SepayWebhookServer(payments, 0)) {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new AppShell(AppConfig.loadTest(), payments);
                frame[0].setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame[0].setTitle("CineViet - SePay Sandbox (VietQR) - CSDL kiểm thử");
                frame[0].setVisible(true);
                frame[0].showScreen(AppShell.TAB_BOOKING);
                if (!frame[0].getBookingPanel().selectScreeningById(screeningId)) {
                    throw new AssertionError("Không chọn được suất chiếu kiểm thử #" + screeningId);
                }
                frame[0].getBookingPanel().selectFirstAvailableSeatsForDemo(1);
                JButton button = findComponent(frame[0], JButton.class, "btnSepaySandbox");
                button.doClick();
            });

            awaitCondition(10, () -> {
                SwingUtilities.invokeAndWait(() -> qrDialog[0] = findOpenSepayDialog());
                return qrDialog[0] != null && qrDialog[0].isShowing();
            }, "Hộp thoại VietQR SePay Sandbox phải mở sau khi tạo đơn.");

            awaitCondition(5, () -> {
                boolean[] loaded = {false};
                SwingUtilities.invokeAndWait(() -> {
                    JLabel lblImg = findComponent(qrDialog[0], JLabel.class, "lblSepayQrImage");
                    loaded[0] = lblImg != null && lblImg.getIcon() != null;
                });
                return loaded[0];
            }, "Ảnh VietQR phải được nạp vào hộp thoại.");

            String[] codeHolder = new String[1];
            SwingUtilities.invokeAndWait(() -> {
                JLabel lblCode = findComponent(qrDialog[0], JLabel.class, "lblSepayTransferCode");
                codeHolder[0] = lblCode != null ? lblCode.getText() : "";
            });
            if (codeHolder[0] == null || !codeHolder[0].startsWith("SP")) {
                throw new AssertionError("Mã chuyển khoản SePay không hợp lệ trên hộp thoại: " + codeHolder[0]);
            }

            Path popupShot = Path.of("verification/business-audit/sandbox-sepay-vietqr-popup.png");
            captureWindowPng(qrDialog[0].getContentPane(), popupShot);

            String payload = "{\"id\":990001,\"gateway\":\"MB\",\"transactionDate\":\"2026-11-22 22:00:00\","
                    + "\"accountNumber\":\"0123456789\",\"subAccount\":null,"
                    + "\"code\":\"" + codeHolder[0] + "\",\"content\":\"Chuyen khoan " + codeHolder[0] + "\","
                    + "\"transferType\":\"in\",\"transferAmount\":95000,\"accumulated\":1000000,"
                    + "\"referenceCode\":\"FT990001\",\"description\":\"GUI verification\"}";
            long ts = Instant.now().getEpochSecond();
            byte[] raw = payload.getBytes(StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + server.port() + SepayWebhookServer.WEBHOOK_PATH))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-SePay-Signature", signer.signHmac(ts, raw))
                    .header("X-SePay-Timestamp", Long.toString(ts))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(raw))
                    .build();
            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new AssertionError("Webhook SePay trả lỗi: " + resp.statusCode() + " " + resp.body());
            }

            SwingUtilities.invokeAndWait(() -> {
                JButton btnRefresh = findComponent(qrDialog[0], JButton.class, "btnRefreshSepayStatus");
                if (btnRefresh != null) {
                    btnRefresh.doClick();
                }
            });

            awaitCondition(10, () -> {
                boolean[] confirmed = {false};
                SwingUtilities.invokeAndWait(() -> {
                    JLabel lblStatus = findComponent(qrDialog[0], JLabel.class, "lblSepayStatus");
                    confirmed[0] = lblStatus != null && lblStatus.getText().contains("SEPAY-SUCCESS-990001");
                });
                return confirmed[0];
            }, "Hộp thoại VietQR phải tự cập nhật trạng thái xác nhận thành công sau Webhook.");

            Path confirmedShot = Path.of("verification/business-audit/sandbox-sepay-webhook-confirmed.png");
            captureWindowPng(frame[0].getContentPane(), confirmedShot);
            System.out.println("[SANDBOX-GUI-PASS-2] Hộp thoại VietQR + Webhook SePay xác nhận thành công; ảnh="
                    + popupShot + ", " + confirmedShot);
        } finally {
            if (qrDialog[0] != null) {
                SwingUtilities.invokeAndWait(qrDialog[0]::dispose);
            }
            if (frame[0] != null) {
                SwingUtilities.invokeAndWait(frame[0]::dispose);
            }
            cleanupTestScreening(tx, screeningId);
        }
    }

    private static void cleanupTestScreening(TransactionTemplate tx, int screeningId) {
        tx.execute(c -> {
            exec(c, "UPDATE ticket t JOIN booking b ON b.booking_id=t.booking_id SET t.ticket_status='CANCELLED' WHERE b.screening_id=?", screeningId);
            exec(c, "DELETE p FROM payment p JOIN booking b ON b.booking_id=p.booking_id WHERE b.screening_id=?", screeningId);
            exec(c, "DELETE t FROM ticket t JOIN booking b ON b.booking_id=t.booking_id WHERE b.screening_id=?", screeningId);
            exec(c, "DELETE FROM booking WHERE screening_id=?", screeningId);
            exec(c, "DELETE FROM screening WHERE screening_id=?", screeningId);
            return null;
        });
    }

    private static void exec(java.sql.Connection c, String sql, int id) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private static byte[] createSyntheticQrPng() throws IOException {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.setColor(Color.BLACK);
        for (int y = 20; y < 180; y += 16) {
            for (int x = 20; x < 180; x += 16) {
                if (((x + y) / 16) % 2 == 0) {
                    g.fillRect(x, y, 12, 12);
                }
            }
        }
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static JDialog findOpenSepayDialog() {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog d && "sepayQrDialog".equals(d.getName()) && d.isShowing()) {
                return d;
            }
        }
        return null;
    }

    private static void captureWindowPng(Container content, Path path) throws Exception {
        Files.createDirectories(path.getParent());
        SwingUtilities.invokeAndWait(() -> {
            int w = Math.max(content.getWidth(), 400);
            int h = Math.max(content.getHeight(), 300);
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            content.paint(graphics);
            graphics.dispose();
            try {
                ImageIO.write(image, "png", path.toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @FunctionalInterface
    private interface ConditionCheck {
        boolean check() throws Exception;
    }

    private static void awaitCondition(int seconds, ConditionCheck condition, String failureMessage) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (condition.check()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError(failureMessage);
    }

    private static <T extends Component> T findComponent(Container parent, Class<T> type, String name) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) {
                return type.cast(child);
            }
            if (child instanceof Container c) {
                T found = findComponent(c, type, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
