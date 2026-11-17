package com.mathverse;

import static com.mathverse.service.MasteryDisplay.DISPLAY_MAX_PCT;
import static com.mathverse.service.MasteryDisplay.DISPLAY_MIN_PCT;
import static com.mathverse.service.MasteryDisplay.displayPct;
import static com.mathverse.service.MasteryDisplay.displayText;
import static com.mathverse.service.MasteryDisplay.masteryBand;
import static org.assertj.core.api.Assertions.assertThat;

import com.mathverse.service.Mastery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Port of backend/tests/test_mastery_display.py, the parts that need no API.
 * Mastery display is presentation only: it limits and labels what is shown, and
 * never changes what is stored or what the game decides. The five API tests of
 * that file are ported with missions (phase 3) and reports (phase 4).
 */
class MasteryDisplayTest {

    /** Every stored value from 0.010 to 0.990. */
    private static List<Double> grid() {
        List<Double> grid = new ArrayList<>();
        for (int i = 10; i < 991; i++) {
            grid.add(i / 1000.0);
        }
        return grid;
    }

    // test_displayed_percentage_always_stays_between_5_and_95
    @Test
    void displayedPercentageAlwaysStaysBetween5And95() {
        for (double p : grid()) {
            assertThat(displayPct(p)).isBetween(DISPLAY_MIN_PCT, DISPLAY_MAX_PCT);
        }
        assertThat(displayPct(Mastery.P_MIN)).isEqualTo(5);
        assertThat(displayPct(Mastery.P_MAX)).isEqualTo(95);
    }

    // test_values_inside_the_range_are_shown_as_they_are
    @Test
    void valuesInsideTheRangeAreShownAsTheyAre() {
        assertThat(Stream.of(0.05, 0.30, 0.666, 0.82, 0.95).map(p -> displayPct(p)))
                .containsExactly(5, 30, 67, 82, 95);
    }

    // test_display_never_reverses_the_order_of_two_students
    @Test
    void displayNeverReversesTheOrderOfTwoStudents() {
        List<Integer> shown = grid().stream().map(p -> displayPct(p)).toList();
        assertThat(shown).isSorted();
    }

    // test_limits_are_worded_as_limits
    @Test
    void limitsAreWordedAsLimits() {
        assertThat(displayText(0.01)).isEqualTo("5% or less");
        assertThat(displayText(0.05)).isEqualTo("5% or less");
        assertThat(displayText(0.06)).isEqualTo("6%");
        assertThat(displayText(0.94)).isEqualTo("94%");
        assertThat(displayText(0.95)).isEqualTo("95% or more");
        assertThat(displayText(0.99)).isEqualTo("95% or more");
    }

    // test_band_uses_the_same_thresholds_as_question_difficulty
    @Test
    void bandUsesTheSameThresholdsAsQuestionDifficulty() {
        Map<String, String> tierToBand = Map.of("easy", "needs_practice", "medium", "developing", "hard", "strong");
        for (double p : grid()) {
            assertThat(masteryBand(p)).isEqualTo(tierToBand.get(Mastery.difficultyFor(p)));
        }
    }

    // test_display_module_only_reads_the_mastery_model
    /** It reads two thresholds and nothing that could write a value back. */
    @Test
    void displayModuleOnlyReadsTheMasteryModel() {
        String code = SourceText.serviceCode("MasteryDisplay");
        assertThat(code).doesNotContain("updateMastery").doesNotContain("model.").doesNotContain("Repository");
    }

    /**
     * Java only: halves round to even, as Python's round() does. The expected
     * values were taken from Python: round(0.125*100) == 12, round(0.875*100) == 88,
     * round(0.665*100) == 66. Math.round would give 13, 88 and 67.
     */
    @Test
    void halvesRoundLikePython() {
        assertThat(displayPct(0.125)).isEqualTo(12);
        assertThat(displayPct(0.875)).isEqualTo(88);
        assertThat(displayPct(0.665)).isEqualTo(66);
    }
}
