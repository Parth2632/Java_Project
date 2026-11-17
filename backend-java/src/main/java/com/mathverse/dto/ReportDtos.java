package com.mathverse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Shapes for guardians and reports (Python: the "Guardians" and "Reports" parts
 * of schemas.py). Dates are sent as text, "2026-11-17", and times as
 * "2026-11-17T10:05:00.123456" in UTC, exactly as the Python API sends them.
 * Integer and Double (not int and double) mark values that can be null.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** POST /guardians/links */
    public record LinkRequest(
            @NotNull(message = "Field required")
            @Size(min = 4, message = "String should have at least 4 characters")
            @Size(max = 32, message = "String should have at most 32 characters")
            String linkCode) {
    }

    /** One line in a parent's or teacher's list of students. */
    public record LinkedStudent(long id, String name, int level, int points, boolean isDemo, int questionsAnswered,
                                Integer accuracyPct, // null when no questions answered
                                String lastActive) {
    }

    public record TopicMasteryOut(String topic, String label,
                                  Integer masteryPct, // display value, 5-95; null = topic not started
                                  String masteryBand, // needs_practice | developing | strong; null = not started
                                  int questionsAnswered,
                                  int correctAnswers) { // the plain record, shown beside the estimate
    }

    public record RecentAttemptOut(long id, String bossName, String topicLabel, String difficulty, boolean isCorrect,
                                   double responseTimeS, boolean hintUsed, int pointsAwarded, String answeredAt,
                                   String attentionLabel) { // null = not measured
    }

    public record AttentionOut(int measuredQuestions, int totalQuestions,
                               Integer avgOnScreenPct) { // null when nothing was measured
    }

    /** One calendar day. Days without practice are included with zero questions. */
    public record DailyPoint(String date, int questions, int correct,
                             Integer accuracyPct, // null when nothing was answered that day
                             double practiceMinutes,
                             int attentionMeasured, // questions that day with a camera estimate
                             Integer avgOnScreenPct) { // null when nothing was measured that day
    }

    public record MasteryPoint(String date, int masteryPct) {
    }

    public record TopicHistory(String topic, String label, List<MasteryPoint> points) {
    }

    public record Badge(String id, String label, String description) {
    }

    public record StudentReport(
            long studentId,
            String studentName,
            boolean isDemo,
            int points,
            int level,
            String lastActive,
            int questionsAnswered,
            int correctAnswers,
            Integer accuracyPct,
            int practiceMinutes,
            int missionsPlayed,
            int missionsWon,
            int bossesDefeated,
            int totalBosses,
            List<TopicMasteryOut> topicMastery,
            List<TopicMasteryOut> needsPractice, // started topics with low mastery, weakest first
            List<DailyPoint> daily, // the last 14 days, oldest first
            List<TopicHistory> masteryHistory,
            List<String> observations, // rule-based statements; empty until there is enough data
            List<Badge> badges, // earned from stored missions and answers only
            List<RecentAttemptOut> recentAttempts,
            AttentionOut attention) {
    }
}
