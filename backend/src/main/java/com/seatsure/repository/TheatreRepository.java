package com.seatsure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.seatsure.entity.Theatre;

public interface TheatreRepository extends JpaRepository<Theatre, Long> {

    @EntityGraph(attributePaths = "city")
    List<Theatre> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "city")
    List<Theatre> findByCityIdOrderByNameAsc(Long cityId);
}
