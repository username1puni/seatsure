package com.seatsure.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The state of one seat for one show: free, held while someone pays, or booked. */
@Entity
@Table(name = "show_seats")
@Getter
@Setter
@NoArgsConstructor
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ShowSeatStatus status = ShowSeatStatus.AVAILABLE;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "held_until")
    private Instant heldUntil;

    @Version
    private long version;

    public void hold(Long bookingId, Instant until) {
        this.status = ShowSeatStatus.HELD;
        this.bookingId = bookingId;
        this.heldUntil = until;
    }

    public void book() {
        this.status = ShowSeatStatus.BOOKED;
        this.heldUntil = null;
    }

    public void free() {
        this.status = ShowSeatStatus.AVAILABLE;
        this.bookingId = null;
        this.heldUntil = null;
    }
}
