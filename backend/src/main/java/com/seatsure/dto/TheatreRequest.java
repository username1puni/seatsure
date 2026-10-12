package com.seatsure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TheatreRequest(
        @NotNull Long cityId, @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 255) String address) {
}
