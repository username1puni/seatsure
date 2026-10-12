package com.seatsure.dto;

import java.math.BigDecimal;

import com.seatsure.entity.SeatCategory;
import com.seatsure.entity.ShowSeatStatus;

public record SeatView(
        Long id, int number, String label, SeatCategory category, ShowSeatStatus status, BigDecimal price) {
}
