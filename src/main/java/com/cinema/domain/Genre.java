package com.cinema.domain;

public record Genre(
        int genreId,
        String genreName,
        String description
) {
    @Override
    public String toString() {
        return genreName;
    }
}
