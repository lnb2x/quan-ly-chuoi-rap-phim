package com.cinema.gui;

import com.cinema.app.AppShell;
import com.cinema.domain.SeatAvailabilityView;
import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.ui.Theme;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * Trình chạy Kiểm chứng Giao diện Thực tế & Chụp ảnh Màn hình Xác thực (Gate D — GUI Behavior).
 * Khởi chạy cửa sổ AppShell thật, thực hiện tuần tự đầy đủ các luồng nghiệp vụ người dùng
 * trên CSDL kiểm thử và ghi lại ảnh chụp màn hình thật vào verification/screenshots/:
 *  1. Thêm suất chiếu hợp lệ (01_screening_management_valid.png)
 *  2. Từ chối suất chiếu trùng lịch phòng (02_screening_management_overlap_rejected.png)
 *  3. Chọn ghế trên sơ đồ phòng chiếu (03_ticket_booking_seat_selection.png)
 *  4. Đặt vé thành công (04_ticket_booking_success.png)
 *  5. Từ chối đặt ghế đã có người mua trước (04b_ticket_booking_occupied_rejected.png)
 *  6. Tra cứu suất chiếu & ghế trống (05_screening_search.png)
 *  7. Xem trước & xuất PDF Báo cáo Doanh thu (06_revenue_report_preview.png + 08_exported_revenue_pdf_page1.png)
 *  8. Xem trước & xuất PDF Báo cáo Bán vé (07_ticket_sales_report_preview.png + 09_exported_sales_pdf_page1.png)
 */
public final class GuiVerificationRunner {

    private static final Path SCREENSHOT_DIR = Path.of("verification", "screenshots");

    private GuiVerificationRunner() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(SCREENSHOT_DIR);
        AppConfig testConfig = AppConfig.loadTest();
        cleanUpGuiDemoArtifacts(testConfig);

        ConnectionProvider cp = new ConnectionProvider(testConfig);
        BookingService concurrentCashierService = new BookingService(
                new BookingRepository(cp), new TransactionTemplate(cp));

        Theme.setupLookAndFeel();

        final AppShell[] holder = new AppShell[1];
        SwingUtilities.invokeAndWait(() -> {
            AppShell shell = new AppShell(testConfig);
            shell.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            shell.setSize(1420, 880);
            shell.setLocationRelativeTo(null);
            shell.setVisible(true);
            holder[0] = shell;
        });

        AppShell shell = holder[0];
        try {
            Thread.sleep(500);

            // Workflow 1: Thêm một suất chiếu hợp lệ trên Màn hình 1
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_SCREENING);
                shell.getScreeningPanel().setFormValuesForVerification(
                        0, 0, 0, "2026-11-18", "09:00", "11:15", "95000");
                shell.getScreeningPanel().handleCreateScreening();
            });
            Thread.sleep(350);
            captureWindow(shell, SCREENSHOT_DIR.resolve("01_screening_management_valid.png"));

            // Workflow 2: Thử thêm một suất chiếu trùng lịch trong cùng phòng -> Bị từ chối [BR-06]
            SwingUtilities.invokeAndWait(() -> {
                shell.getScreeningPanel().setFormValuesForVerification(
                        0, 0, 1, "2026-11-18", "10:00", "12:30", "95000");
                shell.getScreeningPanel().handleCreateScreening();
            });
            Thread.sleep(350);
            captureWindow(shell, SCREENSHOT_DIR.resolve("02_screening_management_overlap_rejected.png"));

            // Workflow 3: Chuyển sang Màn hình 2 (Đặt vé), chọn 2 ghế trống trên sơ đồ ghế
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_BOOKING);
                shell.getBookingPanel().selectFirstAvailableSeatsForDemo(2);
            });
            Thread.sleep(350);
            captureWindow(shell, SCREENSHOT_DIR.resolve("03_ticket_booking_seat_selection.png"));

            // Workflow 4: Xác nhận đặt vé thành công
            SwingUtilities.invokeAndWait(() -> shell.getBookingPanel().handleCreateBooking());
            Thread.sleep(350);
            captureWindow(shell, SCREENSHOT_DIR.resolve("04_ticket_booking_success.png"));

            // Workflow 4b: Thử đặt một ghế vừa bị quầy thu ngân khác mua mất -> Bị từ chối [BR-10]
            final int[] contestedSeatId = new int[1];
            final int[] activeScreeningId = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                shell.getBookingPanel().selectFirstAvailableSeatsForDemo(1);
                List<SeatAvailabilityView> selected = shell.getBookingPanel().getSeatMapPanel().getSelectedSeats();
                contestedSeatId[0] = selected.getFirst().seatId();
                activeScreeningId[0] = concurrentCashierService.listOpenScreenings(null, null).getFirst().screeningId();
            });
            // Quầy thu ngân thứ 2 chốt đơn ghế đó trước 1 tích tắc
            concurrentCashierService.createBooking(
                    activeScreeningId[0], null, 3, List.of(contestedSeatId[0]), "CASH", "SUCCESS");
            // Người dùng trên giao diện nhấn Đặt vé cho ghế đã bị chiếm -> Hiển thị lỗi đỏ [BR-10]
            SwingUtilities.invokeAndWait(() -> shell.getBookingPanel().handleCreateBooking());
            Thread.sleep(350);
            captureWindow(shell, SCREENSHOT_DIR.resolve("04b_ticket_booking_occupied_rejected.png"));

            // Workflow 5: Chuyển sang Màn hình 3 (Tra cứu suất chiếu & ghế trống)
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_SEARCH);
                shell.getSearchPanel().executeSearch();
            });
            Thread.sleep(350);
            captureWindow(shell, SCREENSHOT_DIR.resolve("05_screening_search.png"));

            // Dọn dữ liệu tạm của Workflow 1..4b trước khi chụp và xuất 2 báo cáo tài chính chuẩn (khớp 35.165.000 VNĐ)
            cleanUpGuiDemoArtifacts(testConfig);

            // Workflow 6: Chuyển sang Màn hình 4 (Báo cáo Doanh thu), xem trước & xuất PDF
            final Path[] revPdfHolder = new Path[1];
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_REVENUE_REPORT);
                shell.getRevenueReportPanel().refreshPreview();
                revPdfHolder[0] = shell.getRevenueReportPanel().exportDefaultPdf();
            });
            Thread.sleep(350);
            captureWindow(shell, SCREENSHOT_DIR.resolve("06_revenue_report_preview.png"));

            // Workflow 7: Chuyển sang Màn hình 5 (Báo cáo Tình hình Bán vé), xem trước & xuất PDF
            final Path[] salesPdfHolder = new Path[1];
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_SALES_REPORT);
                shell.getSalesReportPanel().refreshPreview();
                salesPdfHolder[0] = shell.getSalesReportPanel().exportDefaultPdf();
            });
            Thread.sleep(350);
            captureWindow(shell, SCREENSHOT_DIR.resolve("07_ticket_sales_report_preview.png"));

            // Kết xuất trang 1 của 2 tệp PDF ra ảnh PNG để kiểm chứng trực quan
            renderPdfFirstPage(revPdfHolder[0], SCREENSHOT_DIR.resolve("08_exported_revenue_pdf_page1.png"));
            renderPdfFirstPage(salesPdfHolder[0], SCREENSHOT_DIR.resolve("09_exported_sales_pdf_page1.png"));

            System.out.println("GUI verification and screenshot capture completed successfully.");
        } finally {
            cleanUpGuiDemoArtifacts(testConfig);
            SwingUtilities.invokeAndWait(() -> {
                for (Window w : Window.getWindows()) {
                    w.dispose();
                }
            });
        }
    }

    private static void captureWindow(JFrame frame, Path outputFile) throws Exception {
        final BufferedImage[] imgHolder = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            frame.validate();
            Container content = frame.getContentPane();
            int w = Math.max(content.getWidth(), 1400);
            int h = Math.max(content.getHeight(), 840);
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            content.paint(g2d);
            g2d.dispose();
            imgHolder[0] = image;
        });
        ImageIO.write(imgHolder[0], "png", outputFile.toFile());
        System.out.println("Captured screenshot: " + outputFile.toAbsolutePath());
    }

    private static void renderPdfFirstPage(Path pdfFile, Path pngOutput) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfFile.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage pageImage = renderer.renderImageWithDPI(0, 150f);
            ImageIO.write(pageImage, "png", pngOutput.toFile());
            System.out.println("Rendered PDF preview image: " + pngOutput.toAbsolutePath());
        }
    }

    private static void cleanUpGuiDemoArtifacts(AppConfig config) throws Exception {
        ConnectionProvider cp = new ConnectionProvider(config);
        try (Connection conn = cp.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM payment WHERE booking_id > 160")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ticket WHERE booking_id > 160")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM booking WHERE booking_id > 160")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM screening WHERE screening_id > 160 OR DATE(start_time) = '2026-11-18'")) {
                ps.executeUpdate();
            }
        }
    }
}
