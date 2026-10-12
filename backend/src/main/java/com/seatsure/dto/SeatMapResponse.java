package com.seatsure.dto;

import java.util.List;

public record SeatMapResponse(
        Long showId, int holdMinutes, int maxSeats, List<SeatRow> rows) {
}
