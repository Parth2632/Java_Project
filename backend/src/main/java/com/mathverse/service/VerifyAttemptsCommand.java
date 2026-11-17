package com.mathverse.service;

import com.mathverse.model.Attempt;
import com.mathverse.model.AttemptRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks stored answer rows after a camera test run (Python: tools/verify_attempts.py).
 * frontend/scripts/camera-check.mjs runs it with the answers the browser sent:
 *
 *     java -jar target/mathverse-backend-1.0.0.jar --verify-attempts '[{"id": 12, "measured": true}, ...]'
 *
 * It reads those rows from the database and checks two things:
 *   1. Attention columns are filled only where the camera measured something,
 *      and are NULL (never 0) where it did not.
 *   2. Each row's mastery_after is exactly what the mastery model gives from
 *      mastery_before and whether the answer was correct. Attention plays no part.
 *
 * It only reads, and prints one line of JSON: a list of {check, pass, detail}.
 */
@Component
public class VerifyAttemptsCommand {

    public static final String FLAG = "--verify-attempts";

    /** One answer the browser sent: its attempt id, and whether it carried camera numbers. */
    public record Sent(long id, boolean measured) {
    }

    public record Check(String check, boolean pass, String detail) {
    }

    private final AttemptRepository attempts;
    private final JsonMapper json = JsonMapper.builder().build();

    public VerifyAttemptsCommand(AttemptRepository attempts) {
        this.attempts = attempts;
    }

    public static boolean isVerifyRun(String[] args) {
        return List.of(args).contains(FLAG);
    }

    /** Reads the JSON after the flag, runs the checks and prints them. */
    public void run(String[] args) {
        List<String> list = List.of(args);
        int at = list.indexOf(FLAG);
        if (at < 0 || at + 1 >= list.size()) {
            throw new IllegalArgumentException("Usage: " + FLAG + " '[{\"id\": 12, \"measured\": true}, ...]'");
        }
        List<Sent> sent = json.readValue(list.get(at + 1), new TypeReference<List<Sent>>() {
        });
        System.out.println(json.writeValueAsString(check(sent)));
    }

    @Transactional(readOnly = true)
    public List<Check> check(List<Sent> sent) {
        Map<Long, Attempt> rows = new HashMap<>();
        for (Attempt a : attempts.findAllById(sent.stream().map(Sent::id).toList())) {
            rows.put(a.getId(), a);
        }

        List<Long> missing = new ArrayList<>();
        List<Long> wrongNull = new ArrayList<>();
        List<Long> wrongFilled = new ArrayList<>();
        List<Long> wrongMastery = new ArrayList<>();
        for (Sent s : sent) {
            Attempt row = rows.get(s.id());
            if (row == null) {
                missing.add(s.id());
                continue;
            }
            if (s.measured()) {
                if (row.getFacePresentPct() == null || row.getOnScreenPct() == null) {
                    wrongNull.add(s.id());
                }
            } else if (row.getFacePresentPct() != null || row.getOnScreenPct() != null
                    || row.getBlinkRatePerMin() != null || row.getAttentionScore() != null
                    || row.getAttentionLabel() != null) {
                wrongFilled.add(s.id());
            }
            boolean answered = row.getMasteryBefore() != null && row.getMasteryAfter() != null
                    && row.getIsCorrect() != null;
            if (!answered || Math.abs(Mastery.updateMastery(row.getMasteryBefore(), row.getIsCorrect())
                    - row.getMasteryAfter()) > 1e-9) {
                wrongMastery.add(s.id());
            }
        }

        long measured = sent.stream().filter(Sent::measured).count();
        return List.of(
                new Check("every answer sent was stored", missing.isEmpty(),
                        missing.isEmpty() ? sent.size() + " rows" : "missing " + missing),
                new Check("rows answered with the camera on have attention numbers", wrongNull.isEmpty(),
                        wrongNull.isEmpty() ? measured + " rows" : "empty in " + wrongNull),
                new Check("rows answered without the camera are NULL in all attention columns, never 0",
                        wrongFilled.isEmpty(),
                        wrongFilled.isEmpty() ? (sent.size() - measured) + " rows" : "filled in " + wrongFilled),
                new Check("mastery after each answer follows from correctness alone", wrongMastery.isEmpty(),
                        wrongMastery.isEmpty() ? sent.size() + " rows recomputed" : "differs in " + wrongMastery));
    }
}
