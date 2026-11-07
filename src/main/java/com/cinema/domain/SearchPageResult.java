package com.cinema.domain;

/**
 * Kết quả tra cứu suất chiếu có phân trang kèm tổng số ghế trống và ghế đã bán
 * được tính chính xác trên toàn bộ tập kết quả khớp điều kiện lọc (Screen 3 - R6).
 */
public record SearchPageResult(
        PageResult<ScreeningDetailView> page,
        int totalAvailableSeats,
        int totalSoldSeats
) {
}
