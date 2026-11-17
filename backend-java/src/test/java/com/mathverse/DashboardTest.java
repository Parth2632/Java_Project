package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mathverse.dto.ReportDtos.DailyPoint;
import com.mathverse.dto.ReportDtos.StudentReport;
import com.mathverse.model.Attempt;
import com.mathverse.model.PlaySession;
import com.mathverse.model.Times;
import com.mathverse.model.TopicMastery;
import com.mathverse.model.User;
import com.mathverse.service.ReportBuilder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Port of backend/tests/test_dashboard.py.
 * T11: every dashboard statistic matches the stored records.
 * T12 (dashboard half): several children under one parent get separate, correct reports.
 *
 * The dataset below is written straight into the database with known values, and
 * every expected number is worked out by hand in the comments.
 */
class DashboardTest extends ApiTest {

    static final LocalDateTime NOON_TODAY = Times.utcnow().withHour(12).withMinute(0).withSecond(0).withNano(0);

    @Autowired ReportBuilder reportBuilder;

    /** Adds answered questions on one day. onScreen gives camera values for the first few. */
    void addAttempts(long studentId, long sessionId, String topic, int daysAgo, int[] outcomes, int seconds,
                     int[] onScreen, int hints, Double masteryAfter) {
        for (int i = 0; i < outcomes.length; i++) {
            boolean measured = i < onScreen.length;
            boolean isCorrect = outcomes[i] == 1;
            Attempt a = new Attempt(sessionId, studentId, topic, "medium", "q", "[\"1\",\"2\",\"3\",\"4\"]", 0, "");
            a.setSelectedIndex(isCorrect ? 0 : 1);
            a.setIsCorrect(isCorrect);
            a.setResponseTimeMs(seconds * 1000);
            a.setHintUsed(i < hints);
            a.setPointsAwarded(isCorrect ? 50 : 5);
            a.setMasteryAfter(masteryAfter);
            a.setFacePresentPct(measured ? 100.0 : null);
            a.setOnScreenPct(measured ? (double) onScreen[i] : null);
            a.setAttentionScore(measured ? onScreen[i] / 100.0 : null);
            a.setAttentionLabel(measured ? "Focused" : null);
            a.setAnsweredAt(NOON_TODAY.minusDays(daysAgo).plusMinutes(i));
            attempts.save(a);
        }
    }

    Account knownStudent;

    /**
     * 15 answers over three days:
     *   9 days ago  algebra   6 answers, 3 correct, 30s each, no camera
     *   2 days ago  algebra   5 answers, 4 correct, 60s each, camera on for 2 (80% and 60%)
     *   today       geometry  4 answers, 1 correct, 15s each, camera on for 1 (40%), 2 hints
     */
    @BeforeEach
    void knownStudent() {
        knownStudent = register("student", "Known Kid");
        long id = knownStudent.id();
        PlaySession won = new PlaySession(id, "goblin", "linear_equations");
        won.setStatus("victory");
        won.setCorrectCount(7);
        won.setWrongCount(1);
        PlaySession lost = new PlaySession(id, "doc-ock", "area_perimeter");
        lost.setStatus("defeat");
        lost.setCorrectCount(1);
        lost.setWrongCount(7);
        playSessions.save(won);
        playSessions.save(lost);
        addAttempts(id, won.getId(), "linear_equations", 9, new int[] {1, 1, 0, 0, 0, 1}, 30, new int[0], 0, 0.50);
        addAttempts(id, won.getId(), "linear_equations", 2, new int[] {1, 1, 1, 1, 0}, 60, new int[] {80, 60}, 0,
                0.82);
        addAttempts(id, lost.getId(), "area_perimeter", 0, new int[] {0, 0, 1, 0}, 15, new int[] {40}, 2, 0.20);
        TopicMastery algebra = new TopicMastery(id, "linear_equations", 0.82);
        algebra.setAttemptsCount(11);
        TopicMastery geometry = new TopicMastery(id, "area_perimeter", 0.20);
        geometry.setAttemptsCount(4);
        masteries.save(algebra);
        masteries.save(geometry);
        User user = users.findById(id).orElseThrow();
        user.setPoints(430);
        user.setLevel(2);
        users.save(user);
    }

    JsonNode getReport(Account viewer, long studentId) {
        Response res = getPath("/students/" + studentId + "/report", viewer);
        assertThat(res.status()).as(res.text()).isEqualTo(200);
        return res.json();
    }

    static JsonNode day(JsonNode report, int daysAgo) {
        String wanted = NOON_TODAY.minusDays(daysAgo).toLocalDate().toString();
        for (JsonNode d : report.get("daily")) {
            if (d.get("date").asString().equals(wanted)) {
                return d;
            }
        }
        throw new AssertionError("no day " + wanted);
    }

    static Map<String, String> values(JsonNode node, String... names) {
        Map<String, String> picked = new LinkedHashMap<>();
        for (String name : names) {
            picked.put(name, node.get(name).toString());
        }
        return picked;
    }

    // ── T11 ──────────────────────────────────────────────────────────────────

    // test_totals_match_the_stored_answers
    @Test
    void totalsMatchTheStoredAnswers() {
        JsonNode r = getReport(knownStudent, knownStudent.id());
        assertThat(r.get("questions_answered").asInt()).isEqualTo(15);
        assertThat(r.get("correct_answers").asInt()).isEqualTo(8); // 3 + 4 + 1
        assertThat(r.get("accuracy_pct").asInt()).isEqualTo(53); // 8 / 15
        assertThat(r.get("practice_minutes").asInt()).isEqualTo(9); // (6*30 + 5*60 + 4*15) s = 540 s
        assertThat(values(r, "missions_played", "missions_won", "bosses_defeated", "total_bosses").values())
                .containsExactly("2", "1", "1", "6");
        assertThat(r.get("points").asInt()).isEqualTo(430);
        assertThat(r.get("level").asInt()).isEqualTo(2);
        assertThat(r.get("last_active").asString()).startsWith(NOON_TODAY.toLocalDate().toString());
    }

    // test_daily_trend_matches_the_stored_answers
    @Test
    void dailyTrendMatchesTheStoredAnswers() {
        JsonNode r = getReport(knownStudent, knownStudent.id());
        assertThat(r.get("daily").size()).isEqualTo(ReportBuilder.TREND_DAYS);
        List<String> dates = new ArrayList<>();
        r.get("daily").forEach(d -> dates.add(d.get("date").asString()));
        assertThat(dates).isSorted(); // oldest first

        assertThat(values(day(r, 9), "questions", "correct", "accuracy_pct", "practice_minutes").values())
                .containsExactly("6", "3", "50", "3.0");
        assertThat(values(day(r, 2), "questions", "correct", "accuracy_pct", "practice_minutes").values())
                .containsExactly("5", "4", "80", "5.0");
        assertThat(values(day(r, 0), "questions", "correct", "accuracy_pct", "practice_minutes").values())
                .containsExactly("4", "1", "25", "1.0");

        JsonNode quiet = day(r, 5); // a day with no practice is shown as empty, not as 0% accuracy
        assertThat(quiet.get("questions").asInt()).isZero();
        assertThat(quiet.get("accuracy_pct").isNull()).isTrue();
        assertThat(quiet.get("practice_minutes").asDouble()).isZero();
        int sum = 0;
        for (JsonNode d : r.get("daily")) {
            sum += d.get("questions").asInt();
        }
        assertThat(sum).isEqualTo(15);
    }

    // test_topic_mastery_and_history_match_the_stored_values
    @Test
    void topicMasteryAndHistoryMatchTheStoredValues() {
        JsonNode r = getReport(knownStudent, knownStudent.id());
        Map<String, JsonNode> byTopic = new LinkedHashMap<>();
        r.get("topic_mastery").forEach(t -> byTopic.put(t.get("topic").asString(), t));
        assertThat(values(byTopic.get("linear_equations"), "mastery_pct", "questions_answered").values())
                .containsExactly("82", "11");
        assertThat(values(byTopic.get("area_perimeter"), "mastery_pct", "questions_answered").values())
                .containsExactly("20", "4");
        assertThat(byTopic.get("percentages").get("mastery_pct").isNull()).isTrue(); // not started: unknown, not 0%
        assertThat(byTopic.get("percentages").get("mastery_band").isNull()).isTrue();

        // The band in words, and the plain record shown beside the estimate.
        assertThat(byTopic.get("linear_equations").get("mastery_band").asString()).isEqualTo("strong");
        assertThat(byTopic.get("area_perimeter").get("mastery_band").asString()).isEqualTo("needs_practice");
        assertThat(byTopic.get("linear_equations").get("correct_answers").asInt()).isEqualTo(7); // 3 + 4
        assertThat(byTopic.get("area_perimeter").get("correct_answers").asInt()).isEqualTo(1);

        List<String> needsPractice = new ArrayList<>();
        r.get("needs_practice").forEach(t -> needsPractice.add(t.get("topic").asString()));
        assertThat(needsPractice).containsExactly("area_perimeter"); // 20% is below 40%

        Map<String, List<Integer>> history = new LinkedHashMap<>();
        for (JsonNode h : r.get("mastery_history")) {
            List<Integer> points = new ArrayList<>();
            h.get("points").forEach(p -> points.add(p.get("mastery_pct").asInt()));
            history.put(h.get("topic").asString(), points);
        }
        assertThat(history).isEqualTo(Map.of("linear_equations", List.of(50, 82), "area_perimeter", List.of(20)));
    }

    // test_attention_is_averaged_only_over_measured_questions
    @Test
    void attentionIsAveragedOnlyOverMeasuredQuestions() {
        JsonNode r = getReport(knownStudent, knownStudent.id());
        // Measured on 3 of 15 questions: (80 + 60 + 40) / 3 = 60. If the 12 unmeasured
        // questions were wrongly counted as zero this would be 12.
        assertThat(r.get("attention").toString())
                .isEqualTo("{\"measured_questions\":3,\"total_questions\":15,\"avg_on_screen_pct\":60}");
        assertThat(values(day(r, 2), "attention_measured", "avg_on_screen_pct").values()).containsExactly("2", "70");
        assertThat(values(day(r, 0), "attention_measured", "avg_on_screen_pct").values()).containsExactly("1", "40");
        assertThat(values(day(r, 9), "attention_measured", "avg_on_screen_pct").values())
                .containsExactly("0", "null"); // camera not used
    }

    // test_observations_are_rule_based_statements_of_the_numbers
    @Test
    void observationsAreRuleBasedStatementsOfTheNumbers() {
        JsonNode r = getReport(knownStudent, knownStudent.id());
        List<String> observations = new ArrayList<>();
        r.get("observations").forEach(o -> observations.add(o.asString()));
        assertThat(observations).containsExactly(
                "Practised on 2 of the last 7 days.",
                // last 7 days: 5 of 9 correct = 56%; the 7 days before: 3 of 6 = 50%
                "Accuracy over the last 7 days was 56%, up from 50% the week before.",
                "Algebra (Linear Equations) is a strength (estimated mastery 82%).");
        // Attention never appears in, or explains, anything academic.
        String text = String.join(" ", observations).toLowerCase();
        for (String word : List.of("attention", "camera", "focus", "screen", "distract")) {
            assertThat(text).doesNotContain(word);
        }
    }

    // test_badges_are_earned_from_stored_missions_only
    @Test
    void badgesAreEarnedFromStoredMissionsOnly() {
        JsonNode r = getReport(knownStudent, knownStudent.id());
        List<String> ids = new ArrayList<>();
        r.get("badges").forEach(b -> ids.add(b.get("id").asString()));
        assertThat(ids).containsExactly("defeated-goblin"); // one win, with a wrong answer in it
    }

    // test_recent_answers_are_the_latest_stored_answers
    @Test
    void recentAnswersAreTheLatestStoredAnswers() {
        JsonNode recent = getReport(knownStudent, knownStudent.id()).get("recent_attempts");
        assertThat(recent.size()).isEqualTo(10);
        List<Boolean> correct = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            correct.add(recent.get(i).get("is_correct").asBoolean());
            labels.add(recent.get(i).get("attention_label").isNull() ? null
                    : recent.get(i).get("attention_label").asString());
            assertThat(recent.get(i).get("topic_label").asString()).isEqualTo("Geometry (Area & Perimeter)");
        }
        assertThat(correct).containsExactly(false, true, false, false); // today's, newest first
        assertThat(labels).containsExactly(null, null, null, "Focused");
    }

    // test_a_student_with_no_history_gets_an_empty_report
    @Test
    void aStudentWithNoHistoryGetsAnEmptyReport() {
        Account student = register("student", "Brand New");
        JsonNode r = getReport(student, student.id());
        assertThat(r.get("accuracy_pct").isNull()).isTrue();
        assertThat(r.get("last_active").isNull()).isTrue();
        for (String list : List.of("needs_practice", "mastery_history", "observations", "badges", "recent_attempts")) {
            assertThat(r.get(list).size()).as(list).isZero();
        }
        assertThat(r.get("daily").size()).isEqualTo(ReportBuilder.TREND_DAYS);
        for (JsonNode d : r.get("daily")) {
            assertThat(d.get("questions").asInt()).isZero();
            assertThat(d.get("accuracy_pct").isNull()).isTrue();
            assertThat(d.get("avg_on_screen_pct").isNull()).isTrue();
        }
    }

    // test_observations_wait_until_there_is_enough_data
    @Test
    void observationsWaitUntilThereIsEnoughData() {
        Account student = register();
        JsonNode question = start(student).json().get("question");
        for (int i = 0; i < 4; i++) {
            question = play(student, question.get("attempt_id").asLong(), true).get("next_question");
        }
        assertThat(getReport(student, student.id()).get("observations").size()).isZero();
    }

    // test_days_follow_the_viewers_clock
    /** An answer at 23:30 UTC belongs to the next day for a viewer five and a half hours ahead. */
    @Test
    void daysFollowTheViewersClock() {
        Account student = register();
        PlaySession session = playSessions.save(new PlaySession(student.id(), "goblin", "linear_equations"));
        Attempt a = new Attempt(session.getId(), student.id(), "linear_equations", "medium", "q", "[]", 0, "");
        a.setSelectedIndex(0);
        a.setIsCorrect(true);
        a.setResponseTimeMs(1000);
        a.setAnsweredAt(LocalDateTime.of(2026, 3, 10, 23, 30));
        attempts.save(a);
        User user = users.findById(student.id()).orElseThrow();
        LocalDateTime now = LocalDateTime.of(2026, 3, 11, 6, 0);
        StudentReport utc = reportBuilder.build(user, 0, now);
        StudentReport india = reportBuilder.build(user, 330, now);

        assertThat(utc.daily().stream().filter(d -> d.questions() > 0).map(DailyPoint::date))
                .containsExactly("2026-03-10");
        assertThat(india.daily().stream().filter(d -> d.questions() > 0).map(DailyPoint::date))
                .containsExactly("2026-03-11");
    }

    // ── T10 and T12 on the dashboard endpoints ───────────────────────────────

    // test_parent_sees_each_child_separately
    @Test
    void parentSeesEachChildSeparately() {
        Account parent = register("parent");
        Account sibling = register("student", "Quiet Sibling");
        for (Account child : List.of(knownStudent, sibling)) {
            Response res = postJson("/guardians/links",
                    Map.of("link_code", child.user().get("link_code").asString()), parent);
            assertThat(res.status()).isEqualTo(201);
        }

        Map<String, JsonNode> listed = new LinkedHashMap<>();
        getPath("/guardians/students", parent).json().forEach(s -> listed.put(s.get("name").asString(), s));
        assertThat(listed.get("Known Kid").get("questions_answered").asInt()).isEqualTo(15);
        assertThat(listed.get("Known Kid").get("accuracy_pct").asInt()).isEqualTo(53);
        assertThat(listed.get("Known Kid").get("last_active").isNull()).isFalse();
        assertThat(listed.get("Quiet Sibling").get("questions_answered").asInt()).isZero();
        assertThat(listed.get("Quiet Sibling").get("accuracy_pct").isNull()).isTrue();
        assertThat(listed.get("Quiet Sibling").get("last_active").isNull()).isTrue();

        assertThat(getReport(parent, knownStudent.id()).get("questions_answered").asInt()).isEqualTo(15);
        assertThat(getReport(parent, sibling.id()).get("questions_answered").asInt()).isZero();
    }

    // test_teacher_sees_linked_students_and_nobody_else
    @Test
    void teacherSeesLinkedStudentsAndNobodyElse() {
        Account teacher = register("teacher");
        Account outsider = register("student", "Not In Class");
        postJson("/guardians/links", Map.of("link_code", knownStudent.user().get("link_code").asString()), teacher);

        List<String> names = new ArrayList<>();
        getPath("/guardians/students", teacher).json().forEach(s -> names.add(s.get("name").asString()));
        assertThat(names).containsExactly("Known Kid");
        assertThat(getReport(teacher, knownStudent.id()).get("student_name").asString()).isEqualTo("Known Kid");
        for (String path : List.of("/students/" + outsider.id() + "/report",
                "/students/" + outsider.id() + "/report?tz_offset_minutes=330")) {
            assertThat(getPath(path, teacher).status()).isEqualTo(403);
        }
    }

    // test_report_rejects_a_nonsense_clock_offset
    @Test
    void reportRejectsANonsenseClockOffset() {
        Response res = getPath("/students/" + knownStudent.id() + "/report?tz_offset_minutes=99999", knownStudent);
        assertThat(res.status()).isEqualTo(422);
    }
}
