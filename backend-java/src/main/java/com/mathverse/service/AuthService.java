package com.mathverse.service;

import com.mathverse.config.ApiException;
import com.mathverse.config.Settings;
import com.mathverse.dto.LoginRequest;
import com.mathverse.dto.RegisterRequest;
import com.mathverse.dto.TokenResponse;
import com.mathverse.dto.UserOut;
import com.mathverse.model.Roles;
import com.mathverse.model.User;
import com.mathverse.model.UserRepository;
import com.mathverse.security.Passwords;
import com.mathverse.security.Tokens;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounts: register, log in, demo login (Python: routers/auth.py). */
@Service
public class AuthService {

    /** No 0/O/1/I, so a code is easy to read aloud. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final Passwords passwords;
    private final Tokens tokens;
    private final Settings settings;

    public AuthService(UserRepository users, Passwords passwords, Tokens tokens, Settings settings) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.settings = settings;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new ApiException(409, "An account with this email already exists.");
        }
        String linkCode = request.role().equals(Roles.STUDENT) ? newLinkCode() : null;
        User user = new User(request.name().strip(), email, passwords.hash(request.password()),
                request.role(), linkCode, false);
        users.save(user);
        return tokenResponse(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().strip().toLowerCase(Locale.ROOT)).orElse(null);
        // Same message for "no such account" and "wrong password", so the form
        // cannot be used to discover which emails are registered.
        if (user == null || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(401, "Incorrect email or password.");
        }
        return tokenResponse(user);
    }

    /**
     * One-click login for the seeded demo accounts.
     *  - Does nothing unless DEMO_MODE=true (default false): 404.
     *  - Only ever returns an account with is_demo = true, made by the seed command.
     *  - Never creates an account and never returns a real user's token.
     */
    @Transactional(readOnly = true)
    public TokenResponse demoLogin(String role) {
        // The role is checked first, as FastAPI checks path values before running the endpoint.
        if (!Roles.ALL.contains(role)) {
            throw ApiException.invalid(List.of("path", "role"), Roles.INVALID_MESSAGE, "literal_error");
        }
        if (!settings.demoMode()) {
            throw new ApiException(404, "Not found");
        }
        User user = users.findDemoAccount(role).orElseThrow(() -> new ApiException(404,
                "Demo accounts have not been created. Run the seed command with --demo (see README)."));
        return tokenResponse(user);
    }

    /** A short code only the student's own account shows; a guardian needs it to link. */
    String newLinkCode() {
        while (true) {
            StringBuilder code = new StringBuilder("SPIDER-");
            for (int i = 0; i < 6; i++) {
                code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            if (!users.existsByLinkCode(code.toString())) {
                return code.toString();
            }
        }
    }

    private TokenResponse tokenResponse(User user) {
        return new TokenResponse(tokens.create(user.getId()), UserOut.from(user));
    }
}
