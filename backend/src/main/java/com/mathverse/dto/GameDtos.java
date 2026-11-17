package com.mathverse.dto;

import com.mathverse.model.PlaySession;
import com.mathverse.service.Game;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request and response shapes for missions (Python: the "Game" part of schemas.py).
 * Kept together because they are small and used together.
 */
public final class GameDtos {

    private GameDtos() {
    }

    public record ProgressOut(int points, int level, List<String> unlockedBosses, long missionsWon, String linkCode) {
    }

    /** What the student sees. The correct answer is deliberately absent. */
    public record QuestionOut(long attemptId, String text, List<String> options, String topic, String difficulty) {
    }

    public record SessionState(long id, String bossId, String status, int correctCount, int wrongCount, int streak,
                               int questionsToWin, int maxWrong, int pointsAwarded) {

        public static SessionState from(PlaySession s) {
            return new SessionState(s.getId(), s.getBossId(), s.getStatus(), s.getCorrectCount(), s.getWrongCount(),
                    s.getStreak(), Game.QUESTIONS_TO_WIN, Game.MAX_WRONG, s.getPointsAwarded());
        }
    }

    /** POST /sessions */
    public record StartSessionRequest(@NotNull(message = "Field required") String bossId) {
    }

    public record StartSessionResponse(SessionState session, QuestionOut question) {
    }

    /**
     * POST /attempts/{id}/answer. Only which option was picked: the server
     * judges it and awards the points. A score or user id cannot be sent
     * (unknown fields are rejected).
     */
    public record AnswerRequest(
            // null = ran out of time
            @Min(value = 0, message = "Input should be greater than or equal to 0")
            @Max(value = 3, message = "Input should be less than or equal to 3")
            Integer selectedIndex,

            @NotNull(message = "Field required")
            @Min(value = 0, message = "Input should be greater than or equal to 0")
            @Max(value = 3_600_000, message = "Input should be less than or equal to 3600000")
            Integer responseTimeMs,

            Boolean hintUsed, // missing = false

            // null = camera not used
            @Valid AttentionSummary attention) {

        public boolean hintUsedOrFalse() {
            return Boolean.TRUE.equals(hintUsed);
        }
    }

    public record AnswerResponse(
            boolean correct,
            int correctIndex,
            int pointsAwarded,
            int bonusPoints,
            double mastery, // the raw stored value
            int masteryDisplayPct, // what to show on screen: limited to 5-95
            String sessionStatus,
            SessionState session,
            ProgressOut progress,
            QuestionOut nextQuestion) { // null when the mission is over
    }

    public record HintOut(String hint) {
    }
}
