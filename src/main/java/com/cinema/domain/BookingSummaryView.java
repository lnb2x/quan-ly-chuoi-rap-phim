package com.cinema.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thông tin tổng hợp đơn đặt vé phục vụ màn hình Đặt vé, Hủy vé và Kiểm chứng.
 */
public record BookingSummaryView(
        int bookingId,
        String bookingCode,
        int screeningId,
        String movieTitle,
        String branchName,
        String roomName,
        LocalDateTime startTime,
        Integer customerId,
        String customerName,
        String employeeName,
        LocalDateTime bookedAt,
        String bookingStatus,
        String seatLabels,
        int ticketCount,
        BigDecimal totalAmount,
        String paymentMethod,
        String paymentStatus
) {
}
