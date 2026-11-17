package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mathverse.service.Curriculum;
import com.mathverse.service.GeneratedQuestion;
import com.mathverse.service.Questions;
import com.mathverse.service.Rng;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Port of backend/tests/test_questions.py.
 * Every generated question must have a correct stored answer. {@link Solver}
 * works the answer out again from the question text alone, and the tests
 * compare that with what the generator stored, for all six bosses at all three
 * tiers, hundreds of times each.
 */
class QuestionsTest {

    static final int SEEDS = 300;

    /** Every (boss, tier) pair: 18 cases. */
    static Stream<Arguments> cases() {
        return Curriculum.BOSS_IDS.stream()
                .flatMap(boss -> Questions.DIFFICULTIES.stream().map(tier -> Arguments.of(boss, tier)));
    }

    static GeneratedQuestion question(String boss, String tier, int seed) {
        return Questions.generate(boss, tier, new Rng(seed));
    }

    /** True if the hint ends by stating the answer, e.g. "... = 7." */
    static boolean statesAnswer(String hint, String answer) {
        return Pattern.compile("(is|=|equals|answer:?)\\s*" + Pattern.quote(answer) + "\\b\\.?$").matcher(hint).find();
    }

    // test_stored_answer_is_mathematically_correct
    @ParameterizedTest
    @MethodSource("cases")
    void storedAnswerIsMathematicallyCorrect(String boss, String tier) {
        for (int seed = 0; seed < SEEDS; seed++) {
            GeneratedQuestion q = question(boss, tier, seed);
            assertThat(q.options().get(q.correctIndex()))
                    .as("seed %d: %s %s", seed, q.text(), q.options())
                    .isEqualTo(String.valueOf(Solver.solve(boss, q.text())));
        }
    }

    // test_four_distinct_options_with_the_answer_exactly_once
    @ParameterizedTest
    @MethodSource("cases")
    void fourDistinctOptionsWithTheAnswerExactlyOnce(String boss, String tier) {
        Set<Integer> positions = new HashSet<>();
        for (int seed = 0; seed < SEEDS; seed++) {
            GeneratedQuestion q = question(boss, tier, seed);
            assertThat(q.options()).hasSize(4).doesNotHaveDuplicates();
            assertThat(q.correctIndex()).isBetween(0, 3);
            assertThat(q.hint()).isNotEmpty();
            assertThat(q.difficulty()).isEqualTo(tier);
            positions.add(q.correctIndex());
        }
        assertThat(positions).containsExactlyInAnyOrder(0, 1, 2, 3); // the answer is not always in the same place
    }

    // test_hint_never_states_the_answer_outright
    @ParameterizedTest
    @MethodSource("cases")
    void hintNeverStatesTheAnswerOutright(String boss, String tier) {
        for (int seed = 0; seed < SEEDS; seed++) {
            GeneratedQuestion q = question(boss, tier, seed);
            String answer = q.options().get(q.correctIndex());
            assertThat(statesAnswer(q.hint(), answer)).as("%s / %s", q.text(), q.hint()).isFalse();
        }
    }

    // test_tiers_are_genuinely_different
    /** Harder tiers use question forms that the easy tier never produces. */
    @ParameterizedTest
    @FieldSource("com.mathverse.service.Curriculum#BOSS_IDS")
    void tiersAreGenuinelyDifferent(String boss) {
        Set<String> easy = shapes(boss, "easy");
        Set<String> hard = shapes(boss, "hard");
        if (!boss.equals("doc-ock")) {
            assertThat(easy).as(boss).doesNotContainAnyElementsOf(hard);
        }
        Set<String> hardOnly = new HashSet<>(hard);
        hardOnly.removeAll(easy);
        assertThat(hardOnly).as(boss).isNotEmpty(); // hard always has at least one form of its own
    }

    private static Set<String> shapes(String boss, String tier) {
        Set<String> shapes = new HashSet<>();
        for (int seed = 0; seed < SEEDS; seed++) {
            shapes.add(question(boss, tier, seed).text().replaceAll("\\d+", "N"));
        }
        return shapes;
    }

    // test_unknown_boss_or_tier_is_an_error
    @Test
    void unknownBossOrTierIsAnError() {
        assertThatThrownBy(() -> Questions.generate("thanos", "medium")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Questions.generate("goblin", "impossible")).isInstanceOf(IllegalArgumentException.class);
    }
}
