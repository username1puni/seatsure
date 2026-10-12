package com.seatsure.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class BookingCodeGenerator {

    // no 0/O or 1/I so the code is easy to read out at the counter
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder sb = new StringBuilder("SS");
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
