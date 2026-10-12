package com.seatsure.service;

import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.seatsure.dto.AuthResponse;
import com.seatsure.dto.LoginRequest;
import com.seatsure.dto.RegisterRequest;
import com.seatsure.dto.UserResponse;
import com.seatsure.entity.Role;
import com.seatsure.entity.User;
import com.seatsure.exception.ConflictException;
import com.seatsure.exception.NotFoundException;
import com.seatsure.repository.UserRepository;
import com.seatsure.security.AuthUser;
import com.seatsure.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User(request.fullName().trim(), email, passwordEncoder.encode(request.password()), Role.USER);
        if (StringUtils.hasText(request.phone())) {
            user.setPhone(request.phone().trim());
        }
        userRepository.save(user);
        return tokenFor(new AuthUser(user), user);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim(), request.password()));
        AuthUser principal = (AuthUser) auth.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        return tokenFor(principal, user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return userRepository.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private AuthResponse tokenFor(AuthUser principal, User user) {
        return new AuthResponse(jwtService.createToken(principal), jwtService.expirySeconds(), UserResponse.from(user));
    }
}
