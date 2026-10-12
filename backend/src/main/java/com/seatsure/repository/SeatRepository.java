package com.seatsure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seatsure.entity.Seat;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreenIdOrderByRowLabelAscSeatNumberAsc(Long screenId);

    long countByScreenId(Long screenId);
}
