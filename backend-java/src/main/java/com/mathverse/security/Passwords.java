package com.mathverse.security;

import com.mathverse.config.Settings;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion;
import org.springframework.stereotype.Component;

/** Password hashing with bcrypt; each hash has its own random salt (Python: security.py). */
@Component
public class Passwords {

    private final BCryptPasswordEncoder bcrypt;

    public Passwords(Settings settings) {
        // $2b$ is the bcrypt variant the Python backend writes.
        bcrypt = new BCryptPasswordEncoder(BCryptVersion.$2B, settings.bcryptRounds());
    }

    public String hash(String password) {
        return bcrypt.encode(password);
    }

    public boolean matches(String password, String passwordHash) {
        try {
            return bcrypt.matches(password, passwordHash);
        } catch (IllegalArgumentException e) {
            // Malformed hash, or a password longer than bcrypt's 72-byte limit.
            return false;
        }
    }
}
