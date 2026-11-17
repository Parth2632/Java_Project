package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mathverse.model.Attempt;
import com.mathverse.service.VerifyAttemptsCommand;
import com.mathverse.service.VerifyAttemptsCommand.Check;
import com.mathverse.service.VerifyAttemptsCommand.Sent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** The camera check's row check (Python: tools/verify_attempts.py, which had no tests). */
class VerifyAttemptsTest extends ApiTest {

    @Autowired VerifyAttemptsCommand verify;

    /** Answers four questions, the first two with camera numbers. Returns what the browser would report. */
    private List<Sent> playFour() {
        Account student = register();
        long attemptId = start(student).json().get("question").get("attempt_id").asLong();
        List<Sent> sent = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> extra = new LinkedHashMap<>();
            boolean measured = i < 2;
            extra.put("attention", measured ? Map.of("face_present_pct", 100, "on_screen_pct", 70,
                    "sample_seconds", 5) : null);
            JsonNode body = play(student, attemptId, i % 2 == 0, extra);
            sent.add(new Sent(attemptId, measured));
            attemptId = body.get("next_question").get("attempt_id").asLong();
        }
        return sent;
    }

    @Test
    void realAnswersPassEveryCheck() {
        List<Check> checks = verify.check(playFour());
        assertThat(checks).hasSize(4).allMatch(Check::pass);
        assertThat(checks.get(0).detail()).isEqualTo("4 rows");
        assertThat(checks.get(1).detail()).isEqualTo("2 rows");
        assertThat(checks.get(2).detail()).isEqualTo("2 rows");
        assertThat(checks.get(3).detail()).isEqualTo("4 rows recomputed");
    }

    @Test
    void eachKindOfProblemIsReported() {
        List<Sent> sent = new ArrayList<>(playFour());
        Attempt measured = attempts.findById(sent.get(0).id()).orElseThrow();
        measured.setOnScreenPct(null); // camera on, but nothing stored
        attempts.save(measured);
        Attempt unmeasured = attempts.findById(sent.get(2).id()).orElseThrow();
        unmeasured.setOnScreenPct(0.0); // camera off, but 0 stored instead of NULL
        unmeasured.setMasteryAfter(0.5); // and a mastery that does not follow from the answer
        attempts.save(unmeasured);
        sent.add(new Sent(999_999, false)); // never stored

        List<Check> checks = verify.check(sent);
        assertThat(checks).noneMatch(Check::pass);
        assertThat(checks.get(0).detail()).isEqualTo("missing [999999]");
        assertThat(checks.get(1).detail()).isEqualTo("empty in [" + sent.get(0).id() + "]");
        assertThat(checks.get(2).detail()).isEqualTo("filled in [" + sent.get(2).id() + "]");
        assertThat(checks.get(3).detail()).contains(String.valueOf(sent.get(2).id()));
    }

    @Test
    void flagIsRecognised() {
        assertThat(VerifyAttemptsCommand.isVerifyRun(new String[] {"--verify-attempts", "[]"})).isTrue();
        assertThat(VerifyAttemptsCommand.isVerifyRun(new String[] {"--demo"})).isFalse();
    }
}
