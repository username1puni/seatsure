package com.seatsure.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.seatsure.entity.BookingStatus;

public record RecentBooking(
        String bookingCode, String userName, String movieTitle, int seatCount, BigDecimal amount, BookingStatus status, Instant confirmedAt) {
}
