package com.seatsure.service.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class PaymentGatewayConfig {

    @Bean
    PaymentGateway paymentGateway(@Value("${app.payment.gateway}") String gateway,
                                  @Value("${app.payment.mock-secret}") String mockSecret,
                                  @Value("${app.payment.razorpay.key-id}") String keyId,
                                  @Value("${app.payment.razorpay.key-secret}") String keySecret,
                                  RestClient.Builder restClientBuilder) {
        if ("razorpay".equalsIgnoreCase(gateway)) {
            if (!StringUtils.hasText(keyId) || !StringUtils.hasText(keySecret)) {
                throw new IllegalStateException("PAYMENT_GATEWAY=razorpay needs RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
            }
            log.info("Using Razorpay gateway with key {}", keyId);
            return new RazorpayPaymentGateway(keyId, keySecret, restClientBuilder);
        }
        log.info("Using the mock payment gateway (no real money moves)");
        return new MockPaymentGateway(mockSecret);
    }
}
