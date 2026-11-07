package com.cinema.feature.report.sales;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.ScreeningDetailView;
import com.cinema.domain.TicketSalesReportData;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.reporting.PdfReportWriter;
import com.cinema.shared.validation.BusinessRuleException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tầng dịch vụ nghiệp vụ cho Báo cáo Tình hình Bán vé & Lấp đầy Suất chiếu (Screen 5 - R7).
 * Người lập báo cáo: Quản lý Chi nhánh.
 * Người nhận báo cáo: Ban Điều hành và Quản lý Kinh doanh.
 */
public final class TicketSalesReportService {

    public static final String CREATOR_TITLE = "Quản lý Chi nhánh";
    public static final String DEFAULT_CREATOR_NAME = "Nguyễn Minh Hoàng (Mã NV: NV-HN01)";
    public static final String RECIPIENT_TITLE = "Ban Điều hành và Quản lý Kinh doanh";

    private final TicketSalesReportRepository reportRepository;
    private final ScreeningRepository screeningRepository;
    private final PdfReportWriter pdfReportWriter;

    public TicketSalesReportService(
            TicketSalesReportRepository reportRepository,
            ScreeningRepository screeningRepository,
            PdfReportWriter pdfReportWriter
    ) {
        this.reportRepository = reportRepository;
        this.screeningRepository = screeningRepository;
        this.pdfReportWriter = pdfReportWriter;
    }

    public List<CinemaBranch> listBranches() {
        return screeningRepository.findAllBranches();
    }

    public List<ScreeningDetailView> listScreeningsForBranch(Integer branchId) {
        return screeningRepository.findScreenings(branchId, null, null, null);
    }

    public TicketSalesReportData generateReport(
            LocalDate fromDate,
            LocalDate toDate,
            Integer branchId,
            String branchFilterLabel,
            Integer screeningId,
            String screeningFilterLabel
    ) {
        if (fromDate == null || toDate == null) {
            throw new BusinessRuleException("Vui lòng nhập đầy đủ từ ngày và đến ngày cho báo cáo bán vé.");
        }
        if (toDate.isBefore(fromDate)) {
            throw new BusinessRuleException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu.");
        }

        List<TicketSalesReportData.ScreeningSalesRow> rows =
                reportRepository.findScreeningSales(fromDate, toDate, branchId, screeningId);

        int totalScreenings = rows.size();
        int totalCapacity = rows.stream().mapToInt(TicketSalesReportData.ScreeningSalesRow::roomCapacity).sum();
        int totalSold = rows.stream().mapToInt(TicketSalesReportData.ScreeningSalesRow::ticketsSold).sum();
        int totalAvailable = rows.stream().mapToInt(TicketSalesReportData.ScreeningSalesRow::availableSeats).sum();
        BigDecimal totalRevenue = rows.stream()
                .map(TicketSalesReportData.ScreeningSalesRow::salesRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOccupancy = totalCapacity > 0
                ? BigDecimal.valueOf(totalSold * 100.0 / totalCapacity).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new TicketSalesReportData(
                fromDate,
                toDate,
                branchFilterLabel != null ? branchFilterLabel : "Tất cả chi nhánh",
                screeningFilterLabel != null ? screeningFilterLabel : "Tất cả suất chiếu",
                CREATOR_TITLE,
                DEFAULT_CREATOR_NAME,
                RECIPIENT_TITLE,
                LocalDateTime.now(),
                totalScreenings,
                totalCapacity,
                totalSold,
                totalAvailable,
                avgOccupancy,
                totalRevenue,
                rows
        );
    }

    public void exportToPdf(TicketSalesReportData data, Path outputPath) {
        try {
            pdfReportWriter.exportTicketSalesReport(data, outputPath);
        } catch (IOException ex) {
            throw new BusinessRuleException("Không thể xuất tệp PDF Báo cáo Bán vé: " + ex.getMessage(), ex);
        }
    }

    public BigDecimal queryIndependentVerifiedSalesRevenue(
            LocalDate fromDate,
            LocalDate toDate,
            Integer branchId,
            Integer screeningId
    ) {
        return reportRepository.queryIndependentVerifiedSalesRevenue(fromDate, toDate, branchId, screeningId);
    }
}
