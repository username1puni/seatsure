package com.seatsure.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 150) String email,
        @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
        @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Enter a 10 digit mobile number") String phone) {
}
