package com.seatsure.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class RefundPolicyTest {

    private final RefundPolicy policy = new RefundPolicy();
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0);

    @Test
    void fullRefundADayOrMoreBefore() {
        assertThat(policy.refundPercent(now.plusHours(24), now)).isEqualTo(100);
        assertThat(policy.refundPercent(now.plusDays(3), now)).isEqualTo(100);
    }

    @Test
    void partialRefundBetweenTwoAndTwentyFourHours() {
        assertThat(policy.refundPercent(now.plusHours(23).plusMinutes(59), now)).isEqualTo(75);
        assertThat(policy.refundPercent(now.plusHours(2), now)).isEqualTo(75);
    }

    @Test
    void noCancellationInTheLastTwoHours() {
        assertThat(policy.refundPercent(now.plusMinutes(119), now)).isZero();
        assertThat(policy.refundPercent(now.minusMinutes(5), now)).isZero();
    }

    @Test
    void amountIsRoundedToPaise() {
        assertThat(policy.refundAmount(new BigDecimal("333.33"), 75)).isEqualByComparingTo("250.00");
        assertThat(policy.refundAmount(new BigDecimal("450"), 100)).isEqualByComparingTo("450.00");
    }
}
