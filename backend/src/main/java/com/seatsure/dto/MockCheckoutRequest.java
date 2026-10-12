package com.seatsure.dto;

import jakarta.validation.constraints.NotBlank;

public record MockCheckoutRequest(
        @NotBlank String orderId, boolean success) {
}
