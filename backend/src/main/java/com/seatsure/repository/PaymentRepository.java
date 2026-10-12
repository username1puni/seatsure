package com.seatsure.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seatsure.entity.Payment;
import com.seatsure.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    Optional<Payment> findFirstByBookingIdAndStatusOrderByIdDesc(Long bookingId, PaymentStatus status);

    List<Payment> findByBookingIdOrderByIdDesc(Long bookingId);
}
