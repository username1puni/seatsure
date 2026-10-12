package com.seatsure.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HoldRequest(
        @NotNull Long showId, @NotEmpty(message = "Pick at least one seat") @Size(max = 10, message = "You can book at most 10 seats at a time") List<@NotNull Long> seatIds) {
}
