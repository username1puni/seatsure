package com.seatsure.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ShowRequest(
        @NotNull Long movieId,
        @NotNull Long screenId,
        @NotNull LocalDateTime startTime,
        @NotNull @DecimalMin(value = "50", message = "Price must be at least 50") @DecimalMax("5000") BigDecimal priceNormal,
        @NotNull @DecimalMin(value = "50", message = "Price must be at least 50") @DecimalMax("5000") BigDecimal pricePremium) {
}
