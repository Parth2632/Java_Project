package com.mathverse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /auth/login */
public record LoginRequest(
        @NotNull(message = "Field required")
        @ValidEmail
        String email,

        @NotNull(message = "Field required")
        @Size(min = 1, message = "String should have at least 1 character")
        @Size(max = 72, message = "String should have at most 72 characters")
        String password) {
}
