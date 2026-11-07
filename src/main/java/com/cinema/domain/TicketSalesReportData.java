package com.cinema.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mô hình dữ liệu chuẩn cho Báo cáo Tình hình Bán vé & Lấp đầy Suất chiếu (Screen 5 - R7).
 * Người lập báo cáo: Quản lý Chi nhánh.
 * Người nhận báo cáo: Ban Điều hành và Quản lý Kinh doanh.
 */
public record TicketSalesReportData(
        LocalDate fromDate,
        LocalDate toDate,
        String branchFilterLabel,
        String screeningFilterLabel,
        String creatorTitle,
        String creatorName,
        String recipientTitle,
        LocalDateTime generatedAt,
        int totalScreenings,
        int totalCapacity,
        int totalTicketsSold,
        int totalAvailableSeats,
        BigDecimal averageOccupancyPct,
        BigDecimal totalSalesRevenue,
        List<ScreeningSalesRow> rows
) {
    public record ScreeningSalesRow(
            int screeningId,
            LocalDate screeningDate,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String branchName,
            String roomName,
            String roomType,
            String movieTitle,
            int roomCapacity,
            int ticketsSold,
            int availableSeats,
            BigDecimal occupancyRatePct,
            BigDecimal salesRevenue,
            String screeningStatus
    ) {
    }
}
