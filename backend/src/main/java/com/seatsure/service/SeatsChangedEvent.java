package com.seatsure.service;

import java.util.List;

import com.seatsure.dto.SeatState;

/** Published inside a booking transaction; sent to browsers only after that transaction commits. */
public record SeatsChangedEvent(Long showId, List<SeatState> seats) {
}
