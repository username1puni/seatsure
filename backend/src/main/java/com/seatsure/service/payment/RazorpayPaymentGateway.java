package com.seatsure.service.payment;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.seatsure.entity.PaymentGatewayType;
import com.seatsure.exception.PaymentException;

/**
 * Razorpay in test mode. Uses the plain REST API (Orders and Refunds) so no SDK is needed.
 * Docs: https://razorpay.com/docs/api/orders/ and https://razorpay.com/docs/payments/server-integration/java/
 */
public class RazorpayPaymentGateway implements PaymentGateway {

    private final String keyId;
    private final String keySecret;
    private final RestClient client;

    public RazorpayPaymentGateway(String keyId, String keySecret, RestClient.Builder builder) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.client = builder.baseUrl("https://api.razorpay.com/v1")
                .defaultHeaders(h -> h.setBasicAuth(keyId, keySecret))
                .build();
    }

    @Override
    public PaymentGatewayType type() {
        return PaymentGatewayType.RAZORPAY;
    }

    @Override
    public String publicKey() {
        return keyId;
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String receipt) {
        long paise = MockPaymentGateway.toPaise(amount);
        try {
            JsonNode order = client.post().uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", paise, "currency", "INR", "receipt", receipt))
                    .retrieve()
                    .body(JsonNode.class);
            return new GatewayOrder(order.get("id").asText(), paise, "INR");
        } catch (RestClientException e) {
            throw new PaymentException("Could not start the payment, please try again", e);
        }
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        return HmacSigner.matches(orderId + "|" + paymentId, keySecret, signature);
    }

    @Override
    public String refund(String paymentId, BigDecimal amount) {
        try {
            JsonNode refund = client.post().uri("/payments/{id}/refund", paymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", MockPaymentGateway.toPaise(amount)))
                    .retrieve()
                    .body(JsonNode.class);
            return refund.get("id").asText();
        } catch (RestClientException e) {
            throw new PaymentException("Refund failed at the payment gateway, please try again later", e);
        }
    }
}
