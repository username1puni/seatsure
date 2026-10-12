package com.seatsure.service.payment;

public record GatewayOrder(String orderId, long amountPaise, String currency) {
}
