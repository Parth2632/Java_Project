package com.mathverse.service;

/**
 * How mastery is SHOWN to people. Presentation only (Python: services/mastery_display.py).
 *
 * The mastery model ({@link Mastery}) stores a probability between 0.01 and
 * 0.99. Shown raw, "1%" or "99%" reads like certainty to a parent or teacher,
 * which the model cannot honestly claim after a handful of questions.
 *
 * So for display:
 *   - the BAND comes first, in words: needs practice / developing / strong;
 *   - the percentage is limited to 5-95 and described as an estimate. A value
 *     at a limit is worded "5% or less" or "95% or more", so it is clearly a
 *     limit and not a measurement.
 *
 * What this class never does:
 *   - it never changes a stored value (p_mastery, mastery_before, mastery_after);
 *   - it is never used to make a decision. Question difficulty, the "needs
 *     practice" list and which observations appear all use the raw stored value.
 */
public final class MasteryDisplay {

    public static final int DISPLAY_MIN_PCT = 5;
    public static final int DISPLAY_MAX_PCT = 95;

    public static final String NEEDS_PRACTICE = "needs_practice";
    public static final String DEVELOPING = "developing";
    public static final String STRONG = "strong";

    private MasteryDisplay() {
    }

    /** The level in words. Uses the same thresholds that pick question difficulty. */
    public static String masteryBand(double pMastery) {
        if (pMastery < Mastery.EASY_BELOW) {
            return NEEDS_PRACTICE;
        }
        if (pMastery > Mastery.HARD_ABOVE) {
            return STRONG;
        }
        return DEVELOPING;
    }

    /** The percentage to show: the raw value rounded, then kept within 5-95. */
    public static int displayPct(double pMastery) {
        return Math.max(DISPLAY_MIN_PCT, Math.min(DISPLAY_MAX_PCT, Rounding.round(pMastery * 100)));
    }

    /** The percentage as words, making the limits explicit. */
    public static String displayText(double pMastery) {
        int pct = displayPct(pMastery);
        if (pct <= DISPLAY_MIN_PCT) {
            return DISPLAY_MIN_PCT + "% or less";
        }
        if (pct >= DISPLAY_MAX_PCT) {
            return DISPLAY_MAX_PCT + "% or more";
        }
        return pct + "%";
    }
}
