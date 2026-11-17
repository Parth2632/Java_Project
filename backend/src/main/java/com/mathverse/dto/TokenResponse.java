package com.mathverse.dto;

/** Returned by register, login and demo login. */
public record TokenResponse(String accessToken, String tokenType, UserOut user) {

    public TokenResponse(String accessToken, UserOut user) {
        this(accessToken, "bearer", user);
    }
}
