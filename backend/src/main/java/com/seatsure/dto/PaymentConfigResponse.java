package com.seatsure.dto;

import com.seatsure.entity.PaymentGatewayType;

public record PaymentConfigResponse(
        PaymentGatewayType gateway, String keyId) {
}
