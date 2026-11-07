package com.cinema.domain;

import java.time.LocalDate;

public record Movie(
        int movieId,
        String movieCode,
        String title,
        int durationMinutes,
        LocalDate releaseDate,
        String ageRating,
        String country,
        String description
) {
    @Override
    public String toString() {
        return title + " (" + durationMinutes + " phút - " + ageRating + ")";
    }
}
