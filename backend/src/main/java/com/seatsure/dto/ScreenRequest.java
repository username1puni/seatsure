package com.seatsure.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScreenRequest(
        @NotNull Long theatreId,
        @NotBlank @Size(max = 50) String name,
        @Min(value = 1, message = "At least 1 row") @Max(value = 26, message = "At most 26 rows (A to Z)") int rows,
        @Min(value = 1, message = "At least 1 seat per row") @Max(value = 40, message = "At most 40 seats per row") int seatsPerRow,
        List<String> premiumRows) {
}
