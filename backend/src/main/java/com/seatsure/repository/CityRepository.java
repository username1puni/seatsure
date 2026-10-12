package com.seatsure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seatsure.entity.City;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
