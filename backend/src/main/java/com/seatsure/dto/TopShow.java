package com.seatsure.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TopShow(
        Long showId, String movieTitle, String theatreName, LocalDateTime startTime, long seatsSold, long capacity, BigDecimal revenue) {
}
