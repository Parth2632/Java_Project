package com.mathverse.dto;

import com.mathverse.model.Roles;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /auth/register. Unknown fields are rejected (application.properties). */
public record RegisterRequest(
        @NotNull(message = "Field required")
        @Size(min = 1, message = "String should have at least 1 character")
        @Size(max = 80, message = "String should have at most 80 characters")
        String name,

        @NotNull(message = "Field required")
        @ValidEmail
        String email,

        // bcrypt reads at most 72 bytes
        @NotNull(message = "Field required")
        @Size(min = 8, message = "String should have at least 8 characters")
        @Size(max = 72, message = "String should have at most 72 characters")
        String password,

        @NotNull(message = "Field required")
        @Pattern(regexp = "student|parent|teacher", message = Roles.INVALID_MESSAGE)
        String role) {
}
