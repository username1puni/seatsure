package com.seatsure.dto;

import java.util.List;

import com.seatsure.entity.SeatCategory;

public record SeatRow(
        String label, SeatCategory category, List<SeatView> seats) {
}
