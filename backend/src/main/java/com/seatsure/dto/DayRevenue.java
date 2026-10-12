package com.seatsure.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DayRevenue(
        LocalDate day, BigDecimal revenue, long bookings) {
}
