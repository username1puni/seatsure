package com.seatsure.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PricingRequest(
        @NotNull @DecimalMin("50") @DecimalMax("5000") BigDecimal priceNormal,
        @NotNull @DecimalMin("50") @DecimalMax("5000") BigDecimal pricePremium,
        long version) {
}
