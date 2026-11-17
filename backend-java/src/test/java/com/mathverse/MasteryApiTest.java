package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mathverse.model.Attempt;
import com.mathverse.model.TopicMastery;
import com.mathverse.service.Mastery;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Port of backend/tests/test_mastery.py, the tests that go through the API. */
class MasteryApiTest extends ApiTest {

    private JsonNode firstQuestion(Account student, String bossId) {
        Response res = start(student, bossId);
        assertThat(res.status()).as(res.text()).isEqualTo(201);
        return res.json().get("question");
    }

    private Double masteryOf(long studentId, String topic) {
        return masteries.findByStudentIdAndTopic(studentId, topic).map(TopicMastery::getPMastery).orElse(null);
    }

    // ── T7: through the API ──────────────────────────────────────────────────

    // test_first_question_is_easy_and_streaks_move_the_difficulty
    @Test
    void firstQuestionIsEasyAndStreaksMoveTheDifficulty() {
        Account student = register();
        JsonNode question = firstQuestion(student, "goblin");
        assertThat(question.get("difficulty").asString()).isEqualTo("easy");

        for (int i = 0; i < 5; i++) { // five right in a row
            question = play(student, question.get("attempt_id").asLong(), true).get("next_question");
        }
        assertThat(question.get("difficulty").asString()).isEqualTo("hard");

        for (int i = 0; i < 5; i++) { // then five wrong in a row
            question = play(student, question.get("attempt_id").asLong(), false).get("next_question");
        }
        assertThat(question.get("difficulty").asString()).isEqualTo("easy");
    }

    // test_each_topic_has_its_own_mastery
    @Test
    void eachTopicHasItsOwnMastery() {
        Account student = register();
        winMission(student); // unlocks doc-ock

        Double algebraBefore = masteryOf(student.id(), "linear_equations");
        assertThat(algebraBefore).isGreaterThan(0.9);
        assertThat(masteryOf(student.id(), "area_perimeter")).isNull(); // untouched until it is played

        JsonNode question = firstQuestion(student, "doc-ock");
        assertThat(question.get("difficulty").asString()).isEqualTo("easy"); // strong algebra does not make geometry start hard
        play(student, question.get("attempt_id").asLong(), false);

        assertThat(masteryOf(student.id(), "area_perimeter")).isLessThan(Mastery.PRIOR);
        assertThat(masteryOf(student.id(), "linear_equations")).isEqualTo(algebraBefore); // a geometry mistake leaves algebra alone
    }

    // test_mastery_before_and_after_are_recorded_on_each_attempt
    @Test
    void masteryBeforeAndAfterAreRecordedOnEachAttempt() {
        Account student = register();
        long attemptId = firstQuestion(student, "goblin").get("attempt_id").asLong();
        JsonNode body = play(student, attemptId, true);
        Attempt attempt = attempts.findById(attemptId).orElseThrow();
        assertThat(attempt.getMasteryBefore()).isEqualTo(Mastery.PRIOR);
        assertThat(attempt.getMasteryAfter()).isEqualTo(body.get("mastery").asDouble())
                .isEqualTo(Mastery.updateMastery(Mastery.PRIOR, true));
    }

    // ── T6: attention never changes anything academic ────────────────────────

    static final List<Boolean> ANSWERS = List.of(true, true, false, true, false, false, true, true, false, true);

    static Map<String, Object> attention(Object face, Object onScreen, Object blink) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("face_present_pct", face);
        m.put("on_screen_pct", onScreen);
        m.put("blink_rate_per_min", blink);
        m.put("sample_seconds", 8);
        return m;
    }

    // test_same_answers_give_identical_academic_results_whatever_the_attention
    @Test
    void sameAnswersGiveIdenticalAcademicResultsWhateverTheAttention() {
        Map<String, Map<String, Object>> cases = new LinkedHashMap<>();
        cases.put("no camera", null);
        cases.put("fully attentive", attention(100, 100, 15));
        cases.put("looking away", attention(100, 5, 40));
        cases.put("left the room", attention(0, 0, null));
        Map<String, Set<String>> expectedLabels = Map.of("no camera", new HashSet<>(java.util.Collections.singleton(null)),
                "fully attentive", Set.of("Focused"), "looking away", Set.of("Distracted"), "left the room", Set.of("Away"));

        Map<String, List<Object>> outcomes = new LinkedHashMap<>();
        for (var entry : cases.entrySet()) {
            Account student = register();
            JsonNode question = firstQuestion(student, "goblin");
            List<Object> trace = new ArrayList<>();
            for (boolean isCorrect : ANSWERS) {
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("attention", entry.getValue()); // null = camera not used
                JsonNode body = play(student, question.get("attempt_id").asLong(), isCorrect, extra);
                question = body.get("next_question");
                trace.add(List.of(body.get("correct"), body.get("points_awarded"), body.get("mastery"),
                        question.get("difficulty"), body.get("session").get("streak"),
                        body.get("progress").get("points"), body.get("progress").get("level"),
                        body.get("progress").get("unlocked_bosses")));
            }
            double stored = masteries.findByStudentId(student.id()).getFirst().getPMastery();
            Set<String> labels = new HashSet<>();
            for (Attempt a : attempts.findByStudentIdOrderById(student.id())) {
                if (a.getAnsweredAt() != null) {
                    labels.add(a.getAttentionLabel());
                }
            }
            trace.add(stored);
            outcomes.put(entry.getKey(), trace);
            // the attention itself was stored as given, so this is a real comparison
            assertThat(labels).as(entry.getKey()).isEqualTo(expectedLabels.get(entry.getKey()));
        }

        List<Object> baseline = outcomes.get("no camera");
        for (var outcome : outcomes.entrySet()) {
            assertThat(Objects.equals(outcome.getValue(), baseline))
                    .as("attention case '%s' changed an academic result", outcome.getKey()).isTrue();
        }
    }
}
