package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * Java-only tests that the API looks exactly like the Python one to the frontend:
 * JSON names, error bodies, status codes, tokens and CORS. FastAPI gave the Python
 * backend these for free, so there were no Python tests for them.
 */
class ContractTest extends ApiTest {

    private static final String FRONTEND = "http://localhost:5173";

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    @Test
    void healthReportsStatusAndDemoMode() {
        Response res = getPath("/health");
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.text()).isEqualTo("{\"status\":\"ok\",\"demo_mode\":false}");
    }

    @Test
    void responsesUseSnakeCaseNamesInSchemaOrder() {
        Account account = register("student", "Miles");
        Response res = postJson("/auth/login", Map.of("email", account.user().get("email").asString(),
                "password", PASSWORD));
        JsonNode body = res.json();
        assertThat(names(body)).containsExactly("access_token", "token_type", "user");
        assertThat(body.get("token_type").asString()).isEqualTo("bearer");
        assertThat(names(body.get("user")))
                .containsExactly("id", "name", "email", "role", "points", "level", "link_code", "is_demo");
        assertThat(body.get("user").get("points").asInt()).isZero();
        assertThat(body.get("user").get("level").asInt()).isEqualTo(1);
    }

    @Test
    void parentLinkCodeIsSentAsNull() {
        Response res = getPath("/auth/me", register("parent"));
        assertThat(res.json().has("link_code")).isTrue();
        assertThat(res.json().get("link_code").isNull()).isTrue();
    }

    @Test
    void emailIsStoredInLowerCaseAndNameIsTrimmed() {
        Response res = postJson("/auth/register", Map.of("name", "  Miles  ", "email", "Miles@Example.COM",
                "password", PASSWORD, "role", "student"));
        assertThat(res.status()).isEqualTo(201);
        assertThat(res.json().get("user").get("email").asString()).isEqualTo("miles@example.com");
        assertThat(res.json().get("user").get("name").asString()).isEqualTo("Miles");
        Response login = postJson("/auth/login", Map.of("email", "MILES@example.com", "password", PASSWORD));
        assertThat(login.status()).isEqualTo(200);
    }

    // ── 422 bodies have FastAPI's shape: {"detail": [{"type", "loc", "msg"}]} ──

    @Test
    void unknownFieldsAreRejected() {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("name", "X", "email", "x@example.com",
                "password", PASSWORD, "role", "student"));
        body.put("points", 1_000_000);
        Response res = postJson("/auth/register", body);
        assertThat(res.status()).isEqualTo(422);
        JsonNode problem = res.json().get("detail").get(0);
        assertThat(problem.get("type").asString()).isEqualTo("extra_forbidden");
        assertThat(problem.get("loc").toString()).isEqualTo("[\"body\",\"points\"]");
        assertThat(problem.get("msg").asString()).isEqualTo("Extra inputs are not permitted");
        assertThat(users.count()).isZero();
    }

    @Test
    void missingFieldsAreListedInFieldOrder() {
        Response res = postJson("/auth/register", Map.of());
        assertThat(res.status()).isEqualTo(422);
        List<String> fields = new ArrayList<>();
        for (JsonNode problem : res.json().get("detail")) {
            assertThat(problem.get("msg").asString()).isEqualTo("Field required");
            assertThat(problem.get("type").asString()).isEqualTo("missing");
            assertThat(problem.get("loc").get(0).asString()).isEqualTo("body");
            fields.add(problem.get("loc").get(1).asString());
        }
        assertThat(fields).containsExactly("name", "email", "password", "role");
    }

    @Test
    void shortPasswordNamesTheField() {
        Response res = postJson("/auth/register", Map.of("name", "X", "email", "x@example.com",
                "password", "short", "role", "student"));
        assertThat(res.status()).isEqualTo(422);
        JsonNode problem = res.json().get("detail").get(0);
        assertThat(problem.get("loc").toString()).isEqualTo("[\"body\",\"password\"]");
        assertThat(problem.get("msg").asString()).isEqualTo("String should have at least 8 characters");
    }

    @Test
    void tooLongNameAndPasswordAreRejected() {
        assertThat(postJson("/auth/register", Map.of("name", "x".repeat(81), "email", "x@example.com",
                "password", PASSWORD, "role", "student")).status()).isEqualTo(422);
        assertThat(postJson("/auth/register", Map.of("name", "X", "email", "x@example.com",
                "password", "p".repeat(73), "role", "student")).status()).isEqualTo(422);
        assertThat(postJson("/auth/register", Map.of("name", "", "email", "x@example.com",
                "password", PASSWORD, "role", "student")).status()).isEqualTo(422);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plainaddress", "@example.com", "a@b", "a@@example.com", "a b@example.com",
            "a..b@example.com", "a@example.123", "a@localhost", "a@site.test", "a@-bad.com"})
    void invalidEmailsAreRejected(String email) {
        Response res = postJson("/auth/register", Map.of("name", "X", "email", email,
                "password", PASSWORD, "role", "student"));
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.json().get("detail").get(0).get("loc").get(1).asString()).isEqualTo("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"x@example.com", "first.last+tag@mail.example.co.uk", "demo.student@mathverse.demo"})
    void validEmailsAreAccepted(String email) {
        Response res = postJson("/auth/register", Map.of("name", "X", "email", email,
                "password", PASSWORD, "role", "parent"));
        assertThat(res.status()).isEqualTo(201);
    }

    @Test
    void wrongValueTypeIsRejected() {
        Response res = postJson("/auth/login", "{\"email\": \"x@example.com\", \"password\": {\"a\": 1}}");
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.json().get("detail").get(0).get("loc").toString()).isEqualTo("[\"body\",\"password\"]");
    }

    @Test
    void malformedJsonIsRejected() {
        Response res = postJson("/auth/login", "{\"email\": ");
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.json().get("detail").get(0).get("type").asString()).isEqualTo("json_invalid");
    }

    @Test
    void missingBodyIsRejected() {
        Response res = postEmpty("/auth/login");
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.json().get("detail").get(0).get("loc").toString()).isEqualTo("[\"body\"]");
    }

    @Test
    void bodyThatIsNotAnObjectIsRejected() {
        Response res = postJson("/auth/login", "[1, 2]");
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.json().get("detail").get(0).get("type").asString()).isEqualTo("model_attributes_type");
    }

    @Test
    void unknownDemoRoleIsRejectedEvenWithDemoModeOff() {
        Response res = postEmpty("/auth/demo/admin");
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.json().get("detail").get(0).get("loc").toString()).isEqualTo("[\"path\",\"role\"]");
    }

    // ── other status codes ───────────────────────────────────────────────────

    @Test
    void missingTokenGives401WithBearerChallenge() {
        Response res = getPath("/auth/me");
        assertThat(res.status()).isEqualTo(401);
        assertThat(res.raw().getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(res.text()).isEqualTo("{\"detail\":\"Please sign in.\"}");
    }

    @Test
    void wrongLoginGives401WithMessage() {
        Response res = postJson("/auth/login", Map.of("email", "ghost@example.com", "password", "nope-nope"));
        assertThat(res.text()).isEqualTo("{\"detail\":\"Incorrect email or password.\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic abc", "Bearer", "Bearer ", "Token abc", "bearer-abc"})
    void malformedAuthorizationHeadersGive401(String header) {
        assertThat(send(get("/auth/me").header("Authorization", header), null).status()).isEqualTo(401);
    }

    @Test
    void schemeIsCaseInsensitive() {
        Account account = register();
        Response res = send(get("/auth/me").header("Authorization", "bearer " + account.token()), null);
        assertThat(res.status()).isEqualTo(200);
    }

    @Test
    void expiredTokenGives401() {
        Account account = register();
        String expired = Jwts.builder().subject(String.valueOf(account.id()))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(settings.jwtSecret().getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        assertThat(send(get("/auth/me").header("Authorization", "Bearer " + expired), null).status()).isEqualTo(401);
    }

    @Test
    void tokenSignedWithAnotherSecretGives401() {
        Account account = register();
        String forged = Jwts.builder().subject(String.valueOf(account.id()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("some-other-secret-that-is-long-enough".getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
        assertThat(send(get("/auth/me").header("Authorization", "Bearer " + forged), null).status()).isEqualTo(401);
    }

    @Test
    void tokenOfADeletedUserGives401() {
        Account account = register();
        users.deleteById(account.id());
        assertThat(getPath("/auth/me", account).status()).isEqualTo(401);
    }

    @Test
    void unknownPathGives404NotFound() {
        Response res = getPath("/no-such-endpoint");
        assertThat(res.status()).isEqualTo(404);
        assertThat(res.text()).isEqualTo("{\"detail\":\"Not Found\"}");
    }

    @Test
    void wrongMethodGives405() {
        Response res = send(get("/auth/login"), null);
        assertThat(res.status()).isEqualTo(405);
        assertThat(res.text()).isEqualTo("{\"detail\":\"Method Not Allowed\"}");
    }

    @Test
    void demoLoginOffGives404NotFound() {
        Response res = postEmpty("/auth/demo/student");
        assertThat(res.text()).isEqualTo("{\"detail\":\"Not found\"}");
    }

    // ── CORS: the frontend on :5173 may call the API and read its errors ────

    @Test
    void preflightFromTheFrontendIsAllowed() {
        Response res = send(options("/auth/login")
                .header("Origin", FRONTEND)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,authorization"), null);
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.raw().getHeader("Access-Control-Allow-Origin")).isEqualTo(FRONTEND);
        assertThat(res.raw().getHeader("Access-Control-Allow-Methods")).contains("GET", "POST");
        assertThat(res.raw().getHeader("Access-Control-Allow-Credentials")).isNull();
    }

    @Test
    void errorResponsesCarryCorsHeaders() {
        Response res = send(get("/auth/me").header("Origin", FRONTEND), null);
        assertThat(res.status()).isEqualTo(401);
        assertThat(res.raw().getHeader("Access-Control-Allow-Origin")).isEqualTo(FRONTEND);
    }

    @Test
    void otherOriginsAreNotAllowed() {
        Response res = send(post("/auth/login").header("Origin", "http://evil.example")
                .contentType(MediaType.APPLICATION_JSON).content("{}"), null);
        assertThat(res.raw().getHeader("Access-Control-Allow-Origin")).isNull();
    }
}
