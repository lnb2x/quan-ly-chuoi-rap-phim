package com.cinema.gui;

import com.cinema.app.AppShell;
import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.payment.*;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.config.VnpayConfig;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.ui.Theme;
import com.cinema.support.TestDatabaseGuard;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.*;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

/** Real Swing rendering; deliberately no merchant credentials or browser navigation. */
public final class SandboxGuiVerificationRunner {
    public static void main(String[] args) throws Exception {
        var cp = TestDatabaseGuard.verifyAndCreateTestProvider();
        var payments = new SandboxPaymentService(new SandboxPaymentRepository(cp), new BookingRepository(cp),
                new TransactionTemplate(cp), new VnpayConfig("", "", URI.create("http://127.0.0.1:8889/sandbox/return"), 0), Clock.systemUTC());
        Theme.setupLookAndFeel();
        AppShell[] frame = new AppShell[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new AppShell(AppConfig.loadTest(), payments);
                frame[0].setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame[0].setTitle("CineViet - VNPay Sandbox - Chưa cấu hình merchant");
                frame[0].setVisible(true);
                frame[0].showScreen(AppShell.TAB_BOOKING);
                frame[0].getBookingPanel().selectFirstAvailableSeatsForDemo(1);
                JButton button = findButton(frame[0], "btnVnpaySandbox");
                if (button == null) throw new AssertionError("Thiếu nút thanh toán sandbox.");
                button.doClick();
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean[] rejected = {false};
            while (!rejected[0] && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait(() -> rejected[0] = frame[0].getBookingPanel().getStatusBanner().currentText().contains("Chưa cấu hình"));
                Thread.sleep(50);
            }
            if (!rejected[0]) throw new AssertionError("GUI phải báo thiếu cấu hình merchant.");
            Path path = Path.of("verification/business-audit/sandbox-ui-unconfigured.png");
            Files.createDirectories(path.getParent());
            SwingUtilities.invokeAndWait(() -> {
                var content = frame[0].getContentPane();
                var image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                content.paint(graphics);
                graphics.dispose();
                try { ImageIO.write(image, "png", path.toFile()); }
                catch (java.io.IOException e) { throw new RuntimeException(e); }
            });
            System.out.println("[SANDBOX-GUI-PASS] Nút sandbox hiển thị, thiếu merchant bị từ chối trước khi tạo đơn; ảnh=" + path);
        } finally {
            if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    private static JButton findButton(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton b && name.equals(b.getName())) return b;
            if (child instanceof Container c) {
                JButton found = findButton(c, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
