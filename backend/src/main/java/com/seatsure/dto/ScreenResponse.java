package com.seatsure.dto;

import java.util.List;

public record ScreenResponse(
        Long id, Long theatreId, String theatreName, String name, int rows, int seatsPerRow, long seatCount, List<String> premiumRows) {
}
