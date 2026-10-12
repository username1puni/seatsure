package com.seatsure.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PaymentGatewayTest {

    @Test
    void hmacMatchesTheRazorpayFormat() {
        // computed separately with: echo -n "order_123|pay_456" | openssl dgst -sha256 -hmac test_secret
        assertThat(HmacSigner.sign("order_123|pay_456", "test_secret"))
                .isEqualTo("6c343620f1910da483982cf25b9dc33d709afdd25930f08964ef60b65aefa831");
    }

    @Test
    void mockCheckoutSignatureVerifies() {
        MockPaymentGateway gateway = new MockPaymentGateway("secret");
        GatewayOrder order = gateway.createOrder(new BigDecimal("450.50"), "SSABC");
        MockPaymentGateway.MockPayment payment = gateway.pay(order.orderId());

        assertThat(order.amountPaise()).isEqualTo(45050);
        assertThat(gateway.verifySignature(order.orderId(), payment.paymentId(), payment.signature())).isTrue();
    }

    @Test
    void tamperedPaymentIsRejected() {
        MockPaymentGateway gateway = new MockPaymentGateway("secret");
        GatewayOrder order = gateway.createOrder(new BigDecimal("200"), "SSABC");
        MockPaymentGateway.MockPayment payment = gateway.pay(order.orderId());

        assertThat(gateway.verifySignature(order.orderId(), "pay_someone_else", payment.signature())).isFalse();
        assertThat(gateway.verifySignature("order_other", payment.paymentId(), payment.signature())).isFalse();
        assertThat(gateway.verifySignature(order.orderId(), payment.paymentId(), null)).isFalse();
        assertThat(new MockPaymentGateway("other-secret")
                .verifySignature(order.orderId(), payment.paymentId(), payment.signature())).isFalse();
    }
}
