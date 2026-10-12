package com.seatsure.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.seatsure.entity.ShowStatus;

public record AdminShowResponse(
        Long id, Long movieId, String movieTitle, Long theatreId, String theatreName, Long screenId, String screenName,
        LocalDateTime startTime, LocalDateTime endTime, BigDecimal priceNormal, BigDecimal pricePremium,
        ShowStatus status, long seatsBooked, long capacity, long version) {
}
