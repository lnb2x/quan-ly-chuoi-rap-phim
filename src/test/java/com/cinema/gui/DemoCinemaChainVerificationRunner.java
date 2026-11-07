package com.cinema.gui;

import com.cinema.app.AppShell;
import com.cinema.app.Main;
import com.cinema.domain.BookingSummaryView;
import com.cinema.domain.RevenueReportData;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.SeatAvailabilityView;
import com.cinema.domain.TicketSalesReportData;
import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.ui.Theme;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Trình diễn tập & kiểm chứng trực tiếp điểm vào {@link Main#main(String[])} trên CSDL demo {@code cinema_chain}
 * (14 bảng chuẩn 3NF) theo đúng cấu hình demo thực tế ({@link AppConfig#loadProduction()}).
 * <p>
 * LƯU Ý TÁCH BIỆT RÕ RÀNG VỚI BỘ TEST TỰ ĐỘNG:
 * <ul>
 *   <li>Tên lớp kết thúc bằng {@code Runner} (không phải {@code Test}/{@code IT}), nên Maven Surefire/Failsafe
 *       không bao giờ tự động thực thi lớp này.</li>
 *   <li>Bắt buộc truyền cờ chủ động {@code --rehearse-demo-cinema-chain} kèm {@code --session1} hoặc {@code --session2}.</li>
 *   <li>Không bao giờ chạy bất kỳ lệnh {@code DROP}, {@code TRUNCATE} hay {@code DELETE} nào: toàn bộ dữ liệu demo
 *       vừa nhập qua giao diện trong Phiên 1 được giữ nguyên 100% sang Phiên 2 (JVM mới) để chứng minh khởi động
 *       ứng dụng bình thường bảo toàn dữ liệu.</li>
 * </ul>
 */
public final class DemoCinemaChainVerificationRunner {

    private static final String REQUIRED_FLAG = "--rehearse-demo-cinema-chain";
    private static final String DEMO_SCREENING_DATE = "2026-11-18";
    private static final LocalDate REV_FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate REV_TO = LocalDate.of(2026, 11, 18);
    private static final LocalDate SALES_FROM = LocalDate.of(2026, 11, 4);
    private static final LocalDate SALES_TO = LocalDate.of(2026, 11, 18);

    private static final Path SCREENSHOT_DIR = Path.of("verification", "screenshots");
    private static final Path DEMO_REV_PDF = Path.of("reports", "exports", "BaoCaoDoanhThu_CinemaChain_Demo.pdf");
    private static final Path DEMO_SALES_PDF = Path.of("reports", "exports", "BaoCaoBanVe_CinemaChain_Demo.pdf");

    private DemoCinemaChainVerificationRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !REQUIRED_FLAG.equals(args[0])) {
            throw new SecurityException(
                    "CHẶN AN TOÀN: DemoCinemaChainVerificationRunner chỉ được phép chạy khi truyền cờ tường minh "
                            + REQUIRED_FLAG + " (--session1|--session2 <stateFile>).");
        }
        String sessionMode = args[1].trim();
        Path stateFile = args.length >= 3
                ? Path.of(args[2])
                : Path.of("target", "demo-cinema-chain-session-state.properties");

        Files.createDirectories(SCREENSHOT_DIR);

        AppConfig prodConfig = AppConfig.loadProduction();
        ConnectionProvider cp = new ConnectionProvider(prodConfig);
        verifyLiveDemoConnection(cp);

        try {
            if ("--session1".equals(sessionMode)) {
                runSession1ViaMain(cp, stateFile);
            } else if ("--session2".equals(sessionMode)) {
                runSession2ViaMain(cp, stateFile);
            } else {
                throw new IllegalArgumentException("Chế độ phiên không hợp lệ: " + sessionMode);
            }
        } finally {
            disposeAllWindowsSafely();
        }
    }

    private static void verifyLiveDemoConnection(ConnectionProvider cp) throws Exception {
        try (Connection conn = cp.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DATABASE() AS db_name, VERSION() AS mysql_ver, @@version_comment AS dist, CURRENT_USER() AS db_user, "
                             + "(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE') AS tbl_cnt")) {
            if (!rs.next()) {
                throw new IllegalStateException("Không đọc được thông tin phiên kết nối MySQL.");
            }
            String dbName = rs.getString("db_name");
            String mysqlVer = rs.getString("mysql_ver");
            String dist = rs.getString("dist");
            String dbUser = rs.getString("db_user");
            int tblCnt = rs.getInt("tbl_cnt");
            if (!"cinema_chain".equals(dbName)) {
                throw new IllegalStateException("Kỳ vọng kết nối vào CSDL demo 'cinema_chain', nhưng thực tế là: " + dbName);
            }
            if (!mysqlVer.startsWith("8.4.") || dist.toLowerCase().contains("mariadb")) {
                throw new IllegalStateException("Sai phiên bản MySQL kỳ vọng (8.4.x): " + mysqlVer + " / " + dist);
            }
            if (tblCnt != 14) {
                throw new IllegalStateException("CSDL 'cinema_chain' phải có đúng 14 bảng chuẩn 3NF, thực tế: " + tblCnt);
            }
            System.out.printf("[DEMO-MAIN-DB-CHECK] Connected DB=%s | Tables=%d | MySQL=%s (%s) | User=%s%n",
                    dbName, tblCnt, mysqlVer, dist, dbUser);
        }
    }

    private static AppShell launchRealMainAndGrabAppShell() throws Exception {
        Main.main(new String[0]);
        final AppShell[] holder = new AppShell[1];
        for (int attempt = 0; attempt < 40 && holder[0] == null; attempt++) {
            SwingUtilities.invokeAndWait(() -> {
                for (Frame f : Frame.getFrames()) {
                    if (f instanceof AppShell shell && f.isDisplayable()) {
                        shell.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                        shell.setSize(1420, 880);
                        holder[0] = shell;
                        break;
                    }
                }
            });
            if (holder[0] == null) {
                Thread.sleep(100);
            }
        }
        if (holder[0] == null) {
            throw new IllegalStateException("Không tìm thấy cửa sổ AppShell sau khi khởi chạy com.cinema.app.Main.main()!");
        }
        Thread.sleep(400);
        return holder[0];
    }

    private static void runSession1ViaMain(ConnectionProvider cp, Path stateFile) throws Exception {
        System.out.println("=== [DEMO-CINEMA-CHAIN] SESSION 1 (JVM #1): LAUNCH com.cinema.app.Main ON 'cinema_chain' ===");
        AppShell shell = launchRealMainAndGrabAppShell();
        BookingService secondCashierService = new BookingService(
                new BookingRepository(cp), new TransactionTemplate(cp));

        try {
            // 0. Kiểm tra doanh thu mẫu gốc trên cinema_chain trước khi nhập liệu demo mới
            final BigDecimal[] baselineSeedRev = new BigDecimal[1];
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_REVENUE_REPORT);
                shell.getRevenueReportPanel().setDateRangeForVerification("2026-11-01", "2026-11-15");
                shell.getRevenueReportPanel().refreshPreview();
                baselineSeedRev[0] = shell.getRevenueReportPanel().getCurrentReportData().totalPaidRevenue();
            });
            System.out.println("[DEMO-SEED-BASELINE] RevenueReport(2026-11-01..2026-11-15) before demo input = "
                    + baselineSeedRev[0].toPlainString());

            // =========================================================================
            // 1. MÀN HÌNH 1 — FORM 1: QUẢN LÝ SUẤT CHIẾU (4 kịch bản BỊ TỪ CHỐI + 1 HỢP LỆ)
            // =========================================================================
            final int[] newDemoScreeningId = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_SCREENING);
                if (shell.getScreeningPanel().getTotalPages() < 2) {
                    throw new IllegalStateException("Màn hình 1 phải phân trang nhiều trang (50 dòng/trang)");
                }

                // 1a. Từ chối giá cơ sở <= 0 [BR-04]
                shell.getScreeningPanel().setFormValuesForVerification(
                        0, 0, 0, DEMO_SCREENING_DATE, "09:00", "11:15", "0");
                shell.getScreeningPanel().handleCreateScreening();
                requireBannerContains(shell.getScreeningPanel().getStatusBanner().currentText(),
                        "BR-04", "Form 1 trên cinema_chain (Từ chối giá vé <= 0)");

                // 1b. Từ chối thời lượng suất chiếu ngắn hơn thời lượng bộ phim [BR-05]
                shell.getScreeningPanel().setFormValuesForVerification(
                        0, 0, 0, DEMO_SCREENING_DATE, "09:00", "10:00", "95000");
                shell.getScreeningPanel().handleCreateScreening();
                requireBannerContains(shell.getScreeningPanel().getStatusBanner().currentText(),
                        "BR-05", "Form 1 trên cinema_chain (Từ chối giờ kết thúc ngắn hơn thời lượng phim)");

                // 1c. Từ chối trùng lịch chiếu trong cùng phòng [BR-06] với Suất #1 ngày 2026-11-04
                shell.getScreeningPanel().setFormValuesForVerification(
                        0, 0, 0, "2026-11-04", "13:30", "15:50", "95000");
                shell.getScreeningPanel().handleCreateScreening();
                requireBannerContains(shell.getScreeningPanel().getStatusBanner().currentText(),
                        "BR-06", "Form 1 trên cinema_chain (Từ chối chồng lấn lịch chiếu)");
            });
            Thread.sleep(250);
            captureWindow(shell, SCREENSHOT_DIR.resolve("demo_cinema_chain_01_screening_rejected.png"));

            SwingUtilities.invokeAndWait(() -> {
                // 1d. Từ chối đổi phòng cho Suất #1 đã có vé bán ra [BR-09]
                shell.getScreeningPanel().filterByDateForVerification("2026-11-04");
                if (!shell.getScreeningPanel().selectScreeningInTableById(1)) {
                    throw new IllegalStateException("Không tìm thấy Suất #1 ngày 2026-11-04 trên cinema_chain");
                }
                shell.getScreeningPanel().setRoomIndexForVerification(1);
                shell.getScreeningPanel().handleUpdateScreening();
                requireBannerContains(shell.getScreeningPanel().getStatusBanner().currentText(),
                        "BR-09", "Form 1 trên cinema_chain (Từ chối đổi phòng suất chiếu đã phát sinh vé)");

                // 1e. Hợp lệ: Tạo mới 1 suất chiếu demo ngày 2026-11-18 (09:00 - 11:15, giá cơ sở 95.000 VNĐ)
                shell.getScreeningPanel().filterByDateForVerification("");
                shell.getScreeningPanel().clearForm();
                shell.getScreeningPanel().setFormValuesForVerification(
                        0, 0, 0, DEMO_SCREENING_DATE, "09:00", "11:15", "95000");
                shell.getScreeningPanel().handleCreateScreening();
                requireBannerContains(shell.getScreeningPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Form 1 trên cinema_chain (Tạo suất chiếu demo hợp lệ)");

                shell.getScreeningPanel().filterByDateForVerification(DEMO_SCREENING_DATE);
                List<ScreeningDetailView> rows = shell.getScreeningPanel().getCurrentRows();
                if (rows.isEmpty()) {
                    throw new IllegalStateException("Không tìm thấy suất chiếu demo vừa tạo ngày " + DEMO_SCREENING_DATE);
                }
                newDemoScreeningId[0] = rows.getFirst().screeningId();
            });
            Thread.sleep(250);
            captureWindow(shell, SCREENSHOT_DIR.resolve("demo_cinema_chain_02_screening_created.png"));

            // =========================================================================
            // 2. MÀN HÌNH 2 — FORM 2: ĐẶT VÉ & CHỌN GHẾ (2 BỊ TỪ CHỐI + 2 HỢP LỆ KÈM HỦY VÉ)
            // =========================================================================
            final int[] persistedBookingId = new int[1];
            final String[] persistedBookingCode = new String[1];
            final int[] contestedSeatId = new int[1];

            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_BOOKING);
                if (!shell.getBookingPanel().selectScreeningById(newDemoScreeningId[0])) {
                    throw new IllegalStateException("Không chọn được suất chiếu demo #" + newDemoScreeningId[0]);
                }

                // 2a. Từ chối đặt vé khi chưa chọn ghế nào [BR-08]
                shell.getBookingPanel().getSeatMapPanel().clearSelection();
                shell.getBookingPanel().handleCreateBooking();
                requireBannerContains(shell.getBookingPanel().getStatusBanner().currentText(),
                        "BR-08", "Form 2 trên cinema_chain (Từ chối đặt vé khi chưa chọn ghế)");

                // 2b. Hợp lệ #1 (Giữ lại qua đóng/mở ứng dụng): Chọn 1 ghế STD + 1 ghế VIP, thanh toán CARD + SUCCESS
                if (!shell.getBookingPanel().selectAvailableSeatsByTypeCodes("STD", "VIP")) {
                    throw new IllegalStateException("Không chọn được 1 ghế STD + 1 ghế VIP trên suất #" + newDemoScreeningId[0]);
                }
                shell.getBookingPanel().setPaymentOptionsForVerification(false, "CARD", "SUCCESS");
                shell.getBookingPanel().handleCreateBooking();
                requireBannerContains(shell.getBookingPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Form 2 trên cinema_chain (Đặt vé 1 ghế STD + 1 ghế VIP thành công)");

                shell.getBookingPanel().selectScreeningById(newDemoScreeningId[0]);
                List<BookingSummaryView> recent = shell.getBookingPanel().getRecentBookings();
                if (recent.isEmpty()) {
                    throw new IllegalStateException("Không tìm thấy đơn đặt vé demo vừa tạo!");
                }
                persistedBookingId[0] = recent.getFirst().bookingId();
                persistedBookingCode[0] = recent.getFirst().bookingCode();
            });
            Thread.sleep(250);
            captureWindow(shell, SCREENSHOT_DIR.resolve("demo_cinema_chain_03_booking_confirmed.png"));

            // 2c. Từ chối #2 [BR-10]: Chọn 1 ghế trống trên UI, thu ngân #2 mua trước ghế đó, UI bấm đặt -> Bị từ chối!
            SwingUtilities.invokeAndWait(() -> {
                shell.getBookingPanel().selectFirstAvailableSeatsForDemo(1);
                List<SeatAvailabilityView> sel = shell.getBookingPanel().getSeatMapPanel().getSelectedSeats();
                contestedSeatId[0] = sel.getFirst().seatId();
            });
            int secondCashierBookingId = secondCashierService.createBooking(
                    newDemoScreeningId[0], null, 3, List.of(contestedSeatId[0]), "CASH", "SUCCESS");

            SwingUtilities.invokeAndWait(() -> {
                shell.getBookingPanel().handleCreateBooking();
                requireBannerContains(shell.getBookingPanel().getStatusBanner().currentText(),
                        "BR-10", "Form 2 trên cinema_chain (Từ chối ghế đã bị quầy thu ngân khác giữ khóa)");
            });
            Thread.sleep(250);
            captureWindow(shell, SCREENSHOT_DIR.resolve("demo_cinema_chain_04_booking_occupied_rejected.png"));

            // 2d. Hợp lệ #2: Hủy đơn của thu ngân #2 ngay trên bảng lịch sử Màn hình 2 -> Giải phóng ghế & hoàn tiền REFUNDED
            SwingUtilities.invokeAndWait(() -> {
                shell.getBookingPanel().selectScreeningById(newDemoScreeningId[0]);
                if (!shell.getBookingPanel().selectBookingInTableById(secondCashierBookingId)) {
                    throw new IllegalStateException("Không tìm thấy đơn #" + secondCashierBookingId + " trên bảng lịch sử Màn hình 2");
                }
                shell.getBookingPanel().handleCancelSelectedBooking();
                requireBannerContains(shell.getBookingPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Form 2 trên cinema_chain (Hủy đơn & giải phóng khóa ghế thành công)");
            });

            // =========================================================================
            // 3. MÀN HÌNH 3 — TRA CỨU SUẤT CHIẾU (1 BỊ TỪ CHỐI + PHÂN TRANG + TRA CỨU ĐÚNG SUẤT DEMO)
            // =========================================================================
            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_SEARCH);

                // 3a. Từ chối ngày sai định dạng
                shell.getSearchPanel().setDateTextForVerification("2026-99-99");
                shell.getSearchPanel().executeSearch();
                requireBannerContains(shell.getSearchPanel().getStatusBanner().currentText(),
                        "[LỖI]", "Màn hình 3 trên cinema_chain (Từ chối ngày sai định dạng)");

                // 3b. Phân trang toàn bộ suất đang mở bán
                shell.getSearchPanel().resetFilters();
                if (shell.getSearchPanel().getTotalMatchedCount() <= 50 || shell.getSearchPanel().getTotalPages() < 2) {
                    throw new IllegalStateException("Màn hình 3 trên cinema_chain phải có > 50 suất chiếu mở bán");
                }
                shell.getSearchPanel().nextPage();
                if (shell.getSearchPanel().getCurrentPage() != 2 || shell.getSearchPanel().getResultRowCount() <= 0) {
                    throw new IllegalStateException("Phân trang sang trang 2 trên Màn hình 3 thất bại");
                }

                // 3c. Tra cứu đúng ngày suất chiếu demo vừa tạo (2026-11-18): 80 ghế, 2 ghế đã bán, 78 ghế trống
                shell.getSearchPanel().setDateTextForVerification(DEMO_SCREENING_DATE);
                shell.getSearchPanel().executeSearch();
                requireBannerContains(shell.getSearchPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Màn hình 3 trên cinema_chain (Tra cứu ngày 2026-11-18 hợp lệ)");
                if (shell.getSearchPanel().getResultRowCount() != 1) {
                    throw new IllegalStateException("Kỳ vọng đúng 1 suất chiếu ngày " + DEMO_SCREENING_DATE);
                }
            });
            Thread.sleep(250);
            captureWindow(shell, SCREENSHOT_DIR.resolve("demo_cinema_chain_05_search_verified.png"));

            // =========================================================================
            // 4. MÀN HÌNH 4 & 5 — HAI BÁO CÁO QUẢN TRỊ: TỪ CHỐI SAI NGÀY + PREVIEW + XUẤT 2 PDF + ĐỐI CHIẾU SQL
            // =========================================================================
            final RevenueReportData[] revDataHolder = new RevenueReportData[1];
            final Path[] revDefaultPdf = new Path[1];
            final Path[] revDemoPdf = new Path[1];

            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_REVENUE_REPORT);

                // 4a. Từ chối Từ ngày > Đến ngày
                shell.getRevenueReportPanel().setDateRangeForVerification("2026-11-20", "2026-11-01");
                shell.getRevenueReportPanel().refreshPreview();
                requireBannerContains(shell.getRevenueReportPanel().getStatusBanner().currentText(),
                        "[LỖI]", "Màn hình 4 trên cinema_chain (Từ chối khoảng ngày ngược)");

                // 4b. Xem trước & Xuất PDF hợp lệ cho kỳ 2026-11-01 .. 2026-11-18
                shell.getRevenueReportPanel().setDateRangeForVerification(REV_FROM.toString(), REV_TO.toString());
                shell.getRevenueReportPanel().refreshPreview();
                requireBannerContains(shell.getRevenueReportPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Màn hình 4 trên cinema_chain (Xem trước Báo cáo Doanh thu)");
                revDataHolder[0] = shell.getRevenueReportPanel().getCurrentReportData();
                revDefaultPdf[0] = shell.getRevenueReportPanel().exportDefaultPdf();
                revDemoPdf[0] = shell.getRevenueReportPanel().exportPdfTo(DEMO_REV_PDF);
            });
            Thread.sleep(250);
            captureWindow(shell, SCREENSHOT_DIR.resolve("demo_cinema_chain_06_revenue_report.png"));

            final TicketSalesReportData[] salesDataHolder = new TicketSalesReportData[1];
            final Path[] salesDefaultPdf = new Path[1];
            final Path[] salesDemoPdf = new Path[1];

            SwingUtilities.invokeAndWait(() -> {
                shell.showScreen(AppShell.TAB_SALES_REPORT);

                // 5a. Từ chối ngày lọc sai định dạng
                shell.getSalesReportPanel().setDateRangeForVerification("ngay-sai", "2026-11-18");
                shell.getSalesReportPanel().refreshPreview();
                requireBannerContains(shell.getSalesReportPanel().getStatusBanner().currentText(),
                        "[LỖI]", "Màn hình 5 trên cinema_chain (Từ chối ngày sai định dạng)");

                // 5b. Xem trước & Xuất PDF hợp lệ cho kỳ 2026-11-04 .. 2026-11-18 (bao gồm cả suất demo #161)
                shell.getSalesReportPanel().setDateRangeForVerification(SALES_FROM.toString(), SALES_TO.toString());
                shell.getSalesReportPanel().refreshPreview();
                requireBannerContains(shell.getSalesReportPanel().getStatusBanner().currentText(),
                        "[THÀNH CÔNG]", "Màn hình 5 trên cinema_chain (Xem trước Báo cáo Bán vé)");
                salesDataHolder[0] = shell.getSalesReportPanel().getCurrentReportData();
                salesDefaultPdf[0] = shell.getSalesReportPanel().exportDefaultPdf();
                salesDemoPdf[0] = shell.getSalesReportPanel().exportPdfTo(DEMO_SALES_PDF);
            });
            Thread.sleep(250);
            captureWindow(shell, SCREENSHOT_DIR.resolve("demo_cinema_chain_07_sales_report.png"));

            // Đọc chi tiết bản ghi vừa tạo và đối chiếu SQL độc lập trên cinema_chain
            List<String> persistedTicketSpecs = new ArrayList<>();
            int persistedPaymentId;
            BigDecimal persistedAmount;
            try (Connection conn = cp.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT ticket_id, seat_id, unit_price, ticket_status FROM ticket WHERE booking_id = ? ORDER BY ticket_id ASC")) {
                    ps.setInt(1, persistedBookingId[0]);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            persistedTicketSpecs.add(String.format("ticket_id=%d(seat=%d,price=%s,status=%s)",
                                    rs.getInt("ticket_id"),
                                    rs.getInt("seat_id"),
                                    rs.getBigDecimal("unit_price").toPlainString(),
                                    rs.getString("ticket_status")));
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT payment_id, amount, payment_status FROM payment WHERE booking_id = ?")) {
                    ps.setInt(1, persistedBookingId[0]);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Không tìm thấy payment cho đơn demo #" + persistedBookingId[0]);
                        }
                        persistedPaymentId = rs.getInt("payment_id");
                        persistedAmount = rs.getBigDecimal("amount");
                    }
                }
            }

            BigDecimal oracleRev = queryIndependentRevenueOracle(cp, REV_FROM, REV_TO);
            BigDecimal oracleSales = queryIndependentSalesOracle(cp, SALES_FROM, SALES_TO);
            BigDecimal expectedRevAfterDemo = baselineSeedRev[0].add(persistedAmount);

            if (revDataHolder[0].totalPaidRevenue().compareTo(oracleRev) != 0) {
                throw new IllegalStateException("Báo cáo Doanh thu lệch với SQL độc lập trên cinema_chain: UI="
                        + revDataHolder[0].totalPaidRevenue() + " vs SQL=" + oracleRev);
            }
            if (revDataHolder[0].totalPaidRevenue().compareTo(expectedRevAfterDemo) != 0) {
                throw new IllegalStateException("Báo cáo Doanh thu không bằng (Doanh thu mẫu gốc + Đơn demo CONFIRMED): thực tế="
                        + revDataHolder[0].totalPaidRevenue() + " vs kỳ vọng=" + expectedRevAfterDemo);
            }
            if (salesDataHolder[0].totalSalesRevenue().compareTo(oracleSales) != 0) {
                throw new IllegalStateException("Báo cáo Bán vé lệch với SQL độc lập trên cinema_chain: UI="
                        + salesDataHolder[0].totalSalesRevenue() + " vs SQL=" + oracleSales);
            }

            // Kiểm tra nội dung văn bản tiếng Việt & số liệu trong 2 tệp PDF xuất từ cinema_chain
            verifyRevenuePdfContent(revDemoPdf[0], revDataHolder[0].totalPaidRevenue());
            verifySalesPdfContent(salesDemoPdf[0], salesDataHolder[0].totalSalesRevenue());
            renderPdfFirstPage(revDemoPdf[0], SCREENSHOT_DIR.resolve("demo_cinema_chain_08_revenue_pdf_page1.png"));
            renderPdfFirstPage(salesDemoPdf[0], SCREENSHOT_DIR.resolve("demo_cinema_chain_09_sales_pdf_page1.png"));

            System.out.printf("[DEMO-PDF-SQL-RECONCILED] RevenuePDF=%s (UI=%s == SQL=%s) | SalesPDF=%s (UI=%s == SQL=%s)%n",
                    revDemoPdf[0].getFileName(),
                    revDataHolder[0].totalPaidRevenue().toPlainString(),
                    oracleRev.toPlainString(),
                    salesDemoPdf[0].getFileName(),
                    salesDataHolder[0].totalSalesRevenue().toPlainString(),
                    oracleSales.toPlainString());

            // Lưu trạng thái trước khi đóng hoàn toàn ứng dụng (JVM #1)
            Properties props = new Properties();
            props.setProperty("demoScreeningId", Integer.toString(newDemoScreeningId[0]));
            props.setProperty("persistedBookingId", Integer.toString(persistedBookingId[0]));
            props.setProperty("persistedBookingCode", persistedBookingCode[0]);
            props.setProperty("cancelledBookingId", Integer.toString(secondCashierBookingId));
            props.setProperty("tickets", String.join(";", persistedTicketSpecs));
            props.setProperty("paymentId", Integer.toString(persistedPaymentId));
            props.setProperty("paymentAmount", persistedAmount.toPlainString());
            props.setProperty("revTotal", revDataHolder[0].totalPaidRevenue().toPlainString());
            props.setProperty("revBookings", Integer.toString(revDataHolder[0].totalPaidBookings()));
            props.setProperty("revTickets", Integer.toString(revDataHolder[0].totalValidTickets()));
            props.setProperty("salesTotal", salesDataHolder[0].totalSalesRevenue().toPlainString());
            props.setProperty("salesScreenings", Integer.toString(salesDataHolder[0].totalScreenings()));
            props.setProperty("salesTickets", Integer.toString(salesDataHolder[0].totalTicketsSold()));

            if (stateFile.getParent() != null) {
                Files.createDirectories(stateFile.getParent());
            }
            try (OutputStream out = Files.newOutputStream(stateFile)) {
                props.store(out, "Demo cinema_chain Before-Close Snapshot");
            }

            System.out.printf("[DEMO-BEFORE-CLOSE] screening_id=%d | confirmed_booking_id=%d (%s) | cancelled_booking_id=%d | tickets=%s | payment_id=%d (amount=%s)%n",
                    newDemoScreeningId[0], persistedBookingId[0], persistedBookingCode[0], secondCashierBookingId,
                    persistedTicketSpecs, persistedPaymentId, persistedAmount.toPlainString());
        } finally {
            SwingUtilities.invokeAndWait(shell::dispose);
            Thread.sleep(200);
            System.out.println("=== [DEMO-CINEMA-CHAIN] SESSION 1 (JVM #1) CLOSED COMPLETELY ===");
        }
    }

    private static void runSession2ViaMain(ConnectionProvider cp, Path stateFile) throws Exception {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            props.load(in);
        }
        int expectedScreeningId = Integer.parseInt(props.getProperty("demoScreeningId"));
        int expectedBookingId = Integer.parseInt(props.getProperty("persistedBookingId"));
        String expectedBookingCode = props.getProperty("persistedBookingCode");
        int expectedCancelledBookingId = Integer.parseInt(props.getProperty("cancelledBookingId"));
        List<String> expectedTickets = List.of(props.getProperty("tickets").split(";"));
        int expectedPaymentId = Integer.parseInt(props.getProperty("paymentId"));
        BigDecimal expectedPaymentAmount = new BigDecimal(props.getProperty("paymentAmount"));
        BigDecimal expectedRevTotal = new BigDecimal(props.getProperty("revTotal"));
        int expectedRevBookings = Integer.parseInt(props.getProperty("revBookings"));
        int expectedRevTickets = Integer.parseInt(props.getProperty("revTickets"));
        BigDecimal expectedSalesTotal = new BigDecimal(props.getProperty("salesTotal"));
        int expectedSalesScreenings = Integer.parseInt(props.getProperty("salesScreenings"));
        int expectedSalesTickets = Integer.parseInt(props.getProperty("salesTickets"));

        System.out.println("=== [DEMO-CINEMA-CHAIN] SESSION 2 (JVM #2): REOPEN com.cinema.app.Main NORMALLY ON 'cinema_chain' ===");
        AppShell shell2 = launchRealMainAndGrabAppShell();
        try {
            final RevenueReportData[] revAfter = new RevenueReportData[1];
            final TicketSalesReportData[] salesAfter = new TicketSalesReportData[1];

            SwingUtilities.invokeAndWait(() -> {
                // 1. Xác nhận Suất chiếu demo vẫn còn nguyên trên Màn hình 1
                shell2.showScreen(AppShell.TAB_SCREENING);
                shell2.getScreeningPanel().filterByDateForVerification(DEMO_SCREENING_DATE);
                List<ScreeningDetailView> scrRows = shell2.getScreeningPanel().getCurrentRows();
                if (scrRows.size() != 1 || scrRows.getFirst().screeningId() != expectedScreeningId) {
                    throw new IllegalStateException("Sau khi mở lại Main: Suất chiếu demo #" + expectedScreeningId + " không khớp!");
                }
                if (scrRows.getFirst().soldSeats() != 2 || scrRows.getFirst().availableSeats() != 78) {
                    throw new IllegalStateException("Sau khi mở lại Main: Số ghế đã bán/còn trống trên suất #"
                            + expectedScreeningId + " sai lệch (" + scrRows.getFirst().soldSeats() + " / " + scrRows.getFirst().availableSeats() + ")");
                }

                // 2. Xác nhận Đơn đặt vé demo vẫn còn nguyên trên Màn hình 2
                shell2.showScreen(AppShell.TAB_BOOKING);
                if (!shell2.getBookingPanel().selectScreeningById(expectedScreeningId)) {
                    throw new IllegalStateException("Sau khi mở lại Main: Không chọn được suất chiếu #" + expectedScreeningId);
                }
                List<BookingSummaryView> bkRows = shell2.getBookingPanel().getRecentBookings();
                boolean foundConfirmed = bkRows.stream().anyMatch(b ->
                        b.bookingId() == expectedBookingId
                                && expectedBookingCode.equals(b.bookingCode())
                                && "CONFIRMED".equals(b.bookingStatus())
                                && b.totalAmount().compareTo(expectedPaymentAmount) == 0);
                boolean foundCancelled = bkRows.stream().anyMatch(b ->
                        b.bookingId() == expectedCancelledBookingId
                                && "CANCELLED".equals(b.bookingStatus()));
                if (!foundConfirmed || !foundCancelled) {
                    throw new IllegalStateException("Sau khi mở lại Main: Lịch sử đơn đặt vé trên suất #" + expectedScreeningId + " không đầy đủ!");
                }
            });
            Thread.sleep(250);
            captureWindow(shell2, SCREENSHOT_DIR.resolve("demo_cinema_chain_10_reopened_persistence.png"));

            SwingUtilities.invokeAndWait(() -> {
                // 3. Xác nhận Màn hình 3 vẫn tra cứu thấy đúng suất chiếu demo
                shell2.showScreen(AppShell.TAB_SEARCH);
                shell2.getSearchPanel().setDateTextForVerification(DEMO_SCREENING_DATE);
                shell2.getSearchPanel().executeSearch();
                if (shell2.getSearchPanel().getResultRowCount() != 1) {
                    throw new IllegalStateException("Sau khi mở lại Main: Màn hình 3 không trả về suất chiếu demo ngày " + DEMO_SCREENING_DATE);
                }

                // 4. Xác nhận Báo cáo Doanh thu & Báo cáo Bán vé giữ nguyên 100% số liệu
                shell2.showScreen(AppShell.TAB_REVENUE_REPORT);
                shell2.getRevenueReportPanel().setDateRangeForVerification(REV_FROM.toString(), REV_TO.toString());
                shell2.getRevenueReportPanel().refreshPreview();
                revAfter[0] = shell2.getRevenueReportPanel().getCurrentReportData();

                shell2.showScreen(AppShell.TAB_SALES_REPORT);
                shell2.getSalesReportPanel().setDateRangeForVerification(SALES_FROM.toString(), SALES_TO.toString());
                shell2.getSalesReportPanel().refreshPreview();
                salesAfter[0] = shell2.getSalesReportPanel().getCurrentReportData();
            });

            List<String> ticketsAfter = new ArrayList<>();
            int paymentIdAfter;
            BigDecimal paymentAmountAfter;
            try (Connection conn = cp.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT ticket_id, seat_id, unit_price, ticket_status FROM ticket WHERE booking_id = ? ORDER BY ticket_id ASC")) {
                    ps.setInt(1, expectedBookingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ticketsAfter.add(String.format("ticket_id=%d(seat=%d,price=%s,status=%s)",
                                    rs.getInt("ticket_id"),
                                    rs.getInt("seat_id"),
                                    rs.getBigDecimal("unit_price").toPlainString(),
                                    rs.getString("ticket_status")));
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT payment_id, amount FROM payment WHERE booking_id = ?")) {
                    ps.setInt(1, expectedBookingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Sau khi mở lại Main: Giao dịch thanh toán của đơn demo bị mất!");
                        }
                        paymentIdAfter = rs.getInt("payment_id");
                        paymentAmountAfter = rs.getBigDecimal("amount");
                    }
                }
            }

            if (!expectedTickets.equals(ticketsAfter)
                    || expectedPaymentId != paymentIdAfter
                    || expectedPaymentAmount.compareTo(paymentAmountAfter) != 0
                    || expectedRevTotal.compareTo(revAfter[0].totalPaidRevenue()) != 0
                    || expectedRevBookings != revAfter[0].totalPaidBookings()
                    || expectedRevTickets != revAfter[0].totalValidTickets()
                    || expectedSalesTotal.compareTo(salesAfter[0].totalSalesRevenue()) != 0
                    || expectedSalesScreenings != salesAfter[0].totalScreenings()
                    || expectedSalesTickets != salesAfter[0].totalTicketsSold()) {
                throw new IllegalStateException("Phát hiện sai lệch dữ liệu demo trên cinema_chain sau khi đóng & mở lại Main!");
            }

            System.out.printf("[DEMO-AFTER-REOPEN] screening_id=%d | confirmed_booking_id=%d (%s) | tickets=%s | payment_id=%d (amount=%s)%n",
                    expectedScreeningId, expectedBookingId, expectedBookingCode,
                    ticketsAfter, paymentIdAfter, paymentAmountAfter.toPlainString());
            System.out.printf("[DEMO-REPORTS-AFTER-REOPEN] RevenueReport=%s (%d bookings, %d tickets) | SalesReport=%s (%d screenings, %d tickets)%n",
                    revAfter[0].totalPaidRevenue().toPlainString(), revAfter[0].totalPaidBookings(), revAfter[0].totalValidTickets(),
                    salesAfter[0].totalSalesRevenue().toPlainString(), salesAfter[0].totalScreenings(), salesAfter[0].totalTicketsSold());
            System.out.println("[DEMO-CINEMA-CHAIN-PERSISTENCE-VERIFIED] 100% MATCH ACROSS FULL 2-JVM RESTART OF com.cinema.app.Main ON 'cinema_chain' (DEMO DATA REMAINS INTACT).");
        } finally {
            SwingUtilities.invokeAndWait(shell2::dispose);
        }
    }

    private static BigDecimal queryIndependentRevenueOracle(ConnectionProvider cp, LocalDate fromDate, LocalDate toDate) throws Exception {
        String sql = """
                SELECT COALESCE(SUM(sub.booking_ticket_sum), 0.00) AS oracle_rev
                FROM (
                    SELECT bk.booking_id, SUM(tk.unit_price) AS booking_ticket_sum
                    FROM booking bk
                    INNER JOIN ticket tk  ON tk.booking_id = bk.booking_id AND tk.ticket_status = 'VALID'
                    INNER JOIN payment pm ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
                    WHERE bk.booking_status = 'CONFIRMED'
                      AND pm.paid_at >= TIMESTAMP(?, '00:00:00')
                      AND pm.paid_at <= TIMESTAMP(?, '23:59:59')
                    GROUP BY bk.booking_id
                ) sub
                """;
        try (Connection conn = cp.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fromDate));
            ps.setDate(2, Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal("oracle_rev");
            }
        }
    }

    private static BigDecimal queryIndependentSalesOracle(ConnectionProvider cp, LocalDate fromDate, LocalDate toDate) throws Exception {
        String sql = """
                SELECT COALESCE(SUM(sub.booking_ticket_sum), 0.00) AS oracle_sales_rev
                FROM (
                    SELECT bk.booking_id, SUM(tk.unit_price) AS booking_ticket_sum
                    FROM booking bk
                    INNER JOIN screening sc ON sc.screening_id = bk.screening_id
                    INNER JOIN ticket tk    ON tk.booking_id = bk.booking_id AND tk.ticket_status = 'VALID'
                    INNER JOIN payment pm   ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
                    WHERE bk.booking_status = 'CONFIRMED'
                      AND sc.start_time >= TIMESTAMP(?, '00:00:00')
                      AND sc.start_time <= TIMESTAMP(?, '23:59:59')
                    GROUP BY bk.booking_id
                ) sub
                """;
        try (Connection conn = cp.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fromDate));
            ps.setDate(2, Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal("oracle_sales_rev");
            }
        }
    }

    private static void verifyRevenuePdfContent(Path pdfPath, BigDecimal expectedTotal) throws Exception {
        if (!Files.exists(pdfPath) || Files.size(pdfPath) <= 2000) {
            throw new IllegalStateException("Tệp PDF Báo cáo Doanh thu không tồn tại hoặc rỗng: " + pdfPath);
        }
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            if (!text.contains("BÁO CÁO DOANH THU CHUỖI RẠP")
                    || !text.contains("Kế toán Chuỗi rạp")
                    || !text.contains("Giám đốc Kinh doanh Chuỗi rạp")
                    || text.contains("??")
                    || !text.contains(Theme.formatCurrency(expectedTotal))) {
                throw new IllegalStateException("Nội dung PDF Báo cáo Doanh thu không khớp chuẩn đối soát: " + pdfPath);
            }
        }
    }

    private static void verifySalesPdfContent(Path pdfPath, BigDecimal expectedTotal) throws Exception {
        if (!Files.exists(pdfPath) || Files.size(pdfPath) <= 2000) {
            throw new IllegalStateException("Tệp PDF Báo cáo Bán vé không tồn tại hoặc rỗng: " + pdfPath);
        }
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            if (!text.contains("BÁO CÁO TÌNH HÌNH BÁN VÉ VÀ TỶ LỆ LẤP ĐẦY")
                    || !text.contains("Quản lý Chi nhánh")
                    || !text.contains("Ban Điều hành và Quản lý Kinh doanh")
                    || text.contains("??")
                    || !text.contains(Theme.formatCurrency(expectedTotal))) {
                throw new IllegalStateException("Nội dung PDF Báo cáo Bán vé không khớp chuẩn đối soát: " + pdfPath);
            }
        }
    }

    private static void requireBannerContains(String actualBanner, String expectedToken, String context) {
        if (actualBanner == null || !actualBanner.contains(expectedToken)) {
            throw new IllegalStateException(context + " thất bại. Kỳ vọng '" + expectedToken + "', thực tế: " + actualBanner);
        }
        System.out.println("[DEMO-ASSERT-PASS] " + context + " -> " + actualBanner);
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
        System.out.println("Captured demo screenshot: " + outputFile.toAbsolutePath());
    }

    private static void renderPdfFirstPage(Path pdfFile, Path pngOutput) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfFile.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage pageImage = renderer.renderImageWithDPI(0, 150f);
            ImageIO.write(pageImage, "png", pngOutput.toFile());
            System.out.println("Rendered demo PDF preview: " + pngOutput.toAbsolutePath());
        }
    }

    private static void disposeAllWindowsSafely() {
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (Frame f : Frame.getFrames()) {
                    f.setVisible(false);
                    f.dispose();
                }
                for (Window w : Window.getWindows()) {
                    w.setVisible(false);
                    w.dispose();
                }
            });
        } catch (Exception ignored) {
        }
    }
}
