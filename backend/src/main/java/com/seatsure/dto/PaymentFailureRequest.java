package com.seatsure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentFailureRequest(
        @NotBlank String orderId, @Size(max = 200) String reason) {
}
