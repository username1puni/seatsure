package com.seatsure.service.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.seatsure.entity.PaymentGatewayType;

/**
 * Stands in for Razorpay when no test keys are configured. It behaves the same way from the
 * app's point of view: create an order, the "checkout" returns a payment id and an HMAC
 * signature, and the backend verifies that signature before confirming anything.
 */
public class MockPaymentGateway implements PaymentGateway {

    private final String secret;

    public MockPaymentGateway(String secret) {
        this.secret = secret;
    }

    @Override
    public PaymentGatewayType type() {
        return PaymentGatewayType.MOCK;
    }

    @Override
    public String publicKey() {
        return null;
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String receipt) {
        return new GatewayOrder("order_mock_" + shortId(), toPaise(amount), "INR");
    }

    /** What the mock checkout page calls when the user presses Pay. */
    public MockPayment pay(String orderId) {
        String paymentId = "pay_mock_" + shortId();
        return new MockPayment(paymentId, HmacSigner.sign(orderId + "|" + paymentId, secret));
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        return HmacSigner.matches(orderId + "|" + paymentId, secret, signature);
    }

    @Override
    public String refund(String paymentId, BigDecimal amount) {
        return "rfnd_mock_" + shortId();
    }

    static long toPaise(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    public record MockPayment(String paymentId, String signature) {
    }
}
