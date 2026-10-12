package com.seatsure.dto;

import java.time.LocalDate;

import com.seatsure.entity.MovieKind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MovieRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 2000) String description,
        @NotNull MovieKind kind,
        @NotBlank @Size(max = 30) String language,
        @NotBlank @Size(max = 60) String genre,
        @Min(15) @Max(400) int durationMinutes,
        @NotBlank @Pattern(regexp = "U|UA|A|S|UA7\\+|UA13\\+|UA16\\+", message = "Certificate must be U, UA, A or S") String certificate,
        @Size(max = 500) String posterUrl,
        LocalDate releaseDate,
        Boolean active) {
}
