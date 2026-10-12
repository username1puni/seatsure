package com.seatsure.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TicketVerifyResponse(
        String result, String message, String bookingCode, String movieTitle, String theatreName, LocalDateTime startTime, List<String> seats, String holder) {
}
