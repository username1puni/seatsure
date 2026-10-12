package com.seatsure.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.seatsure.entity.ShowStatus;

public record ShowDetailResponse(
        Long id, MovieResponse movie, Long theatreId, String theatreName, String theatreAddress, String city,
        String screenName, LocalDateTime startTime, LocalDateTime endTime, BigDecimal priceNormal,
        BigDecimal pricePremium, ShowStatus status) {
}
