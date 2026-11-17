package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.mathverse.config.Settings;
import com.mathverse.model.AttemptRepository;
import com.mathverse.model.GuardianLinkRepository;
import com.mathverse.model.PlaySessionRepository;
import com.mathverse.model.TopicMasteryRepository;
import com.mathverse.model.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared set-up for API tests (Python: tests/conftest.py).
 *
 * The whole application runs against an in-memory SQLite database that is
 * emptied before every test, with a test-only secret, demo mode off, the fastest
 * bcrypt setting and no AI key, so the tests need no .env file.
 */
@SpringBootTest(properties = {
        "JWT_SECRET_KEY=test-only-secret-not-used-anywhere-else",
        "DEMO_MODE=false",
        "BCRYPT_ROUNDS=4",
        "GROQ_API_KEY=",
        "DATABASE_URL=jdbc:sqlite:file:mathverse-test?mode=memory&cache=shared",
        // keep the one connection open, or the in-memory database would vanish
        "spring.datasource.hikari.max-lifetime=0",
})
@AutoConfigureMockMvc
public abstract class ApiTest {

    public static final String PASSWORD = "correct-horse-battery";
    public static final List<String> ROLES = List.of("student", "parent", "teacher");

    @Autowired protected MockMvc mvc;
    @Autowired protected JsonMapper json;
    @Autowired protected Settings settings;
    @Autowired protected UserRepository users;
    @Autowired protected GuardianLinkRepository links;
    @Autowired protected PlaySessionRepository playSessions;
    @Autowired protected AttemptRepository attempts;
    @Autowired protected TopicMasteryRepository masteries;

    private int registered = 0;

    @BeforeEach
    void emptyDatabase() {
        attempts.deleteAllInBatch();
        playSessions.deleteAllInBatch();
        masteries.deleteAllInBatch();
        links.deleteAllInBatch();
        users.deleteAllInBatch();
        settings.setDemoMode(false);
    }

    /** A registered account: its token, the user as JSON, and its id. */
    public record Account(String token, JsonNode user, long id) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    /** Python fixture "register": a new account with a unique email. */
    protected Account register(String role, String name) {
        registered++;
        Response res = postJson("/auth/register", Map.of("name", name,
                "email", "user" + registered + "@example.com", "password", PASSWORD, "role", role));
        assertThat(res.status()).as(res.text()).isEqualTo(201);
        JsonNode body = res.json();
        return new Account(body.get("access_token").asString(), body.get("user"), body.get("user").get("id").asLong());
    }

    protected Account register(String role) {
        return register(role, "Test User");
    }

    protected Account register() {
        return register("student");
    }

    // ── requests ─────────────────────────────────────────────────────────────

    /** A response: status code, body text, and body as JSON. */
    public record Response(int status, String text, MockHttpServletResponse raw, JsonMapper mapper) {
        public JsonNode json() {
            return mapper.readTree(text);
        }
    }

    protected Response postJson(String path, Object body) {
        return postJson(path, body, null);
    }

    protected Response postJson(String path, Object body, Account account) {
        String text = body instanceof String s ? s : json.writeValueAsString(body);
        return send(post(path).contentType(MediaType.APPLICATION_JSON).content(text), account);
    }

    protected Response postEmpty(String path) {
        return send(post(path), null);
    }

    protected Response getPath(String path, Account account) {
        return send(get(path), account);
    }

    protected Response getPath(String path) {
        return getPath(path, null);
    }

    protected Response send(MockHttpServletRequestBuilder request, Account account) {
        if (account != null) {
            request.header("Authorization", account.bearer());
        }
        try {
            MockHttpServletResponse raw = mvc.perform(request).andReturn().getResponse();
            return new Response(raw.getStatus(), raw.getContentAsString(), raw, json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
