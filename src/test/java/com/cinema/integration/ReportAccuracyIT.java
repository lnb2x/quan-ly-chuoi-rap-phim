package com.cinema.integration;

import com.cinema.domain.RevenueReportData;
import com.cinema.domain.TicketSalesReportData;
import com.cinema.feature.booking.BookingRepository;
import com.cinema.feature.booking.BookingService;
import com.cinema.feature.report.revenue.RevenueReportRepository;
import com.cinema.feature.report.revenue.RevenueReportService;
import com.cinema.feature.report.sales.TicketSalesReportRepository;
import com.cinema.feature.report.sales.TicketSalesReportService;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.feature.screening.ScreeningService;
import com.cinema.reporting.PdfReportWriter;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.db.TransactionTemplate;
import com.cinema.shared.ui.Theme;
import com.cinema.support.TestDatabaseGuard;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Độ chính xác Báo cáo Tài chính & Xuất PDF tiếng Việt (Gate E — Report Accuracy).
 * <p>
 * Đảm bảo nguyên tắc độc lập phép kiểm chứng (Independent Oracle — Section 4.D):
 * <ul>
 *   <li>Tuyệt đối không gọi lại phương thức hay View của chính Service/Repository đang cần kiểm tra làm đáp án.</li>
 *   <li>Đối chiếu đồng thời với: (1) kết quả biết trước của bộ dữ liệu mẫu chuẩn (35.165.000 VNĐ),
 *       (2) truy vấn SQL độc lập trên bảng gốc viết riêng trong bài test, và
 *       (3) bộ dữ liệu kiểm chứng có đáp án toán học tính tay trước (Known-Answer Fixture).</li>
 * </ul>
 */
class ReportAccuracyIT {

    private static ConnectionProvider connectionProvider;
    private static RevenueReportService revenueService;
    private static TicketSalesReportService salesService;
    private static ScreeningService screeningService;
    private static BookingService bookingService;

    @BeforeAll
    static void init() {
        connectionProvider = TestDatabaseGuard.verifyAndCreateTestProvider();
        ScreeningRepository screeningRepository = new ScreeningRepository(connectionProvider);
        TransactionTemplate tx = new TransactionTemplate(connectionProvider);
        PdfReportWriter pdfWriter = new PdfReportWriter();

        screeningService = new ScreeningService(screeningRepository, tx);
        bookingService = new BookingService(new BookingRepository(connectionProvider), tx);

        revenueService = new RevenueReportService(
                new RevenueReportRepository(connectionProvider),
                screeningRepository,
                pdfWriter
        );
        salesService = new TicketSalesReportService(
                new TicketSalesReportRepository(connectionProvider),
                screeningRepository,
                pdfWriter
        );
    }

    @Test
    @DisplayName("Gate E.1: Báo cáo Doanh thu khớp 100% với đáp án biết trước (35.165.000 VNĐ), SQL độc lập và PDF tiếng Việt [R7, BR-14, BR-15, BR-16]")
    void revenueReportTotalsAndPdfMustMatchIndependentSqlAndKnownSeedTotal() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 15);

        RevenueReportData report = revenueService.generateReport(from, to, null, "Tất cả chi nhánh");
        BigDecimal independentTotal = executeIndependentRevenueOracleSql(from, to, null);
        BigDecimal knownSeedExpected = new BigDecimal("35165000.00");

        assertEquals(0, knownSeedExpected.compareTo(report.totalPaidRevenue()),
                "Tổng doanh thu kỳ mẫu chuẩn 01/11-15/11/2026 phải khớp đáp án biết trước 35.165.000 VNĐ");
        assertEquals(0, independentTotal.compareTo(report.totalPaidRevenue()),
                "Tổng doanh thu trên Báo cáo Doanh thu phải khớp tuyệt đối với truy vấn SQL độc lập ngoài tầng ứng dụng");

        // Kiểm tra bộ lọc từng chi nhánh riêng lẻ so với SQL độc lập
        BigDecimal sumOfBranches = BigDecimal.ZERO;
        for (int branchId = 1; branchId <= 4; branchId++) {
            RevenueReportData branchReport = revenueService.generateReport(from, to, branchId, "Chi nhánh #" + branchId);
            BigDecimal branchOracleTotal = executeIndependentRevenueOracleSql(from, to, branchId);
            assertEquals(0, branchOracleTotal.compareTo(branchReport.totalPaidRevenue()),
                    "Doanh thu chi nhánh #" + branchId + " phải khớp tuyệt đối với SQL độc lập");
            sumOfBranches = sumOfBranches.add(branchReport.totalPaidRevenue());
        }
        assertEquals(0, report.totalPaidRevenue().compareTo(sumOfBranches),
                "Tổng doanh thu của 4 chi nhánh cộng lại phải khớp 100% tổng toàn chuỗi");

        // Xuất PDF và kiểm chứng nội dung văn bản tiếng Việt + số tiền tổng cộng
        Path pdfPath = Path.of("reports", "exports", "BaoCaoDoanhThu_Mau.pdf").toAbsolutePath();
        revenueService.exportToPdf(report, pdfPath);

        assertTrue(Files.exists(pdfPath), "Tệp PDF Báo cáo Doanh thu phải tồn tại");
        assertTrue(Files.size(pdfPath) > 2000, "Tệp PDF Báo cáo Doanh thu không được rỗng");

        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            assertTrue(doc.getNumberOfPages() >= 1);
            String extracted = new PDFTextStripper().getText(doc);
            assertTrue(extracted.contains("BÁO CÁO DOANH THU CHUỖI RẠP"), "PDF phải chứa tiêu đề tiếng Việt có dấu");
            assertTrue(extracted.contains("Kế toán Chuỗi rạp"), "PDF phải ghi rõ Người lập là Kế toán Chuỗi rạp");
            assertTrue(extracted.contains("Giám đốc Kinh doanh Chuỗi rạp"), "PDF phải ghi rõ Người nhận là Giám đốc Kinh doanh Chuỗi rạp");
            assertTrue(extracted.contains("CineViet Vincom Bà Triệu"), "PDF phải hiển thị đúng dấu tiếng Việt tên chi nhánh từ CSDL");
            assertTrue(extracted.contains("Hà Nội") && extracted.contains("Đà Nẵng"), "PDF phải hiển thị đúng dấu tiếng Việt tên thành phố từ CSDL");
            assertFalse(extracted.contains("??"), "PDF tuyệt đối không được chứa ký tự lỗi mã hóa '??'");
            assertTrue(extracted.contains(Theme.formatCurrency(report.totalPaidRevenue())),
                    "PDF phải chứa chính xác số tổng doanh thu: " + Theme.formatCurrency(report.totalPaidRevenue()));
        }
    }

    @Test
    @DisplayName("Gate E.2: Báo cáo Tình hình Bán vé khớp 100% với truy vấn SQL độc lập theo từng bộ lọc và xuất tệp PDF tiếng Việt [R7, BR-14, BR-16]")
    void ticketSalesReportTotalsAndPdfMustMatchIndependentSqlExactly() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 4);
        LocalDate to = LocalDate.of(2026, 11, 10);

        TicketSalesReportData report = salesService.generateReport(
                from, to, null, "Tất cả chi nhánh", null, "Tất cả suất chiếu");
        BigDecimal independentSalesTotal = executeIndependentSalesOracleSql(from, to, null, null);

        assertTrue(report.totalSalesRevenue().compareTo(BigDecimal.ZERO) > 0, "Doanh thu bán vé phải > 0");
        assertEquals(0, independentSalesTotal.compareTo(report.totalSalesRevenue()),
                "Tổng doanh thu trên Báo cáo Bán vé phải khớp tuyệt đối với SQL độc lập ngoài tầng ứng dụng");

        // Kiểm tra lọc theo Chi nhánh #1
        TicketSalesReportData branch1Report = salesService.generateReport(
                from, to, 1, "Chi nhánh Hà Nội", null, "Tất cả suất chiếu");
        BigDecimal branch1Oracle = executeIndependentSalesOracleSql(from, to, 1, null);
        assertEquals(0, branch1Oracle.compareTo(branch1Report.totalSalesRevenue()),
                "Doanh thu bán vé khi lọc Chi nhánh #1 phải khớp SQL độc lập");

        Path pdfPath = Path.of("reports", "exports", "BaoCaoBanVe_Mau.pdf").toAbsolutePath();
        salesService.exportToPdf(report, pdfPath);

        assertTrue(Files.exists(pdfPath), "Tệp PDF Báo cáo Bán vé phải tồn tại");
        assertTrue(Files.size(pdfPath) > 2000, "Tệp PDF Báo cáo Bán vé không được rỗng");

        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            assertTrue(doc.getNumberOfPages() >= 1);
            String extracted = new PDFTextStripper().getText(doc);
            assertTrue(extracted.contains("BÁO CÁO TÌNH HÌNH BÁN VÉ VÀ TỶ LỆ LẤP ĐẦY"),
                    "PDF phải chứa tiêu đề tiếng Việt có dấu");
            assertTrue(extracted.contains("Quản lý Chi nhánh"),
                    "PDF phải ghi rõ Người lập là Quản lý Chi nhánh");
            assertTrue(extracted.contains("Ban Điều hành và Quản lý Kinh doanh"),
                    "PDF phải ghi rõ Người nhận là Ban Điều hành và Quản lý Kinh doanh");
            assertTrue(extracted.contains("Đào, Phở và Piano"),
                    "PDF phải hiển thị đúng dấu tiếng Việt tựa đề phim từ CSDL");
            assertFalse(extracted.contains("??"),
                    "PDF tuyệt đối không được chứa ký tự lỗi mã hóa '??'");
            assertTrue(extracted.contains(Theme.formatCurrency(report.totalSalesRevenue())),
                    "PDF phải chứa chính xác tổng doanh thu bán vé: " + Theme.formatCurrency(report.totalSalesRevenue()));
        }
    }

    @Test
    @DisplayName("Gate E.3: Đối chiếu với bộ dữ liệu kiểm chứng có đáp án toán học tính tay trước (Known-Answer Fixture: Retry + Cancelled + Pending)")
    void reportsAndPdfsMustMatchHandComputedKnownAnswerFixture() throws Exception {
        LocalDate fixtureDate = LocalDate.of(2026, 12, 26);
        cleanUpFixtureDate(fixtureDate);

        Path tempRevPdf = Files.createTempFile("rev_known_", ".pdf");
        Path tempSalesPdf = Files.createTempFile("sales_known_", ".pdf");
        try {
            // Tạo 1 suất chiếu ở Phòng 1 (Chi nhánh 1, 80 ghế), giá cơ sở = 100.000 VNĐ
            int scrId = screeningService.createScreening(
                    1,
                    1,
                    LocalDateTime.of(2026, 12, 26, 19, 0),
                    LocalDateTime.of(2026, 12, 26, 21, 15),
                    new BigDecimal("100000.00")
            );

            // Đơn A: 1 ghế STD (#1: +0 = 100.000) + 1 ghế VIP (#45: +25.000 hoặc tra cứu thực tế surcharge VIP)
            BigDecimal vipSurcharge;
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT surcharge FROM seat_type WHERE seat_type_code = 'VIP'");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                vipSurcharge = rs.getBigDecimal(1);
            }
            BigDecimal expectedBookingARevenue = new BigDecimal("100000.00")
                    .add(new BigDecimal("100000.00").add(vipSurcharge));

            int bookingA = bookingService.createBooking(scrId, 1, 3, List.of(1, 45), "CARD", "SUCCESS");
            // Ghi nhận ngày thanh toán của Đơn A đúng vào ngày 2026-12-26 và chèn thêm 1 lần FAILED trước đó để kiểm tra chống nhân bản
            try (Connection conn = connectionProvider.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE payment SET paid_at = '2026-12-26 19:05:00' WHERE booking_id = ? AND payment_status = 'SUCCESS'")) {
                    ps.setInt(1, bookingA);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO payment (booking_id, amount, payment_method, payment_status, paid_at, transaction_ref)
                        VALUES (?, ?, 'CARD', 'FAILED', '2026-12-26 19:04:00', 'TXN-RETRY-FAIL-KNOWN')
                        """)) {
                    ps.setInt(1, bookingA);
                    ps.setBigDecimal(2, expectedBookingARevenue);
                    ps.executeUpdate();
                }
            }

            // Đơn B: Đặt ghế #2 rồi HỦY (CANCELLED + REFUNDED) -> đóng góp 0 VNĐ, giải phóng ghế #2
            int bookingB = bookingService.createBooking(scrId, 2, 3, List.of(2), "CASH", "SUCCESS");
            bookingService.cancelBooking(bookingB);

            // Đơn C: Đặt ghế #3 ở trạng thái PENDING -> đóng góp 0 VNĐ doanh thu thực thu, nhưng đang giữ 1 ghế (#3)
            bookingService.createBooking(scrId, 3, 3, List.of(3), "TRANSFER", "PENDING");

            // 1. Kiểm chứng Báo cáo Doanh thu cho ngày 2026-12-26 tại Chi nhánh 1
            RevenueReportData revData = revenueService.generateReport(fixtureDate, fixtureDate, 1, "Chi nhánh 1");
            assertEquals(1, revData.totalPaidBookings(), "Chỉ đúng 1 đơn CONFIRMED+SUCCESS được tính");
            assertEquals(2, revData.totalValidTickets(), "Chỉ đúng 2 vé thuộc đơn A được tính (không bị nhân đôi bởi FAILED payment)");
            assertEquals(0, expectedBookingARevenue.compareTo(revData.totalPaidRevenue()),
                    "Doanh thu thực thu phải bằng chính xác tổng tính tay của Đơn A");

            revenueService.exportToPdf(revData, tempRevPdf);
            try (PDDocument doc = Loader.loadPDF(tempRevPdf.toFile())) {
                String text = new PDFTextStripper().getText(doc);
                assertTrue(text.contains(Theme.formatCurrency(expectedBookingARevenue)),
                        "PDF Báo cáo Doanh thu phải in đúng con số tính tay: " + Theme.formatCurrency(expectedBookingARevenue));
            }

            // 2. Kiểm chứng Báo cáo Bán vé cho đúng suất chiếu scrId ngày 2026-12-26
            TicketSalesReportData salesData = salesService.generateReport(
                    fixtureDate, fixtureDate, 1, "Chi nhánh 1", scrId, "Suất #" + scrId);
            assertEquals(1, salesData.totalScreenings());
            assertEquals(80, salesData.totalCapacity());
            assertEquals(2, salesData.totalTicketsSold(), "Số vé đã bán thu tiền phải là 2");
            assertEquals(77, salesData.totalAvailableSeats(), "Số ghế trống phải là 80 - 2 (Đơn A) - 1 (Đơn C đang giữ) = 77");
            assertEquals(0, new BigDecimal("2.50").compareTo(salesData.averageOccupancyPct()), "Tỷ lệ lấp đầy vé đã bán 2/80 = 2.50%");
            assertEquals(0, expectedBookingARevenue.compareTo(salesData.totalSalesRevenue()));

            salesService.exportToPdf(salesData, tempSalesPdf);
            try (PDDocument doc = Loader.loadPDF(tempSalesPdf.toFile())) {
                String text = new PDFTextStripper().getText(doc);
                assertTrue(text.contains(Theme.formatCurrency(expectedBookingARevenue)),
                        "PDF Báo cáo Bán vé phải in đúng con số tính tay: " + Theme.formatCurrency(expectedBookingARevenue));
                assertTrue(text.contains("2.50%"), "PDF Báo cáo Bán vé phải in đúng tỷ lệ lấp đầy 2.50%");
            }
        } finally {
            Files.deleteIfExists(tempRevPdf);
            Files.deleteIfExists(tempSalesPdf);
            cleanUpFixtureDate(fixtureDate);
        }
    }

    /**
     * Truy vấn SQL đối chứng độc lập hoàn toàn (viết riêng trong Test, không qua View hay Repository của ứng dụng).
     */
    private BigDecimal executeIndependentRevenueOracleSql(LocalDate fromDate, LocalDate toDate, Integer branchId) throws Exception {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(sub.booking_ticket_sum), 0.00) AS oracle_rev
                FROM (
                    SELECT bk.booking_id, rm.branch_id, SUM(tk.unit_price) AS booking_ticket_sum
                    FROM booking bk
                    INNER JOIN screening sc      ON sc.screening_id = bk.screening_id
                    INNER JOIN screening_room rm ON rm.room_id = sc.room_id
                    INNER JOIN ticket tk         ON tk.booking_id = bk.booking_id AND tk.ticket_status = 'VALID'
                    INNER JOIN payment pm        ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
                    WHERE bk.booking_status = 'CONFIRMED'
                      AND pm.paid_at >= TIMESTAMP(?, '00:00:00')
                      AND pm.paid_at <= TIMESTAMP(?, '23:59:59')
                    GROUP BY bk.booking_id, rm.branch_id
                ) sub
                """);
        if (branchId != null) {
            sql.append(" WHERE sub.branch_id = ?");
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setDate(1, Date.valueOf(fromDate));
            ps.setDate(2, Date.valueOf(toDate));
            if (branchId != null) {
                ps.setInt(3, branchId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getBigDecimal("oracle_rev");
            }
        }
    }

    private BigDecimal executeIndependentSalesOracleSql(
            LocalDate fromDate, LocalDate toDate, Integer branchId, Integer screeningId) throws Exception {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(sub.booking_ticket_sum), 0.00) AS oracle_sales_rev
                FROM (
                    SELECT bk.booking_id, sc.screening_id, rm.branch_id, SUM(tk.unit_price) AS booking_ticket_sum
                    FROM booking bk
                    INNER JOIN screening sc      ON sc.screening_id = bk.screening_id
                    INNER JOIN screening_room rm ON rm.room_id = sc.room_id
                    INNER JOIN ticket tk         ON tk.booking_id = bk.booking_id AND tk.ticket_status = 'VALID'
                    INNER JOIN payment pm        ON pm.booking_id = bk.booking_id AND pm.payment_status = 'SUCCESS'
                    WHERE bk.booking_status = 'CONFIRMED'
                      AND sc.start_time >= TIMESTAMP(?, '00:00:00')
                      AND sc.start_time <= TIMESTAMP(?, '23:59:59')
                    GROUP BY bk.booking_id, sc.screening_id, rm.branch_id
                ) sub
                WHERE 1=1
                """);
        if (branchId != null) {
            sql.append(" AND sub.branch_id = ?");
        }
        if (screeningId != null) {
            sql.append(" AND sub.screening_id = ?");
        }
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setDate(idx++, Date.valueOf(fromDate));
            ps.setDate(idx++, Date.valueOf(toDate));
            if (branchId != null) {
                ps.setInt(idx++, branchId);
            }
            if (screeningId != null) {
                ps.setInt(idx, screeningId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getBigDecimal("oracle_sales_rev");
            }
        }
    }

    private void cleanUpFixtureDate(LocalDate fixtureDate) throws Exception {
        String d = fixtureDate.toString();
        try (Connection conn = connectionProvider.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE pm FROM payment pm
                    JOIN booking bk ON bk.booking_id = pm.booking_id
                    JOIN screening sc ON sc.screening_id = bk.screening_id
                    WHERE DATE(sc.start_time) = ?
                    """)) {
                ps.setString(1, d);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE tk FROM ticket tk
                    JOIN screening sc ON sc.screening_id = tk.screening_id
                    WHERE DATE(sc.start_time) = ?
                    """)) {
                ps.setString(1, d);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE bk FROM booking bk
                    JOIN screening sc ON sc.screening_id = bk.screening_id
                    WHERE DATE(sc.start_time) = ?
                    """)) {
                ps.setString(1, d);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM screening WHERE DATE(start_time) = ?")) {
                ps.setString(1, d);
                ps.executeUpdate();
            }
        }
    }
}
