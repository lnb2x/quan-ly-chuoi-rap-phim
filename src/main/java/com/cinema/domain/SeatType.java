package com.cinema.domain;

import java.math.BigDecimal;

public record SeatType(
        String seatTypeCode,
        String typeName,
        BigDecimal surcharge
) {
    @Override
    public String toString() {
        return typeName + " (" + seatTypeCode + ")";
    }
}
