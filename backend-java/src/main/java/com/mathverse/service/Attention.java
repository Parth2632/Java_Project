package com.mathverse.service;

import com.mathverse.dto.AttentionSummary;
import com.mathverse.model.Attempt;

/**
 * Turns the browser's attention summary into the values stored beside an
 * attempt (Python: services/attention.py).
 *
 * This is an estimate of whether the student was facing the screen. It is not a
 * medical assessment, and nothing academic (mastery, difficulty, points, unlocks)
 * ever reads it.
 */
public final class Attention {

    public record ScoreAndLabel(double score, String label) {
    }

    private Attention() {
    }

    public static ScoreAndLabel scoreAndLabel(double facePresentPct, double onScreenPct) {
        double score = Rounding.round(onScreenPct / 100.0, 3);
        String label;
        if (facePresentPct < 50) {
            label = "Away";
        } else if (onScreenPct >= 80) {
            label = "Focused";
        } else if (onScreenPct >= 50) {
            label = "Partly focused";
        } else {
            label = "Distracted";
        }
        return new ScoreAndLabel(score, label);
    }

    /** Copies a summary onto an attempt. With no summary, every attention column stays NULL. */
    public static void applyToAttempt(Attempt attempt, AttentionSummary summary) {
        if (summary == null) {
            return;
        }
        attempt.setFacePresentPct(summary.facePresentPct());
        attempt.setOnScreenPct(summary.onScreenPct());
        attempt.setBlinkRatePerMin(summary.blinkRatePerMin());
        ScoreAndLabel result = scoreAndLabel(summary.facePresentPct(), summary.onScreenPct());
        attempt.setAttentionScore(result.score());
        attempt.setAttentionLabel(result.label());
    }
}
