package com.seatsure.dto;

import com.seatsure.entity.ShowSeatStatus;

public record SeatState(
        Long seatId, ShowSeatStatus status) {
}
