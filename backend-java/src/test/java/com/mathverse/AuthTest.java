package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.mathverse.service.DemoData;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Port of backend/tests/test_auth.py. Each test names its Python original. */
class AuthTest extends ApiTest {

    @Autowired DemoData demoData;

    // test_register_login_and_me
    @Test
    void registerLoginAndMe() {
        Account account = register("student", "Miles");
        assertThat(account.user().get("role").asString()).isEqualTo("student");
        assertThat(account.user().get("link_code").asString()).startsWith("SPIDER-");
        assertThat(account.user().get("is_demo").asBoolean()).isFalse();

        Response res = postJson("/auth/login", Map.of("email", account.user().get("email").asString(),
                "password", PASSWORD));
        assertThat(res.status()).isEqualTo(200);
        String token = res.json().get("access_token").asString();
        Response me = send(get("/auth/me").header("Authorization", "Bearer " + token), null);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().get("id").asLong()).isEqualTo(account.id());
    }

    // test_password_is_stored_as_a_bcrypt_hash
    @Test
    void passwordIsStoredAsABcryptHash() {
        Account account = register();
        String stored = users.findById(account.id()).orElseThrow().getPasswordHash();
        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    // test_only_students_get_a_link_code
    @Test
    void onlyStudentsGetALinkCode() {
        assertThat(register("parent").user().get("link_code").isNull()).isTrue();
        assertThat(register("teacher").user().get("link_code").isNull()).isTrue();
    }

    // test_duplicate_email_is_rejected
    @Test
    void duplicateEmailIsRejected() {
        Account account = register();
        Response res = postJson("/auth/register", Map.of("name", "Copy",
                "email", account.user().get("email").asString(), "password", PASSWORD, "role", "student"));
        assertThat(res.status()).isEqualTo(409);
    }

    // test_roles_are_strictly_validated
    @ParameterizedTest
    @ValueSource(strings = {"admin", "Student", "", "guardian"})
    void rolesAreStrictlyValidated(String role) {
        Response res = postJson("/auth/register", Map.of("name", "X", "email", "x@example.com",
                "password", PASSWORD, "role", role));
        assertThat(res.status()).isEqualTo(422);
    }

    // test_login_failures_do_not_reveal_whether_the_account_exists
    @Test
    void loginFailuresDoNotRevealWhetherTheAccountExists() {
        Account account = register();
        Response wrongPassword = postJson("/auth/login", Map.of("email", account.user().get("email").asString(),
                "password", "nope-nope"));
        Response noAccount = postJson("/auth/login", Map.of("email", "ghost@example.com", "password", "nope-nope"));
        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(noAccount.status()).isEqualTo(401);
        assertThat(wrongPassword.json()).isEqualTo(noAccount.json());
    }

    // test_endpoints_require_a_token
    @ParameterizedTest
    @ValueSource(strings = {"/auth/me", "/students/me/progress", "/guardians/students", "/students/1/report"})
    void endpointsRequireAToken(String path) {
        assertThat(getPath(path).status()).isEqualTo(401);
        assertThat(send(get(path).header("Authorization", "Bearer not-a-real-token"), null).status())
                .isEqualTo(401);
    }

    // ── T8: demo login is unavailable unless DEMO_MODE is switched on ────────

    // test_demo_login_is_off_by_default
    @ParameterizedTest
    @FieldSource("com.mathverse.ApiTest#ROLES")
    void demoLoginIsOffByDefault(String role) {
        demoData.createDemoAccounts(); // even when demo accounts exist
        assertThat(postEmpty("/auth/demo/" + role).status()).isEqualTo(404);
    }

    // ── T9: demo login only ever reaches the seeded demo accounts ────────────

    // test_demo_login_returns_only_seeded_demo_accounts
    @ParameterizedTest
    @FieldSource("com.mathverse.ApiTest#ROLES")
    void demoLoginReturnsOnlySeededDemoAccounts(String role) {
        settings.setDemoMode(true);
        Account real = register(role, "Real Person"); // registered first, so it has the lowest id
        demoData.createDemoAccounts();
        long usersBefore = users.count();

        Response res = postEmpty("/auth/demo/" + role);
        assertThat(res.status()).isEqualTo(200);
        JsonNode user = res.json().get("user");
        assertThat(user.get("is_demo").asBoolean()).isTrue();
        assertThat(user.get("role").asString()).isEqualTo(role);
        assertThat(user.get("id").asLong()).isNotEqualTo(real.id());

        assertThat(users.count()).isEqualTo(usersBefore); // nothing was created
    }

    // test_demo_login_never_creates_accounts
    @Test
    void demoLoginNeverCreatesAccounts() {
        settings.setDemoMode(true);
        register("student");
        for (String role : ROLES) {
            assertThat(postEmpty("/auth/demo/" + role).status()).isEqualTo(404); // not seeded
        }
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.countDemoAccounts()).isZero();
    }

    // test_demo_login_rejects_unknown_roles
    @ParameterizedTest
    @ValueSource(strings = {"admin", "root", "STUDENT"})
    void demoLoginRejectsUnknownRoles(String role) {
        settings.setDemoMode(true);
        assertThat(postEmpty("/auth/demo/" + role).status()).isEqualTo(422);
    }

    // test_demo_accounts_cannot_log_in_with_a_password
    @Test
    void demoAccountsCannotLogInWithAPassword() {
        demoData.createDemoAccounts();
        Response res = postJson("/auth/login", Map.of("email", "demo.student@mathverse.demo", "password", PASSWORD));
        assertThat(res.status()).isEqualTo(401);
    }

    // test_demo_data_is_flagged_and_seeding_is_idempotent
    @Test
    void demoDataIsFlaggedAndSeedingIsIdempotent() {
        settings.setDemoMode(true);
        assertThat(demoData.createDemoAccounts()).isTrue();
        assertThat(demoData.createDemoAccounts()).isFalse();
        assertThat(users.countDemoAccounts()).isEqualTo(3);

        String token = postEmpty("/auth/demo/student").json().get("access_token").asString();
        JsonNode me = send(get("/auth/me").header("Authorization", "Bearer " + token), null).json();
        JsonNode report = send(get("/students/" + me.get("id").asLong() + "/report")
                .header("Authorization", "Bearer " + token), null).json();
        assertThat(me.get("is_demo").asBoolean()).isTrue();
        assertThat(report.get("is_demo").asBoolean()).isTrue();
        assertThat(report.get("questions_answered").asInt()).isEqualTo(30);
        // some demo questions were measured, some were not (camera off)
        int measured = report.get("attention").get("measured_questions").asInt();
        assertThat(measured).isGreaterThan(0).isLessThan(report.get("attention").get("total_questions").asInt());
    }
}
