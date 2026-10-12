package com.seatsure.dto;

public record TheatreResponse(
        Long id, String name, String address, Long cityId, String cityName, int screenCount) {
}
