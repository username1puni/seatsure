package com.seatsure.service.payment;

import java.math.BigDecimal;

import com.seatsure.entity.PaymentGatewayType;

/** What the booking flow needs from a payment provider. Razorpay and the mock both implement it. */
public interface PaymentGateway {

    PaymentGatewayType type();

    /** Public key the browser needs to open the checkout. Null for the mock. */
    String publicKey();

    GatewayOrder createOrder(BigDecimal amount, String receipt);

    /** Checks the signature the checkout returned, so a user can't fake a successful payment. */
    boolean verifySignature(String orderId, String paymentId, String signature);

    /** Refunds the amount and returns the gateway's refund id. */
    String refund(String paymentId, BigDecimal amount);
}
