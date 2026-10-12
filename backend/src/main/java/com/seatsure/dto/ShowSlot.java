package com.seatsure.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowSlot(
        Long id, LocalDateTime startTime, String screenName, BigDecimal priceNormal, BigDecimal pricePremium, long availableSeats, long totalSeats) {
}
