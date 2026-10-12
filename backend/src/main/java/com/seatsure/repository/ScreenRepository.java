package com.seatsure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seatsure.entity.Screen;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    List<Screen> findByTheatreIdOrderByNameAsc(Long theatreId);

    boolean existsByTheatreIdAndNameIgnoreCase(Long theatreId, String name);
}
