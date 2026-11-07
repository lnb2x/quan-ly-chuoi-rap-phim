package com.cinema.domain;

public record CinemaBranch(
        int branchId,
        String branchCode,
        String branchName,
        String address,
        String city,
        String phone,
        boolean active
) {
    @Override
    public String toString() {
        return branchName + " (" + branchCode + ")";
    }
}
