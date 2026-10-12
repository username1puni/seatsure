package com.seatsure.exception;

import java.util.List;

import lombok.Getter;

/** Thrown when at least one of the requested seats is already held or booked by someone else. */
@Getter
public class SeatUnavailableException extends RuntimeException {

    private final List<String> seats;

    public SeatUnavailableException(List<String> seats) {
        super("Sorry, " + String.join(", ", seats) + (seats.size() == 1 ? " was" : " were")
                + " just taken by someone else. Please pick other seats.");
        this.seats = seats;
    }
}
