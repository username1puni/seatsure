package com.seatsure.dto;

public record MockCheckoutResponse(
        String orderId, String paymentId, String signature, boolean success, String error) {
}
