package com.mathverse.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * All configuration in one place (Python: config.py). Each value is read from an
 * environment variable of the same name, or from backend-java/.env.
 */
@Component
public class Settings {

    private static final Set<String> TRUE_WORDS = Set.of("1", "true", "yes", "on");

    private final String jwtSecret;
    private final int tokenExpireMinutes;
    private final int bcryptRounds;
    private final List<String> corsOrigins;
    private final String groqApiKey;
    private final String groqModel;
    private boolean demoMode;

    public Settings(Environment env) {
        jwtSecret = env.getProperty("JWT_SECRET_KEY", "").strip();
        if (jwtSecret.isEmpty()) {
            throw new IllegalStateException("JWT_SECRET_KEY is not set. Copy backend-java/.env.example to "
                    + "backend-java/.env and set JWT_SECRET_KEY to a long random string.");
        }
        // HS256 needs a key of at least 32 bytes; the JWT library refuses shorter ones.
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET_KEY is too short. Use at least 32 characters.");
        }
        tokenExpireMinutes = Integer.parseInt(env.getProperty("TOKEN_EXPIRE_MINUTES", "1440").strip());
        // bcrypt work factor. 12 is the usual default; tests lower it to run fast.
        bcryptRounds = Integer.parseInt(env.getProperty("BCRYPT_ROUNDS", "12").strip());
        // Off unless explicitly switched on. See AuthService.demoLogin.
        String demo = env.getProperty("DEMO_MODE", "false").strip().toLowerCase(Locale.ROOT);
        demoMode = TRUE_WORDS.contains(demo);
        corsOrigins = Arrays.stream(env.getProperty("CORS_ORIGINS",
                        "http://localhost:5173,http://127.0.0.1:5173").split(","))
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .toList();
        groqApiKey = env.getProperty("GROQ_API_KEY", "").strip();
        groqModel = env.getProperty("GROQ_MODEL", "openai/gpt-oss-120b").strip();
    }

    public String jwtSecret() {
        return jwtSecret;
    }

    public int tokenExpireMinutes() {
        return tokenExpireMinutes;
    }

    public int bcryptRounds() {
        return bcryptRounds;
    }

    public boolean demoMode() {
        return demoMode;
    }

    /** Only tests call this, to switch demo login on and off (the Python tests use monkeypatch). */
    public void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }

    public List<String> corsOrigins() {
        return corsOrigins;
    }

    public String groqApiKey() {
        return groqApiKey;
    }

    public String groqModel() {
        return groqModel;
    }
}
