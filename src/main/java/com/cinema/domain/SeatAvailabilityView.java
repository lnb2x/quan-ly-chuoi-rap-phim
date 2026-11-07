package com.cinema.domain;

import java.math.BigDecimal;

/**
 * Thông tin ghế và trạng thái trống/đã đặt trong một suất chiếu cụ thể.
 */
public record SeatAvailabilityView(
        int seatId,
        int roomId,
        String seatRow,
        int seatNumber,
        String seatTypeCode,
        String seatTypeName,
        BigDecimal surcharge,
        BigDecimal finalUnitPrice,
        boolean occupied
) {
    public String seatLabel() {
        return seatRow + String.format("%02d", seatNumber);
    }
}
