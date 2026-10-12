package com.seatsure.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BookingCodeGeneratorTest {

    @Test
    void codesAreReadableAndDoNotRepeat() {
        BookingCodeGenerator generator = new BookingCodeGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String code = generator.next();
            assertThat(code).matches("SS[A-HJ-NP-Z2-9]{8}");
            seen.add(code);
        }
        assertThat(seen).hasSize(10_000);
    }
}
