package com.cinema.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thông tin tổng hợp của một suất chiếu kèm chi nhánh, phòng chiếu, bộ phim và số ghế trống.
 */
public record ScreeningDetailView(
        int screeningId,
        int movieId,
        String movieCode,
        String movieTitle,
        int durationMinutes,
        String ageRating,
        int roomId,
        String roomName,
        String roomType,
        int branchId,
        String branchCode,
        String branchName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal basePrice,
        String status,
        int totalSeats,
        int soldSeats,
        int availableSeats
) {
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public String toString() {
        return String.format("#%d | %s | %s (%s) | %s | Còn %d/%d ghế",
                screeningId,
                movieTitle,
                roomName,
                branchName,
                startTime != null ? startTime.format(DT_FMT) : "",
                availableSeats,
                totalSeats);
    }
}
