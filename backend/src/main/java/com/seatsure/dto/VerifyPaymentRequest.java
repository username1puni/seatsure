package com.seatsure.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPaymentRequest(
        @NotBlank String orderId, @NotBlank String paymentId, @NotBlank String signature) {
}
