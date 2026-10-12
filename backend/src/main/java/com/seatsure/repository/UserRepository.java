package com.seatsure.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seatsure.entity.Role;
import com.seatsure.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);
}
