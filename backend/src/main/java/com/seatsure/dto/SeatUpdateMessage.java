package com.seatsure.dto;

import java.util.List;

public record SeatUpdateMessage(
        Long showId, List<SeatState> seats) {
}
