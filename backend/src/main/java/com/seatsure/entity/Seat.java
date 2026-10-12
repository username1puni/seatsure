package com.seatsure.entity;

import java.util.Comparator;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seats")
@Getter
@Setter
@NoArgsConstructor
public class Seat {

    /** A1, A2 ... A10, B1 - numbers compared as numbers, not text. */
    public static final Comparator<Seat> DISPLAY_ORDER = Comparator.comparing((Seat s) -> s.getRowLabel().length())
            .thenComparing(Seat::getRowLabel)
            .thenComparingInt(Seat::getSeatNumber);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id")
    private Screen screen;

    @Column(name = "row_label", nullable = false, length = 2)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatCategory category;

    public Seat(Screen screen, String rowLabel, int seatNumber, SeatCategory category) {
        this.screen = screen;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.category = category;
    }

    public String getLabel() {
        return rowLabel + seatNumber;
    }
}
