package com.mathverse;

import static com.mathverse.service.Mastery.PRIOR;
import static com.mathverse.service.Mastery.P_MAX;
import static com.mathverse.service.Mastery.P_MIN;
import static com.mathverse.service.Mastery.difficultyFor;
import static com.mathverse.service.Mastery.updateMastery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mathverse.service.Mastery;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Port of backend/tests/test_mastery.py, the parts that need no API.
 * T6: academic mastery is unaffected by attention data.
 * T7: correct and incorrect answers update BKT the right way.
 * The API tests of that file (first question, each topic, mastery recorded, and
 * T6 through the API) are ported with the mission endpoints in phase 3.
 */
class MasteryTest {

    /** 0.01 … 0.99 */
    static Stream<Double> grid() {
        return IntStream.range(1, 100).mapToObj(i -> i / 100.0);
    }

    // ── T7: the model itself ─────────────────────────────────────────────────

    // test_correct_answer_never_lowers_and_normally_raises_mastery
    @ParameterizedTest
    @MethodSource("grid")
    void correctAnswerNeverLowersAndNormallyRaisesMastery(double p) {
        double after = updateMastery(p, true);
        assertThat(after).isGreaterThanOrEqualTo(p);
        if (p < 0.98) {
            assertThat(after).isGreaterThan(p);
        }
        assertThat(after).isBetween(P_MIN, P_MAX);
    }

    // test_incorrect_answer_never_raises_and_normally_lowers_mastery
    @ParameterizedTest
    @MethodSource("grid")
    void incorrectAnswerNeverRaisesAndNormallyLowersMastery(double p) {
        double after = updateMastery(p, false);
        assertThat(after).isLessThanOrEqualTo(p);
        if (p > 0.02) {
            assertThat(after).isLessThan(p);
        }
        assertThat(after).isBetween(P_MIN, P_MAX);
    }

    // test_worked_example_from_the_documentation
    @Test
    void workedExampleFromTheDocumentation() {
        assertThat(updateMastery(0.30, true)).isCloseTo(0.6657, within(1e-4));
        assertThat(updateMastery(0.30, false)).isCloseTo(0.0541, within(1e-4));
    }

    // test_textbook_bkt_would_have_raised_mastery_after_a_wrong_answer
    @Test
    void textbookBktWouldHaveRaisedMasteryAfterAWrongAnswer() {
        double p = 0.05;
        double posterior = p * Mastery.P_SLIP / (p * Mastery.P_SLIP + (1 - p) * (1 - Mastery.P_GUESS));
        double textbook = posterior + (1 - posterior) * Mastery.P_LEARN;
        assertThat(textbook).isGreaterThan(p); // the textbook value goes UP after a wrong answer
        assertThat(updateMastery(p, false)).isLessThan(p); // ours goes down
    }

    // test_mastery_stays_inside_its_limits_under_long_streaks
    @Test
    void masteryStaysInsideItsLimitsUnderLongStreaks() {
        double p = PRIOR;
        for (int i = 0; i < 50; i++) {
            p = updateMastery(p, true);
        }
        assertThat(p).isEqualTo(P_MAX);
        for (int i = 0; i < 50; i++) {
            p = updateMastery(p, false);
        }
        assertThat(p).isEqualTo(P_MIN);
    }

    // test_difficulty_thresholds
    @Test
    void difficultyThresholds() {
        assertThat(Stream.of(0.01, 0.39).map(Mastery::difficultyFor)).containsExactly("easy", "easy");
        assertThat(Stream.of(0.40, 0.60, 0.75).map(Mastery::difficultyFor)).containsExactly("medium", "medium", "medium");
        assertThat(Stream.of(0.76, 0.99).map(Mastery::difficultyFor)).containsExactly("hard", "hard");
        assertThat(difficultyFor(PRIOR)).isEqualTo("easy"); // a new student starts gently
    }

    // test_mastery_functions_cannot_be_given_attention_data
    /** The separation is structural: there is no parameter to pass attention through. */
    @Test
    void masteryFunctionsCannotBeGivenAttentionData() throws NoSuchMethodException {
        Method update = Mastery.class.getMethod("updateMastery", double.class, boolean.class);
        Method difficulty = Mastery.class.getMethod("difficultyFor", double.class);
        assertThat(names(update)).containsExactly("pMastery", "isCorrect");
        assertThat(names(difficulty)).containsExactly("pMastery");
        // No other public methods that could take attention data.
        assertThat(Arrays.stream(Mastery.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(Method::getName))
                .containsExactlyInAnyOrder("updateMastery", "difficultyFor");

        String code = SourceText.serviceCode("Mastery").toLowerCase(); // the class comment is skipped
        for (String word : List.of("attention", "camera", "onscreen", "on_screen", "facepresent", "face_present",
                "blink", "responsetime", "response_time", "hint")) {
            assertThat(code).as(word).doesNotContain(word);
        }
    }

    private static List<String> names(Method method) {
        return Arrays.stream(method.getParameters()).map(Parameter::getName).toList();
    }
}
