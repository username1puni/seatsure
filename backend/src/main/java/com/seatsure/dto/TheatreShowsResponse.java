package com.seatsure.dto;

import java.util.List;

public record TheatreShowsResponse(
        Long theatreId, String theatreName, String address, List<ShowSlot> shows) {
}
