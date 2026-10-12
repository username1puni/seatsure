package com.seatsure.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

/**
 * Cancellation rules:
 * 24 hours or more before the show: full refund.
 * 2 to 24 hours before: 75% back.
 * Less than 2 hours: cannot cancel.
 */
@Component
public class RefundPolicy {

    public int refundPercent(LocalDateTime showStart, LocalDateTime now) {
        Duration left = Duration.between(now, showStart);
        if (left.compareTo(Duration.ofHours(24)) >= 0) {
            return 100;
        }
        if (left.compareTo(Duration.ofHours(2)) >= 0) {
            return 75;
        }
        return 0;
    }

    public BigDecimal refundAmount(BigDecimal paid, int percent) {
        return paid.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
