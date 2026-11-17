package com.mathverse.controller;

import com.mathverse.dto.LoginRequest;
import com.mathverse.dto.RegisterRequest;
import com.mathverse.dto.TokenResponse;
import com.mathverse.dto.UserOut;
import com.mathverse.model.User;
import com.mathverse.security.CurrentUser;
import com.mathverse.security.SignedIn;
import com.mathverse.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /auth endpoints (Python: routers/auth.py). The work happens in {@link AuthService}. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest body) {
        return auth.register(body);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest body) {
        return auth.login(body);
    }

    @GetMapping("/me")
    @SignedIn
    public UserOut me(@CurrentUser User user) {
        return UserOut.from(user);
    }

    @PostMapping("/demo/{role}")
    public TokenResponse demoLogin(@PathVariable String role) {
        return auth.demoLogin(role);
    }
}
