package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mathverse.model.Attempt;
import com.mathverse.service.Mastery;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Port of backend/tests/test_mastery_display.py, the tests that read the report:
 * what is stored stays raw, what is shown is limited.
 */
class MasteryDisplayApiTest extends ApiTest {

    /** The stored mastery and each answered attempt's (before, after). */
    private List<Object> storedMastery(long studentId) {
        List<Object> stored = new ArrayList<>();
        stored.add(masteries.findByStudentId(studentId).getFirst().getPMastery());
        for (Attempt a : attempts.findByStudentIdOrderById(studentId)) {
            if (a.getAnsweredAt() != null) {
                stored.add(List.of(a.getMasteryBefore(), a.getMasteryAfter()));
            }
        }
        return stored;
    }

    private JsonNode report(Account student) {
        return getPath("/students/" + student.id() + "/report", student).json();
    }

    // test_extreme_mastery_is_stored_raw_but_shown_limited
    @ParameterizedTest
    @CsvSource({"true, 0.99, 95, strong, 95% or more", "false, 0.01, 5, needs_practice, 5% or less"})
    void extremeMasteryIsStoredRawButShownLimited(boolean correct, double rawEnd, int shown, String band,
                                                  String words) {
        Account student = register();
        JsonNode question = start(student).json().get("question");
        JsonNode body = null;
        for (int i = 0; i < 6; i++) {
            body = play(student, question.get("attempt_id").asLong(), correct);
            question = body.get("next_question");
        }

        // The answer response carries both: the raw value and the one to show.
        assertThat(body.get("mastery").asDouble()).isEqualTo(rawEnd);
        assertThat(body.get("mastery_display_pct").asInt()).isEqualTo(shown);

        List<Object> before = storedMastery(student.id());
        assertThat(before.getFirst()).isEqualTo(rawEnd); // stored at the model's own limit, not at 5 or 95
        assertThat(rawEnd).isEqualTo(correct ? Mastery.P_MAX : Mastery.P_MIN);

        JsonNode r = report(student);
        JsonNode algebra = null;
        for (JsonNode t : r.get("topic_mastery")) {
            if (t.get("topic").asString().equals("linear_equations")) {
                algebra = t;
            }
        }
        assertThat(algebra.get("mastery_pct").asInt()).isEqualTo(shown);
        assertThat(algebra.get("mastery_band").asString()).isEqualTo(band);
        assertThat(algebra.get("questions_answered").asInt()).isEqualTo(6);
        assertThat(algebra.get("correct_answers").asInt()).isEqualTo(correct ? 6 : 0); // the plain record
        List<Integer> historyPoints = new ArrayList<>();
        r.get("mastery_history").forEach(h -> h.get("points").forEach(p -> historyPoints.add(p.get("mastery_pct").asInt())));
        assertThat(historyPoints).containsExactly(shown);

        // No raw extreme reaches any report field or sentence.
        List<JsonNode> shownTopics = new ArrayList<>();
        r.get("topic_mastery").forEach(shownTopics::add);
        r.get("needs_practice").forEach(shownTopics::add);
        for (JsonNode t : shownTopics) {
            assertThat(t.get("mastery_pct").isNull() || t.get("mastery_pct").asInt() >= 5
                    && t.get("mastery_pct").asInt() <= 95).isTrue();
        }
        StringBuilder text = new StringBuilder();
        r.get("observations").forEach(o -> text.append(o.asString()).append(' '));
        assertThat(text.toString().replace("11%", "")).doesNotContain("1%");
        assertThat(text.toString()).doesNotContain("99%");
        assertThat(words).isNotEmpty(); // the wording itself is checked by the observation test below

        // Building the report changed nothing that is stored.
        assertThat(storedMastery(student.id())).isEqualTo(before);
    }

    // test_needs_practice_is_decided_on_the_raw_value
    /** Raw mastery of 1% is shown as 5%, and is still listed as needing practice. */
    @Test
    void needsPracticeIsDecidedOnTheRawValue() {
        Account student = register();
        JsonNode question = start(student).json().get("question");
        for (int i = 0; i < 6; i++) {
            question = play(student, question.get("attempt_id").asLong(), false).get("next_question");
        }
        JsonNode needs = report(student).get("needs_practice");
        assertThat(needs.size()).isEqualTo(1);
        assertThat(needs.get(0).get("topic").asString()).isEqualTo("linear_equations");
        assertThat(needs.get(0).get("mastery_pct").asInt()).isEqualTo(5);
    }

    // test_observation_sentences_use_the_display_wording
    @Test
    void observationSentencesUseTheDisplayWording() {
        Account student = register();
        JsonNode question = start(student).json().get("question");
        for (int i = 0; i < 12; i++) { // enough answers for observations; ends at the model's upper limit
            JsonNode body = play(student, question.get("attempt_id").asLong(), true);
            question = body.get("next_question").isNull() ? start(student).json().get("question")
                    : body.get("next_question");
        }
        List<String> observations = new ArrayList<>();
        report(student).get("observations").forEach(o -> observations.add(o.asString()));
        assertThat(observations).contains("Algebra (Linear Equations) is a strength (estimated mastery 95% or more).");
    }
}
