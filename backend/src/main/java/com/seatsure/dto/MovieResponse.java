package com.seatsure.dto;

import java.time.LocalDate;

import com.seatsure.entity.Movie;
import com.seatsure.entity.MovieKind;

public record MovieResponse(
        Long id, String title, String description, MovieKind kind, String language, String genre,
        int durationMinutes, String certificate, String posterUrl, LocalDate releaseDate, boolean active) {

    public static MovieResponse from(Movie m) {
        return new MovieResponse(m.getId(), m.getTitle(), m.getDescription(), m.getKind(), m.getLanguage(), m.getGenre(),
                m.getDurationMinutes(), m.getCertificate(), m.getPosterUrl(), m.getReleaseDate(), m.isActive());
    }
}
