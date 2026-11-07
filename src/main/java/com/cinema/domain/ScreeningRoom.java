package com.cinema.domain;

public record ScreeningRoom(
        int roomId,
        int branchId,
        String branchName,
        String roomName,
        String roomType,
        boolean active
) {
    @Override
    public String toString() {
        return roomName + " [" + roomType + "]";
    }
}
