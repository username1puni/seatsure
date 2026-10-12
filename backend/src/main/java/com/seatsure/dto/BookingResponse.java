package com.seatsure.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import com.seatsure.entity.BookingStatus;
import com.seatsure.entity.MovieKind;

public record BookingResponse(
        Long id, String bookingCode, BookingStatus status, Long showId, Long movieId, String movieTitle, MovieKind kind,
        String language, String certificate, String posterUrl, String theatreName, String theatreAddress, String city,
        String screenName, LocalDateTime startTime, List<String> seats, int seatCount, BigDecimal totalAmount,
        BigDecimal refundAmount, Instant holdExpiresAt, Instant createdAt, Instant confirmedAt, Instant cancelledAt,
        boolean cancellable) {
}
