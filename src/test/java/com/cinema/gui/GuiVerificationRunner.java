package com.cinema.gui;

import com.cinema.app.AppShell;
import com.cinema.domain.SeatAvailabilityView;
import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.ui.Theme;
import com.cinema.support.TestDatabaseGuard;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Trình chạy Kiểm chứng Giao diện Thực tế & Chụp ảnh Màn hình Xác thực (Layer C / Gate D — Real GUI Behavior).
 * <p>
 * CAM KẾT BẢO TOÀN DỮ LIỆU DEMO:
 * Chốt chặn {@link TestDatabaseGuard} bắt buộc trình kiểm thử này CHỈ được chạy trên {@code cinema_chain_test},
 * tuyệt đối từ chối chạy nếu cấu hình trỏ sang {@code cinema_chain}.
 * <p>
 * Kiểm tra đầy đủ cả trường hợp HỢP LỆ và trường hợp BỊ TỪ CHỐI trực tiếp qua 5 màn hình Swing thật:
 * <ol>
 *   <li>Màn hình 1 (Quản lý Suất chiếu): Tạo hợp lệ + Từ chối trùng lịch [BR-06] + Từ chối đổi phòng khi đã có vé [BR-09].</li>
 *   <li>Màn hình 2 (Đặt vé): Từ chối khi chưa chọn ghế [BR-08] + Chọn ghế & Đặt vé thành công + Từ chối ghế vừa bị quầy khác mua [BR-10].</li>
 *   <li>Màn hình 3 (Tìm kiếm): Từ chối ngày sai định dạng + Tìm kiếm hợp lệ trả về danh sách suất chiếu & ghế trống.</li>
 *   <li>Màn hình 4 (Báo cáo Doanh thu): Từ chối khoảng ngày ngược (Từ ngày > Đến ngày) + Xem trước & Xuất PDF hợp lệ.</li>
 *   <li>Màn hình 5 (Báo cáo Bán vé): Từ chối ngày sai định dạng + Xem trước & Xuất PDF hợp lệ.</li>
 * </ol>
 */
public final class GuiVerificationRunner {

    private static final Path SCREENSHOT_DIR = Path.of("verification", "screenshots");

    private GuiVerificationRunner() {
    }

    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            if (arg.toLowerCase().contains("cinema_chain") && !arg.toLowerCase().contains("cinema_chain_test")) {
                throw new SecurityException(
                        "CHẶN AN TOÀN: GuiVerificationRunner có thực hiện thêm/xóa dữ liệu thử nghiệm nên CẤM chạy trên cinema_chain!");
            }
        }

        Files.createDirectories(SCREENSHOT_DIR);
        AppConfig testConfig = AppConfig.loadTest();
        TestDatabaseGuard.assertSafeTestConfig(testConfig);

        ConnectionProvider cp = new ConnectionProvider(testConfig);
        TestDatabaseGuard.verifyLiveTestConnection(cp);
        cleanUpGuiDemoArtifacts(cp);

        try (Connection conn = cp.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DATABASE() AS db_name, VERSION() AS mysql_ver, @@version_comment AS dist, CURRENT_USER() AS db_user")) {
            if (rs.next()) {
                System.out.printf("[GUI-VERIFY] Connected DB=%s | MySQL=%s (%s) | User=%s%n",
                        rs.getString("db_name"),
                        rs.getString("mysql_ver"),
                        rs.getString("dist"),
                        rs.getString("db_user"));
            }
        }

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
            Thread.sleep(450);

            // =========================================================================
            // MÀN HÌNH 1: QUẢN LÝ SUẤT CHIẾU (Hợp lệ + 2 kịch bản Bị từ chối)
            // =========================================================================
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_SCREENING);
                shell.getScreeningPanel().setFormValuesForVerification(
                        0, 0, 0, "2026-11-18", "09:00", "11:15", "95000");
                shell.getScreeningPanel().handleCreateScreening();
                requireBannerContains(shell.getScreeningPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Màn hình 1 (Tạo suất chiếu hợp lệ)");
            });
            Thread.sleep(300);
            captureWindow(shell, SCREENSHOT_DIR.resolve("01_screening_management_valid.png"));

            // Màn hình 1 - Từ chối #1: Tạo suất chiếu trùng lịch trong cùng phòng [BR-06]
            SwingUtilities.invokeAndWait(() -> {
                shell.getScreeningPanel().setFormValuesForVerification(
                        0, 0, 1, "2026-11-18", "10:00", "12:30", "95000");
                shell.getScreeningPanel().handleCreateScreening();
                requireBannerContains(shell.getScreeningPanel().getStatusBanner().currentText(),
                        "BR-06", "Màn hình 1 (Từ chối trùng lịch chiếu)");
            });
            Thread.sleep(300);
            captureWindow(shell, SCREENSHOT_DIR.resolve("02_screening_management_overlap_rejected.png"));

            // Màn hình 1 - Từ chối #2: Chọn suất chiếu đã có vé (#1) và đổi sang phòng khác -> Bị từ chối [BR-09]
            SwingUtilities.invokeAndWait(() -> {
                shell.getScreeningPanel().filterByDateForVerification("2026-11-04");
                if (!shell.getScreeningPanel().selectScreeningInTableById(1)) {
                    throw new IllegalStateException("Không tìm thấy suất chiếu #1 trên bảng Màn hình 1");
                }
                shell.getScreeningPanel().setRoomIndexForVerification(1); // Đổi từ Phòng 01 sang Phòng 02
                shell.getScreeningPanel().handleUpdateScreening();
                requireBannerContains(shell.getScreeningPanel().getStatusBanner().currentText(),
                        "BR-09", "Màn hình 1 (Từ chối đổi phòng cho suất chiếu đã có vé)");
                shell.getScreeningPanel().filterByDateForVerification("");
            });

            // =========================================================================
            // MÀN HÌNH 2: ĐẶT VÉ & CHỌN GHẾ (Từ chối chưa chọn ghế + Hợp lệ + Từ chối trùng ghế)
            // =========================================================================
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_BOOKING);
                shell.getBookingPanel().getSeatMapPanel().clearSelection();
                shell.getBookingPanel().handleCreateBooking();
                requireBannerContains(shell.getBookingPanel().getStatusBanner().currentText(),
                        "BR-08", "Màn hình 2 (Từ chối đặt vé khi chưa chọn ghế)");

                shell.getBookingPanel().selectFirstAvailableSeatsForDemo(2);
            });
            Thread.sleep(300);
            captureWindow(shell, SCREENSHOT_DIR.resolve("03_ticket_booking_seat_selection.png"));

            // Màn hình 2 - Hợp lệ: Xác nhận đặt vé thành công
            SwingUtilities.invokeAndWait(() -> {
                shell.getBookingPanel().handleCreateBooking();
                requireBannerContains(shell.getBookingPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Màn hình 2 (Đặt vé hợp lệ)");
            });
            Thread.sleep(300);
            captureWindow(shell, SCREENSHOT_DIR.resolve("04_ticket_booking_success.png"));

            // Màn hình 2 - Từ chối #2: Thử đặt ghế vừa bị quầy thu ngân khác mua mất -> Bị từ chối [BR-10]
            final int[] contestedSeatId = new int[1];
            final int[] activeScreeningId = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                shell.getBookingPanel().selectFirstAvailableSeatsForDemo(1);
                List<SeatAvailabilityView> selected = shell.getBookingPanel().getSeatMapPanel().getSelectedSeats();
                contestedSeatId[0] = selected.getFirst().seatId();
                activeScreeningId[0] = concurrentCashierService.listOpenScreenings(null, null).getFirst().screeningId();
            });
            concurrentCashierService.createBooking(
                    activeScreeningId[0], null, 3, List.of(contestedSeatId[0]), "CASH", "SUCCESS");
            SwingUtilities.invokeAndWait(() -> {
                shell.getBookingPanel().handleCreateBooking();
                requireBannerContains(shell.getBookingPanel().getStatusBanner().currentText(),
                        "BR-10", "Màn hình 2 (Từ chối ghế đã có người mua)");
            });
            Thread.sleep(300);
            captureWindow(shell, SCREENSHOT_DIR.resolve("04b_ticket_booking_occupied_rejected.png"));

            // =========================================================================
            // MÀN HÌNH 3: TRA CỨU SUẤT CHIẾU (Từ chối sai ngày + Tra cứu hợp lệ)
            // =========================================================================
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_SEARCH);
                shell.getSearchPanel().setDateTextForVerification("2026-99-99");
                shell.getSearchPanel().executeSearch();
                requireBannerContains(shell.getSearchPanel().getStatusBanner().currentText(),
                        "[LỖI]", "Màn hình 3 (Từ chối ngày chiếu sai định dạng)");

                shell.getSearchPanel().resetFilters();
                if (shell.getSearchPanel().getTotalMatchedCount() <= 50
                        || shell.getSearchPanel().getTotalPages() < 2) {
                    throw new IllegalStateException("Màn hình 3 khi xem toàn bộ phải có nhiều trang (> 50 suất chiếu)");
                }
                shell.getSearchPanel().nextPage();
                if (shell.getSearchPanel().getCurrentPage() != 2 || shell.getSearchPanel().getResultRowCount() <= 0) {
                    throw new IllegalStateException("Phân trang Màn hình 3 sang trang 2 không trả về dữ liệu hợp lệ");
                }

                shell.getSearchPanel().setDateTextForVerification("2026-11-04");
                shell.getSearchPanel().executeSearch();
                requireBannerContains(shell.getSearchPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Màn hình 3 (Tra cứu suất chiếu hợp lệ)");
                if (shell.getSearchPanel().getResultRowCount() <= 0) {
                    throw new IllegalStateException("Màn hình 3 phải hiển thị danh sách suất chiếu ngày 2026-11-04");
                }
            });
            Thread.sleep(300);
            captureWindow(shell, SCREENSHOT_DIR.resolve("05_screening_search.png"));

            // Dọn dữ liệu tạm của Màn hình 1..2 trên cinema_chain_test trước khi kiểm tra & chụp 2 báo cáo chuẩn
            cleanUpGuiDemoArtifacts(cp);

            // =========================================================================
            // MÀN HÌNH 4: BÁO CÁO DOANH THU (Từ chối Từ ngày > Đến ngày + Preview & PDF hợp lệ)
            // =========================================================================
            final Path[] revPdfHolder = new Path[1];
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_REVENUE_REPORT);
                shell.getRevenueReportPanel().setDateRangeForVerification("2026-11-20", "2026-11-01");
                shell.getRevenueReportPanel().refreshPreview();
                requireBannerContains(shell.getRevenueReportPanel().getStatusBanner().currentText(),
                        "[LỖI]", "Màn hình 4 (Từ chối khoảng thời gian Từ ngày > Đến ngày)");

                shell.getRevenueReportPanel().setDateRangeForVerification("2026-11-01", "2026-11-15");
                shell.getRevenueReportPanel().refreshPreview();
                requireBannerContains(shell.getRevenueReportPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Màn hình 4 (Xem trước Báo cáo Doanh thu hợp lệ)");
                revPdfHolder[0] = shell.getRevenueReportPanel().exportDefaultPdf();
            });
            Thread.sleep(300);
            captureWindow(shell, SCREENSHOT_DIR.resolve("06_revenue_report_preview.png"));

            // =========================================================================
            // MÀN HÌNH 5: BÁO CÁO BÁN VÉ (Từ chối ngày sai + Preview & PDF hợp lệ)
            // =========================================================================
            final Path[] salesPdfHolder = new Path[1];
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_SALES_REPORT);
                shell.getSalesReportPanel().setDateRangeForVerification("ngay-sai", "2026-11-10");
                shell.getSalesReportPanel().refreshPreview();
                requireBannerContains(shell.getSalesReportPanel().getStatusBanner().currentText(),
                        "[LỖI]", "Màn hình 5 (Từ chối ngày lọc sai định dạng)");

                shell.getSalesReportPanel().setDateRangeForVerification("2026-11-04", "2026-11-10");
                shell.getSalesReportPanel().refreshPreview();
                requireBannerContains(shell.getSalesReportPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Màn hình 5 (Xem trước Báo cáo Bán vé hợp lệ)");
                salesPdfHolder[0] = shell.getSalesReportPanel().exportDefaultPdf();
            });
            Thread.sleep(300);
            captureWindow(shell, SCREENSHOT_DIR.resolve("07_ticket_sales_report_preview.png"));

            if (!Files.exists(revPdfHolder[0]) || Files.size(revPdfHolder[0]) == 0) {
                throw new IllegalStateException("Báo cáo Doanh thu PDF không được tạo hoặc rỗng: " + revPdfHolder[0]);
            }
            if (!Files.exists(salesPdfHolder[0]) || Files.size(salesPdfHolder[0]) == 0) {
                throw new IllegalStateException("Báo cáo Bán vé PDF không được tạo hoặc rỗng: " + salesPdfHolder[0]);
            }

            renderPdfFirstPage(revPdfHolder[0], SCREENSHOT_DIR.resolve("08_exported_revenue_pdf_page1.png"));
            renderPdfFirstPage(salesPdfHolder[0], SCREENSHOT_DIR.resolve("09_exported_sales_pdf_page1.png"));

            try (Connection conn = cp.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT DATABASE() AS db_name, "
                                 + "(SELECT COUNT(*) FROM screening) AS scr_cnt, "
                                 + "(SELECT COUNT(*) FROM booking) AS bk_cnt, "
                                 + "(SELECT COALESCE(SUM(total_paid_revenue), 0) FROM vw_branch_daily_revenue) AS total_rev")) {
                if (rs.next()) {
                    System.out.printf("[GUI-VERIFY-POST] DB=%s | screenings=%d | bookings=%d | total_paid_revenue=%s%n",
                            rs.getString("db_name"),
                            rs.getInt("scr_cnt"),
                            rs.getInt("bk_cnt"),
                            rs.getBigDecimal("total_rev").toPlainString());
                }
            }

            System.out.println("GUI verification (all 5 screens: valid + rejected cases) completed successfully on cinema_chain_test.");
        } finally {
            try {
                cleanUpGuiDemoArtifacts(cp);
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    for (Window w : Window.getWindows()) {
                        w.dispose();
                    }
                });
            }
        }
    }

    private static void requireBannerContains(String actualBanner, String expectedToken, String context) {
        if (actualBanner == null || !actualBanner.contains(expectedToken)) {
            throw new IllegalStateException(context + " thất bại. Mong đợi '" + expectedToken + "' nhưng thực tế: " + actualBanner);
        }
        System.out.println("[GUI-ASSERT-PASS] " + context + " -> " + actualBanner);
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

    private static void cleanUpGuiDemoArtifacts(ConnectionProvider cp) throws Exception {
        TestDatabaseGuard.verifyLiveTestConnection(cp);
        try (Connection conn = cp.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET ticket_status = 'CANCELLED', cancelled_at = COALESCE(cancelled_at, CURRENT_TIMESTAMP) WHERE booking_id > 160 AND ticket_status = 'VALID'")) {
                ps.executeUpdate();
            }
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
