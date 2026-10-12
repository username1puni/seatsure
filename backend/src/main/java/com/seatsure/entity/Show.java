package com.seatsure.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shows")
@Getter
@Setter
@NoArgsConstructor
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id")
    private Screen screen;

    /** Local time at the theatre. */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "price_normal", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceNormal;

    @Column(name = "price_premium", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePremium;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ShowStatus status = ShowStatus.SCHEDULED;

    // two admins editing prices at once should not silently overwrite each other
    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public BigDecimal priceFor(SeatCategory category) {
        return category == SeatCategory.PREMIUM ? pricePremium : priceNormal;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
