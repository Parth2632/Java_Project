package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Port of backend/tests/test_reports.py. */
class ReportsTest extends ApiTest {

    // test_new_student_report_is_empty_not_invented
    @Test
    void newStudentReportIsEmptyNotInvented() {
        Account student = register("student", "Newcomer");
        JsonNode r = getPath("/students/" + student.id() + "/report", student).json();

        assertThat(r.get("student_name").asString()).isEqualTo("Newcomer");
        assertThat(r.get("is_demo").asBoolean()).isFalse();
        assertThat(r.get("points").asInt()).isZero();
        assertThat(r.get("level").asInt()).isEqualTo(1);
        assertThat(r.get("questions_answered").asInt()).isZero();
        assertThat(r.get("correct_answers").asInt()).isZero();
        assertThat(r.get("accuracy_pct").isNull()).isTrue(); // unknown, not 0% and not a sample value
        assertThat(r.get("practice_minutes").asInt()).isZero();
        assertThat(r.get("missions_won").asInt()).isZero();
        assertThat(r.get("bosses_defeated").asInt()).isZero();
        assertThat(r.get("recent_attempts").size()).isZero();
        assertThat(r.get("topic_mastery").size()).isEqualTo(6);
        for (JsonNode t : r.get("topic_mastery")) {
            assertThat(t.get("mastery_pct").isNull()).isTrue();
            assertThat(t.get("questions_answered").asInt()).isZero();
        }
        assertThat(r.get("attention").toString())
                .isEqualTo("{\"measured_questions\":0,\"total_questions\":0,\"avg_on_screen_pct\":null}");
    }

    // test_report_numbers_match_what_was_played
    @Test
    void reportNumbersMatchWhatWasPlayed() {
        Account student = register();
        long attemptId = start(student).json().get("question").get("attempt_id").asLong();
        List<Boolean> outcomes = List.of(true, false, true, true);
        int points = 0;
        for (int i = 0; i < outcomes.size(); i++) {
            Map<String, Object> extra = new LinkedHashMap<>();
            if (i < 2) {
                extra.put("attention", Map.of("face_present_pct", 100, "on_screen_pct", 60 + 20 * i,
                        "sample_seconds", 5));
            } else {
                extra.put("attention", null);
            }
            JsonNode body = play(student, attemptId, outcomes.get(i), extra);
            points += body.get("points_awarded").asInt();
            attemptId = body.get("next_question").get("attempt_id").asLong();
        }

        JsonNode r = getPath("/students/" + student.id() + "/report", student).json();
        assertThat(r.get("questions_answered").asInt()).isEqualTo(4);
        assertThat(r.get("correct_answers").asInt()).isEqualTo(3);
        assertThat(r.get("accuracy_pct").asInt()).isEqualTo(75);
        assertThat(r.get("points").asInt()).isEqualTo(points);
        assertThat(r.get("missions_won").asInt()).isZero();
        assertThat(r.get("missions_played").asInt()).isZero(); // still in progress
        List<Boolean> recentCorrect = new ArrayList<>();
        r.get("recent_attempts").forEach(a -> recentCorrect.add(a.get("is_correct").asBoolean()));
        assertThat(recentCorrect).containsExactlyElementsOf(outcomes.reversed());

        JsonNode algebra = null;
        int answeredSum = 0;
        for (JsonNode t : r.get("topic_mastery")) {
            answeredSum += t.get("questions_answered").asInt();
            if (t.get("topic").asString().equals("linear_equations")) {
                algebra = t;
            }
        }
        assertThat(algebra.get("questions_answered").asInt()).isEqualTo(4);
        assertThat(algebra.get("mastery_pct").isNull()).isFalse();
        assertThat(answeredSum).isEqualTo(4);

        // attention: averaged over the two measured questions only (60 and 80), the
        // two unmeasured ones are counted as unmeasured rather than as zero
        assertThat(r.get("attention").toString())
                .isEqualTo("{\"measured_questions\":2,\"total_questions\":4,\"avg_on_screen_pct\":70}");
        List<String> labels = new ArrayList<>();
        r.get("recent_attempts").forEach(a -> labels.add(a.get("attention_label").isNull() ? null
                : a.get("attention_label").asString()));
        assertThat(labels).containsExactly(null, null, "Focused", "Partly focused");
    }
}
