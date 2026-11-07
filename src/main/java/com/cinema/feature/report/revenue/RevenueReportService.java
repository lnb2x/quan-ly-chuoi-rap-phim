package com.cinema.feature.report.revenue;

import com.cinema.domain.CinemaBranch;
import com.cinema.domain.RevenueReportData;
import com.cinema.feature.screening.ScreeningRepository;
import com.cinema.reporting.PdfReportWriter;
import com.cinema.shared.validation.BusinessRuleException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tầng dịch vụ nghiệp vụ cho Báo cáo Doanh thu Chuỗi rạp (Screen 4 - R7).
 * Người lập báo cáo: Kế toán chuỗi rạp.
 * Người nhận báo cáo: Giám đốc Kinh doanh chuỗi rạp.
 */
public final class RevenueReportService {

    public static final String CREATOR_TITLE = "Kế toán Chuỗi rạp";
    public static final String DEFAULT_CREATOR_NAME = "Trần Thị Thu Hà (Mã NV: NV-HN02)";
    public static final String RECIPIENT_TITLE = "Giám đốc Kinh doanh Chuỗi rạp";

    private final RevenueReportRepository reportRepository;
    private final ScreeningRepository screeningRepository;
    private final PdfReportWriter pdfReportWriter;

    public RevenueReportService(
            RevenueReportRepository reportRepository,
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

    public RevenueReportData generateReport(
            LocalDate fromDate,
            LocalDate toDate,
            Integer branchId,
            String branchFilterLabel
    ) {
        if (fromDate == null || toDate == null) {
            throw new BusinessRuleException("Vui lòng nhập đầy đủ từ ngày và đến ngày cho kỳ báo cáo doanh thu.");
        }
        if (toDate.isBefore(fromDate)) {
            throw new BusinessRuleException("Ngày kết thúc kỳ báo cáo không được nhỏ hơn ngày bắt đầu.");
        }

        List<RevenueReportData.BranchRevenueRow> branchRows =
                reportRepository.findBranchSummaries(fromDate, toDate, branchId);
        List<RevenueReportData.DailyRevenueRow> dailyRows =
                reportRepository.findDailyDetails(fromDate, toDate, branchId);

        int totalBookings = branchRows.stream().mapToInt(RevenueReportData.BranchRevenueRow::paidBookings).sum();
        int totalTickets = branchRows.stream().mapToInt(RevenueReportData.BranchRevenueRow::validTickets).sum();
        BigDecimal totalRevenue = branchRows.stream()
                .map(RevenueReportData.BranchRevenueRow::paidRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RevenueReportData(
                fromDate,
                toDate,
                branchFilterLabel != null ? branchFilterLabel : "Tất cả chi nhánh",
                CREATOR_TITLE,
                DEFAULT_CREATOR_NAME,
                RECIPIENT_TITLE,
                LocalDateTime.now(),
                totalBookings,
                totalTickets,
                totalRevenue,
                branchRows,
                dailyRows
        );
    }

    public void exportToPdf(RevenueReportData data, Path outputPath) {
        try {
            pdfReportWriter.exportRevenueReport(data, outputPath);
        } catch (IOException ex) {
            throw new BusinessRuleException("Không thể xuất tệp PDF Báo cáo Doanh thu: " + ex.getMessage(), ex);
        }
    }

    public BigDecimal queryIndependentVerifiedRevenue(LocalDate fromDate, LocalDate toDate, Integer branchId) {
        return reportRepository.queryIndependentVerifiedRevenue(fromDate, toDate, branchId);
    }
}
