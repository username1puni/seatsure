package com.seatsure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.seatsure.entity.Role;
import com.seatsure.entity.User;

class JwtServiceTest {

    private final JwtService jwt = new JwtService("a-test-secret-that-is-long-enough-123456", 60);

    @Test
    void tokenRoundTrip() {
        User user = new User("Asha", "asha@test.local", "hash", Role.USER);
        user.setId(7L);
        String token = jwt.createToken(new AuthUser(user));
        assertThat(jwt.validateAndGetSubject(token)).isEqualTo("asha@test.local");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService("some-other-secret-that-is-also-long-0000", 60);
        User user = new User("Asha", "asha@test.local", "hash", Role.USER);
        user.setId(7L);
        String token = other.createToken(new AuthUser(user));
        assertThat(jwt.validateAndGetSubject(token)).isNull();
        assertThat(jwt.validateAndGetSubject("not-a-token")).isNull();
    }

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> new JwtService("short", 60)).isInstanceOf(IllegalStateException.class);
    }
}
