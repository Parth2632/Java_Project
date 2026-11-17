package com.mathverse.service;

/**
 * Game rules that the server owns: scoring, winning, losing (Python: services/game.py).
 * Levels and progress need the database and live in the mission code (phase 3).
 */
public final class Game {

    /** Correct answers needed to defeat a boss. */
    public static final int QUESTIONS_TO_WIN = 7;
    /** Wrong answers (or time-outs) before the mission fails. */
    public static final int MAX_WRONG = 7;

    static final int BASE_POINTS = 50;
    static final int STREAK_BONUS = 15;
    static final int SPEED_BONUS = 20;
    static final int SPEED_LIMIT_MS = 15_000;
    /** For an attempted-but-wrong answer; a time-out earns nothing. */
    static final int EFFORT_POINTS = 5;

    private Game() {
    }

    /**
     * Points for one answer.
     * @param selectedIndex the option picked, or null when the time ran out
     * @param streakBefore  correct answers in a row before this one
     */
    public static int pointsForAnswer(boolean isCorrect, Integer selectedIndex, int streakBefore, int responseTimeMs) {
        if (isCorrect) {
            int speed = responseTimeMs <= SPEED_LIMIT_MS ? SPEED_BONUS : 0;
            return BASE_POINTS + STREAK_BONUS * streakBefore + speed;
        }
        return selectedIndex != null ? EFFORT_POINTS : 0;
    }

    public static int victoryBonus(int streak) {
        return 300 + 50 * streak;
    }
}
