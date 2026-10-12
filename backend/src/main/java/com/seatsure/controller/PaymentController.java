package com.seatsure.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seatsure.dto.BookingResponse;
import com.seatsure.dto.CreateOrderRequest;
import com.seatsure.dto.MockCheckoutRequest;
import com.seatsure.dto.MockCheckoutResponse;
import com.seatsure.dto.OrderResponse;
import com.seatsure.dto.PaymentConfigResponse;
import com.seatsure.dto.PaymentFailureRequest;
import com.seatsure.dto.VerifyPaymentRequest;
import com.seatsure.security.AuthUser;
import com.seatsure.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/config")
    @Operation(summary = "Which gateway is active (mock or razorpay) and its public key")
    public PaymentConfigResponse config() {
        return paymentService.config();
    }

    @PostMapping("/order")
    @Operation(summary = "Create a gateway order for a held booking")
    public OrderResponse createOrder(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateOrderRequest request) {
        return paymentService.createOrder(user.getId(), request.bookingId());
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify the checkout signature and confirm the booking")
    public BookingResponse verify(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verify(user.getId(), request);
    }

    @PostMapping("/failure")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Report a failed or abandoned payment so the seats are released")
    public void failure(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PaymentFailureRequest request) {
        paymentService.reportFailure(user.getId(), request);
    }

    @PostMapping("/mock/checkout")
    @Operation(summary = "Test checkout used when the mock gateway is active")
    public MockCheckoutResponse mockCheckout(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody MockCheckoutRequest request) {
        return paymentService.mockCheckout(user.getId(), request);
    }
}
