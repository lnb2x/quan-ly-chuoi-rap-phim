package com.cinema.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mô hình dữ liệu chuẩn cho Báo cáo Doanh thu (Screen 4 - R7).
 * Người lập báo cáo: Kế toán chuỗi rạp.
 * Người nhận báo cáo: Giám đốc Kinh doanh chuỗi rạp.
 */
public record RevenueReportData(
        LocalDate fromDate,
        LocalDate toDate,
        String branchFilterLabel,
        String creatorTitle,
        String creatorName,
        String recipientTitle,
        LocalDateTime generatedAt,
        int totalPaidBookings,
        int totalValidTickets,
        BigDecimal totalPaidRevenue,
        List<BranchRevenueRow> branchSummaries,
        List<DailyRevenueRow> dailyDetails
) {
    public record BranchRevenueRow(
            int branchId,
            String branchCode,
            String branchName,
            String city,
            int paidBookings,
            int validTickets,
            BigDecimal paidRevenue
    ) {
    }

    public record DailyRevenueRow(
            LocalDate revenueDate,
            String branchCode,
            String branchName,
            int paidBookings,
            int validTickets,
            BigDecimal paidRevenue
    ) {
    }
}
