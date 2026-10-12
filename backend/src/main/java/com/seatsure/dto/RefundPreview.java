package com.seatsure.dto;

import java.math.BigDecimal;

public record RefundPreview(
        Long bookingId, boolean cancellable, int refundPercent, BigDecimal refundAmount, String message) {
}
