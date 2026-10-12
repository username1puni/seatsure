package com.seatsure.dto;

import java.math.BigDecimal;

public record TopMovie(
        String title, long seatsSold, BigDecimal revenue) {
}
