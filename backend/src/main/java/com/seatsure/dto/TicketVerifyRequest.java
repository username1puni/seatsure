package com.seatsure.dto;

import jakarta.validation.constraints.NotBlank;

public record TicketVerifyRequest(
        @NotBlank String code) {
}
