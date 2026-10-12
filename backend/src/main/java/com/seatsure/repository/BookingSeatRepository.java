package com.seatsure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.seatsure.entity.BookingSeat;
import com.seatsure.entity.BookingSeatStatus;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    @EntityGraph(attributePaths = "seat")
    List<BookingSeat> findByBookingIdOrderBySeatIdAsc(Long bookingId);

    long countByShowIdAndStatus(Long showId, BookingSeatStatus status);
}
