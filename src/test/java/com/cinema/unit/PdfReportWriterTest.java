package com.cinema.unit;

import com.cinema.domain.RevenueReportData;
import com.cinema.domain.TicketSalesReportData;
import com.cinema.feature.report.revenue.RevenueReportService;
import com.cinema.feature.report.sales.TicketSalesReportService;
import com.cinema.reporting.PdfReportWriter;
import com.cinema.shared.validation.BusinessRuleException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfReportWriterTest {
    @TempDir Path directory;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 15);
    private static final String BRANCH = "CineViet chi nhánh Nguyễn Trãi, Thành phố Hồ Chí Minh - Trung tâm thương mại Điện Biên Phủ";

    @Test
    void revenueWrapsMetadataAndPaginatesEveryBranchWithoutLosingVietnameseText() throws Exception {
        List<RevenueReportData.BranchRevenueRow> branches = new ArrayList<>();
        List<RevenueReportData.DailyRevenueRow> daily = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            branches.add(new RevenueReportData.BranchRevenueRow(i, "CN-" + i, BRANCH, "Thành phố Hồ Chí Minh", 1, 2, new BigDecimal("200000")));
            daily.add(new RevenueReportData.DailyRevenueRow(DATE, "CN-" + i, BRANCH, 1, 2, new BigDecimal("200000")));
        }
        Path output = directory.resolve("doanh-thu.pdf");
        new PdfReportWriter().exportRevenueReport(revenue(branches, daily), output);
        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            assertTrue(document.getNumberOfPages() > 2, "Cả bảng chi nhánh và bảng ngày phải được phân trang");
            String text = checkedText(document);
            assertTrue(text.contains("CN-79"), "Không mất chi nhánh cuối khi sang trang");
            assertTrue(text.replaceAll("\\s+", "").contains(BRANCH.replaceAll("\\s+", "")));
            assertTrue(text.contains("NGƯỜI NHẬN BÁO CÁO"));
            assertTrue(text.contains("Tiếp theo"));
        }
    }

    @Test
    void salesWrapsLongTitlesAndKeepsTotalsAndPageNumbersInsidePage() throws Exception {
        List<TicketSalesReportData.ScreeningSalesRow> rows = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            rows.add(new TicketSalesReportData.ScreeningSalesRow(i, DATE, DATE.atTime(19, 0), DATE.atTime(21, 0),
                    BRANCH, "Phòng chiếu IMAX Nguyễn Trãi", "IMAX", "Đào, Phở và Piano - Hành trình của những người trẻ giữa lòng Hà Nội",
                    80, 2, 78, new BigDecimal("2.50"), new BigDecimal("200000"), "OPEN"));
        }
        Path output = directory.resolve("ban-ve.pdf");
        new PdfReportWriter().exportTicketSalesReport(sales(rows), output);
        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            assertTrue(document.getNumberOfPages() > 2);
            String text = checkedText(document);
            assertTrue(text.contains("#79"));
            assertTrue(text.contains("2.50%"));
            assertTrue(text.replaceAll("\\s+", "").contains("Đào,PhởvàPiano-HànhtrìnhcủanhữngngườitrẻgiữalòngHàNội"));
            assertTrue(text.contains("Trang " + document.getNumberOfPages() + "/" + document.getNumberOfPages()));
        }
    }

    @Test
    void exportFailurePreservesExistingDestinationAndHasVietnameseServiceError() throws Exception {
        Path destination = directory.resolve("da-ton-tai.pdf");
        Files.createDirectory(destination);
        Path existing = destination.resolve("giu-nguyen.txt");
        Files.writeString(existing, "Dữ liệu cần bảo toàn");
        RevenueReportService revenue = new RevenueReportService(null, null, new PdfReportWriter());
        BusinessRuleException failure = assertThrows(BusinessRuleException.class,
                () -> revenue.exportToPdf(revenue(List.of(), List.of()), destination));
        assertTrue(failure.getMessage().contains("Không thể xuất tệp PDF Báo cáo Doanh thu"));
        TicketSalesReportService sales = new TicketSalesReportService(null, null, new PdfReportWriter());
        assertTrue(assertThrows(BusinessRuleException.class, () -> sales.exportToPdf(sales(List.of()), destination))
                .getMessage().contains("Không thể xuất tệp PDF Báo cáo Bán vé"));
        assertEquals("Dữ liệu cần bảo toàn", Files.readString(existing));
        try (var paths = Files.list(directory)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(".cinema-report-")), "Không để lại tệp PDF tạm sau lỗi");
        }
    }

    private static String checkedText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override protected void processTextPosition(TextPosition text) {
                float width = document.getPage(getCurrentPageNo() - 1).getMediaBox().getWidth();
                float height = document.getPage(getCurrentPageNo() - 1).getMediaBox().getHeight();
                assertTrue(text.getXDirAdj() >= 35 && text.getXDirAdj() + text.getWidthDirAdj() <= width - 35,
                        "Chữ tràn lề ngang: " + text.getUnicode());
                assertTrue(text.getYDirAdj() >= 30 && text.getYDirAdj() <= height - 14,
                        "Chữ nằm ngoài vùng in: " + text.getUnicode());
                super.processTextPosition(text);
            }
        };
        return stripper.getText(document);
    }

    private static RevenueReportData revenue(List<RevenueReportData.BranchRevenueRow> branches, List<RevenueReportData.DailyRevenueRow> daily) {
        return new RevenueReportData(DATE, DATE, BRANCH, "Kế toán Chuỗi rạp", "Trần Thị Thu Hà (Mã NV: NV-HN02)",
                "Giám đốc Kinh doanh Chuỗi rạp", LocalDateTime.of(2026, 11, 15, 10, 0), 80, 160,
                new BigDecimal("16000000"), branches, daily);
    }

    private static TicketSalesReportData sales(List<TicketSalesReportData.ScreeningSalesRow> rows) {
        return new TicketSalesReportData(DATE, DATE, BRANCH, "Tất cả suất chiếu", "Quản lý Chi nhánh", "Nguyễn Minh Hoàng",
                "Ban Điều hành và Quản lý Kinh doanh", DATE.atTime(10, 0), 80, 6400, 160, 6240,
                new BigDecimal("2.50"), new BigDecimal("16000000"), rows);
    }
}
