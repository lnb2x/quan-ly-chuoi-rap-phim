package com.cinema.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Customer(
        int customerId,
        String fullName,
        String phone,
        String email,
        LocalDate birthDate,
        LocalDateTime createdAt
) {
    @Override
    public String toString() {
        return fullName + " - " + phone;
    }
}
