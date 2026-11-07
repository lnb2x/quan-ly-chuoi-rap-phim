package com.cinema.domain;

import java.time.LocalDate;

public record Employee(
        int employeeId,
        int branchId,
        String employeeCode,
        String fullName,
        String phone,
        String role,
        LocalDate hiredDate
) {
    @Override
    public String toString() {
        return fullName + " (" + employeeCode + " - " + role + ")";
    }
}
