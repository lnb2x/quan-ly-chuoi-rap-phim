package com.cinema.integration;

import com.cinema.domain.RevenueReportData;
import com.cinema.domain.TicketSalesReportData;
import com.cinema.feature.report.revenue.RevenueReportRepository;
import com.cinema.feature.report.revenue.RevenueReportService;
import com.cinema.feature.report.sales.TicketSalesReportRepository;
import com.cinema.feature.report.sales.TicketSalesReportService;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.reporting.PdfReportWriter;
import com.cinema.shared.config.AppConfig;
import com.cinema.shared.db.ConnectionProvider;
import com.cinema.shared.ui.Theme;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Độ chính xác Báo cáo Tài chính & Xuất PDF tiếng Việt (Gate E — Report Accuracy).
 * Đối chiếu tổng doanh thu hiển thị và xuất ra PDF với truy vấn SQL độc lập trực tiếp
 * trên bảng gốc (ticket + booking + EXISTS payment SUCCESS), đảm bảo:
 * - Không bao giờ nhân bản doanh thu khi đơn có nhiều lần thanh toán (FAILED + SUCCESS).
 * - Loại trừ hoàn toàn các đơn PENDING, FAILED và REFUNDED.
 * - Tệp PDF tồn tại, mở thành công, hiển thị chuẩn tiếng Việt có dấu và chứa đúng tổng tiền.
 */
class ReportAccuracyIT {

    private static RevenueReportService revenueService;
    private static TicketSalesReportService salesService;

    @BeforeAll
    static void init() {
        ConnectionProvider cp = new ConnectionProvider(AppConfig.loadTest());
        ScreeningRepository screeningRepository = new ScreeningRepository(cp);
        PdfReportWriter pdfWriter = new PdfReportWriter();

        revenueService = new RevenueReportService(
                new RevenueReportRepository(cp),
                screeningRepository,
                pdfWriter
        );
        salesService = new TicketSalesReportService(
                new TicketSalesReportRepository(cp),
                screeningRepository,
                pdfWriter
        );
    }

    @Test
    @DisplayName("Gate E.1: Báo cáo Doanh thu khớp 100% với truy vấn SQL độc lập và xuất tệp PDF tiếng Việt hợp lệ [R7, BR-14, BR-15, BR-16]")
    void revenueReportTotalsAndPdfMustMatchIndependentSqlExactly() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 15);

        RevenueReportData report = revenueService.generateReport(from, to, null, "Tất cả chi nhánh");
        BigDecimal independentTotal = revenueService.queryIndependentVerifiedRevenue(from, to, null);

        assertTrue(report.totalPaidRevenue().compareTo(BigDecimal.ZERO) > 0, "Tổng doanh thu phải > 0");
        assertEquals(0, independentTotal.compareTo(report.totalPaidRevenue()),
                "Tổng doanh thu trên Báo cáo Doanh thu phải khớp tuyệt đối với truy vấn SQL độc lập");

        // Kiểm tra từng chi nhánh riêng lẻ
        for (int branchId = 1; branchId <= 4; branchId++) {
            RevenueReportData branchReport = revenueService.generateReport(from, to, branchId, "Chi nhánh #" + branchId);
            BigDecimal branchSqlTotal = revenueService.queryIndependentVerifiedRevenue(from, to, branchId);
            assertEquals(0, branchSqlTotal.compareTo(branchReport.totalPaidRevenue()),
                    "Doanh thu chi nhánh #" + branchId + " phải khớp tuyệt đối với SQL độc lập");
        }

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
    @DisplayName("Gate E.2: Báo cáo Tình hình Bán vé khớp 100% với truy vấn SQL độc lập và xuất tệp PDF tiếng Việt hợp lệ [R7, BR-14, BR-16]")
    void ticketSalesReportTotalsAndPdfMustMatchIndependentSqlExactly() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 4);
        LocalDate to = LocalDate.of(2026, 11, 10);

        TicketSalesReportData report = salesService.generateReport(
                from, to, null, "Tất cả chi nhánh", null, "Tất cả suất chiếu");
        BigDecimal independentSalesTotal = salesService.queryIndependentVerifiedSalesRevenue(from, to, null, null);

        assertTrue(report.totalSalesRevenue().compareTo(BigDecimal.ZERO) > 0, "Doanh thu bán vé phải > 0");
        assertEquals(0, independentSalesTotal.compareTo(report.totalSalesRevenue()),
                "Tổng doanh thu trên Báo cáo Bán vé phải khớp tuyệt đối với truy vấn SQL độc lập");

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
}
