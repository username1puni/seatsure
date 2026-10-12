package com.seatsure.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.seatsure.entity.PaymentGatewayType;

public record OrderResponse(
        PaymentGatewayType gateway, String keyId, String orderId, BigDecimal amount, long amountPaise, String currency,
        Long bookingId, String bookingCode, Instant holdExpiresAt, String description) {
}
