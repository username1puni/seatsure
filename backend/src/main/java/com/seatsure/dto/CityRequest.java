package com.seatsure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CityRequest(
        @NotBlank @Size(max = 60) String name, @NotBlank @Size(max = 60) String state) {
}
