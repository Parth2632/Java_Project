package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mathverse.model.Attempt;
import com.mathverse.service.Game;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Port of backend/tests/test_sessions.py. */
class SessionsTest extends ApiTest {

    static final Map<String, Object> ATTENTION = Map.of("face_present_pct", 96.0, "on_screen_pct", 88.0,
            "blink_rate_per_min", 15.0, "sample_seconds", 8.0);

    private long firstAttempt(Account student) {
        return start(student).json().get("question").get("attempt_id").asLong();
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    // test_question_never_contains_the_answer
    @Test
    void questionNeverContainsTheAnswer() {
        JsonNode body = start(register()).json();
        assertThat(names(body.get("question")))
                .containsExactlyInAnyOrder("attempt_id", "text", "options", "topic", "difficulty");
        assertThat(body.get("question").get("options").size()).isEqualTo(4);
    }

    // test_correct_answer_scores_and_raises_mastery
    @Test
    void correctAnswerScoresAndRaisesMastery() {
        Account student = register();
        long attemptId = firstAttempt(student);
        JsonNode body = play(student, attemptId, true);
        assertThat(body.get("correct").asBoolean()).isTrue();
        assertThat(body.get("points_awarded").asInt()).isEqualTo(70); // 50 base + 20 speed bonus, no streak yet
        assertThat(body.get("mastery").asDouble()).isGreaterThan(0.30);
        assertThat(body.get("session").get("correct_count").asInt()).isEqualTo(1);
        assertThat(body.get("session").get("streak").asInt()).isEqualTo(1);
        assertThat(body.get("progress").get("points").asInt()).isEqualTo(70);
        assertThat(body.get("next_question").get("attempt_id").asLong()).isNotEqualTo(attemptId);
    }

    // test_wrong_answer_is_stored_as_wrong_and_lowers_mastery
    // Regression for the old bug where any score above 0 counted as correct.
    @Test
    void wrongAnswerIsStoredAsWrongAndLowersMastery() {
        Account student = register();
        long attemptId = firstAttempt(student);
        JsonNode body = play(student, attemptId, false);
        assertThat(body.get("correct").asBoolean()).isFalse();
        assertThat(body.get("mastery").asDouble()).isLessThan(0.30);
        assertThat(body.get("session").get("wrong_count").asInt()).isEqualTo(1);
        assertThat(body.get("session").get("streak").asInt()).isZero();
        assertThat(attempts.findById(attemptId).orElseThrow().getIsCorrect()).isFalse();

        JsonNode report = getPath("/students/" + student.id() + "/report", student).json();
        assertThat(report.get("accuracy_pct").asInt()).isZero();
        assertThat(report.get("correct_answers").asInt()).isZero();
    }

    // test_timeout_counts_as_wrong_and_earns_nothing
    @Test
    void timeoutCountsAsWrongAndEarnsNothing() {
        Account student = register();
        long attemptId = firstAttempt(student);
        Map<String, Object> payload = new HashMap<>();
        payload.put("selected_index", null);
        payload.put("response_time_ms", 300000);
        Response res = postJson("/attempts/" + attemptId + "/answer", payload, student);
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.json().get("correct").asBoolean()).isFalse();
        assertThat(res.json().get("points_awarded").asInt()).isZero();
    }

    // ── T1 (server half): a whole mission without any camera data ────────────

    // test_mission_can_be_won_without_a_camera
    @Test
    void missionCanBeWonWithoutACamera() {
        Account student = register();
        JsonNode last = winMission(student); // no "attention" field sent at all
        assertThat(last.get("session_status").asString()).isEqualTo("victory");
        assertThat(last.get("bonus_points").asInt()).isEqualTo(Game.victoryBonus(Game.QUESTIONS_TO_WIN));
        assertThat(last.get("progress").get("level").asInt()).isEqualTo(2);
        assertThat(last.get("progress").get("unlocked_bosses").toString()).isEqualTo("[\"goblin\",\"doc-ock\"]");
        assertThat(last.get("progress").get("missions_won").asInt()).isEqualTo(1);

        List<Attempt> rows = attempts.findByStudentIdOrderById(student.id());
        assertThat(rows).hasSize(Game.QUESTIONS_TO_WIN);
        for (Attempt a : rows) { // not measured stays NULL, never 0 or a default label
            assertThat(a.getFacePresentPct()).isNull();
            assertThat(a.getOnScreenPct()).isNull();
            assertThat(a.getBlinkRatePerMin()).isNull();
            assertThat(a.getAttentionScore()).isNull();
            assertThat(a.getAttentionLabel()).isNull();
        }
    }

    // ── T2 (server half): camera data is stored, and changes nothing academic ──

    // test_attention_is_stored_but_does_not_change_the_outcome
    @Test
    void attentionIsStoredButDoesNotChangeTheOutcome() {
        Account without = register();
        Account withCamera = register();
        JsonNode a = winMission(without, "goblin", Map.of());
        JsonNode b = winMission(withCamera, "goblin", Map.of("attention", ATTENTION));
        for (String field : List.of("points", "level", "unlocked_bosses", "missions_won")) {
            assertThat(a.get("progress").get(field)).as(field).isEqualTo(b.get("progress").get(field));
        }
        assertThat(a.get("mastery")).isEqualTo(b.get("mastery"));

        Attempt row = attempts.findByStudentIdOrderById(withCamera.id()).getFirst();
        assertThat(row.getOnScreenPct()).isEqualTo(88.0);
        assertThat(row.getAttentionLabel()).isEqualTo("Focused");
    }

    // test_mission_is_lost_after_too_many_wrong_answers
    @Test
    void missionIsLostAfterTooManyWrongAnswers() {
        Account student = register();
        long attemptId = firstAttempt(student);
        JsonNode body = null;
        for (int i = 0; i < Game.MAX_WRONG; i++) {
            body = play(student, attemptId, false);
            if (!body.get("next_question").isNull()) {
                attemptId = body.get("next_question").get("attempt_id").asLong();
            }
        }
        assertThat(body.get("session_status").asString()).isEqualTo("defeat");
        assertThat(body.get("next_question").isNull()).isTrue();
        assertThat(body.get("progress").get("level").asInt()).isEqualTo(1);
    }

    // test_question_cannot_be_answered_twice
    @Test
    void questionCannotBeAnsweredTwice() {
        Account student = register();
        long attemptId = firstAttempt(student);
        play(student, attemptId, false);
        Response res = postJson("/attempts/" + attemptId + "/answer",
                Map.of("selected_index", 0, "response_time_ms", 1000), student);
        assertThat(res.status()).isEqualTo(409);
    }

    // test_locked_and_unknown_missions_are_refused
    @Test
    void lockedAndUnknownMissionsAreRefused() {
        Account student = register();
        assertThat(start(student, "kingpin").status()).isEqualTo(403);
        assertThat(start(student, "thanos").status()).isEqualTo(404);
    }

    // test_one_student_cannot_touch_anothers_question
    @Test
    void oneStudentCannotTouchAnothersQuestion() {
        Account owner = register();
        Account other = register();
        long attemptId = firstAttempt(owner);
        Map<String, Object> payload = Map.of("selected_index", 0, "response_time_ms", 1000);
        assertThat(postJson("/attempts/" + attemptId + "/answer", payload, other).status()).isEqualTo(404);
        assertThat(getPath("/attempts/" + attemptId + "/hint", other).status()).isEqualTo(404);
    }

    // test_gameplay_requires_a_student_account
    @Test
    void gameplayRequiresAStudentAccount() {
        for (String role : List.of("parent", "teacher")) {
            Account account = register(role);
            assertThat(start(account).status()).isEqualTo(403);
            assertThat(getPath("/students/me/progress", account).status()).isEqualTo(403);
        }
        assertThat(postJson("/sessions", Map.of("boss_id", "goblin")).status()).isEqualTo(401);
    }

    // test_points_cannot_be_set_by_the_client
    @Test
    void pointsCannotBeSetByTheClient() {
        Account student = register();
        long attemptId = firstAttempt(student);
        for (Map<String, Object> extra : List.<Map<String, Object>>of(Map.of("score", 9999),
                Map.of("bonus_points", 9999), Map.of("user_id", 1))) {
            Map<String, Object> payload = new HashMap<>(Map.of("selected_index", 0, "response_time_ms", 1000));
            payload.putAll(extra);
            assertThat(postJson("/attempts/" + attemptId + "/answer", payload, student).status())
                    .as(extra.toString()).isEqualTo(422);
        }
    }

    // ── T5 (server half): there is nowhere to send an image ──────────────────

    // test_image_data_is_rejected
    @Test
    void imageDataIsRejected() {
        Account student = register();
        long attemptId = firstAttempt(student);
        Map<String, Object> withImage = new HashMap<>(Map.of("selected_index", 0, "response_time_ms", 1000));
        withImage.put("image_base64", "data:image/jpeg;base64,AAAA");
        Map<String, Object> frame = new HashMap<>(ATTENTION);
        frame.put("frame", "data:image/jpeg;base64,AAAA");
        Map<String, Object> withFrame = new HashMap<>(Map.of("selected_index", 0, "response_time_ms", 1000));
        withFrame.put("attention", frame);
        for (Map<String, Object> payload : List.of(withImage, withFrame)) {
            assertThat(postJson("/attempts/" + attemptId + "/answer", payload, student).status()).isEqualTo(422);
        }
        assertThat(attempts.findById(attemptId).orElseThrow().getAnsweredAt()).isNull(); // nothing was stored
    }

    // test_api_schema_has_no_image_or_binary_fields: see ApiShapeTest (the Java
    // server has no /openapi.json, so that test inspects the endpoints directly).

    // test_hint_marks_the_attempt
    @Test
    void hintMarksTheAttempt() {
        Account student = register();
        long attemptId = firstAttempt(student);
        Response res = getPath("/attempts/" + attemptId + "/hint", student);
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.json().get("hint").asString()).isNotEmpty();
        assertThat(attempts.findById(attemptId).orElseThrow().isHintUsed()).isTrue();
    }

    // test_starting_a_new_mission_abandons_the_old_one
    @Test
    void startingANewMissionAbandonsTheOldOne() {
        Account student = register();
        long first = start(student).json().get("session").get("id").asLong();
        start(student);
        assertThat(playSessions.findById(first).orElseThrow().getStatus()).isEqualTo("abandoned");
    }

    // ── Java-only checks of the answer request ───────────────────────────────

    @Test
    void outOfRangeAndFractionalValuesAreRejected() {
        Account student = register();
        long attemptId = firstAttempt(student);
        String path = "/attempts/" + attemptId + "/answer";
        assertThat(postJson(path, Map.of("selected_index", 4, "response_time_ms", 1000), student).status())
                .isEqualTo(422);
        assertThat(postJson(path, Map.of("selected_index", 1.5, "response_time_ms", 1000), student).status())
                .isEqualTo(422);
        assertThat(postJson(path, Map.of("selected_index", 0), student).status()).isEqualTo(422);
        Map<String, Object> badAttention = new HashMap<>(ATTENTION);
        badAttention.put("on_screen_pct", 101);
        Response res = postJson(path, Map.of("selected_index", 0, "response_time_ms", 1000,
                "attention", badAttention), student);
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.json().get("detail").get(0).get("loc").toString())
                .isEqualTo("[\"body\",\"attention\",\"on_screen_pct\"]");
    }

    @Test
    void missingBlinkRateStaysNull() {
        Account student = register();
        long attemptId = firstAttempt(student);
        Map<String, Object> noBlink = new HashMap<>(ATTENTION);
        noBlink.put("blink_rate_per_min", null);
        play(student, attemptId, true, Map.of("attention", noBlink));
        Attempt row = attempts.findById(attemptId).orElseThrow();
        assertThat(row.getBlinkRatePerMin()).isNull();
        assertThat(row.getOnScreenPct()).isEqualTo(88.0);
    }

    @Test
    void attemptIdMustBeANumber() {
        Account student = register();
        Response res = getPath("/attempts/abc/hint", student);
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.json().get("detail").get(0).get("loc").toString()).isEqualTo("[\"path\",\"attempt_id\"]");
    }
}
