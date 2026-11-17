package com.mathverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One question served to a student. Created when served, completed when answered.
 *
 * Columns that can be "not answered" or "not measured" use Integer, Double and
 * Boolean (which can be null) rather than int, double and boolean (which would
 * silently become 0 or false).
 */
@Entity
@Table(name = "attempts", indexes = {@Index(columnList = "session_id"), @Index(columnList = "student_id")})
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "difficulty", nullable = false)
    private String difficulty = "medium";

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** The four options as a JSON list, e.g. ["12","14","15","16"] */
    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    /** Never sent to the client before the question is answered. */
    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    @Column(name = "hint_text", nullable = false, columnDefinition = "TEXT")
    private String hintText = "";

    /** NULL = unanswered or timed out */
    @Column(name = "selected_index")
    private Integer selectedIndex;

    /** NULL until answered */
    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "hint_used", nullable = false)
    private boolean hintUsed = false;

    @Column(name = "points_awarded", nullable = false)
    private int pointsAwarded = 0;

    @Column(name = "mastery_before")
    private Double masteryBefore;

    @Column(name = "mastery_after")
    private Double masteryAfter;

    // Optional attention estimate. NULL means "not measured" (camera off or
    // unavailable). It is stored and reported, and never read by anything academic.
    @Column(name = "face_present_pct")
    private Double facePresentPct;

    @Column(name = "on_screen_pct")
    private Double onScreenPct;

    @Column(name = "blink_rate_per_min")
    private Double blinkRatePerMin;

    @Column(name = "attention_score")
    private Double attentionScore;

    @Column(name = "attention_label")
    private String attentionLabel;

    @Column(name = "served_at", nullable = false)
    private LocalDateTime servedAt = Times.utcnow();

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    protected Attempt() {
        // for JPA
    }

    public Attempt(Long sessionId, Long studentId, String topic, String difficulty, String questionText,
                   String optionsJson, int correctIndex, String hintText) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.topic = topic;
        this.difficulty = difficulty;
        this.questionText = questionText;
        this.optionsJson = optionsJson;
        this.correctIndex = correctIndex;
        this.hintText = hintText;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getTopic() {
        return topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public String getHintText() {
        return hintText;
    }

    public Integer getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(Integer selectedIndex) {
        this.selectedIndex = selectedIndex;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public Integer getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Integer responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public boolean isHintUsed() {
        return hintUsed;
    }

    public void setHintUsed(boolean hintUsed) {
        this.hintUsed = hintUsed;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public void setPointsAwarded(int pointsAwarded) {
        this.pointsAwarded = pointsAwarded;
    }

    public Double getMasteryBefore() {
        return masteryBefore;
    }

    public void setMasteryBefore(Double masteryBefore) {
        this.masteryBefore = masteryBefore;
    }

    public Double getMasteryAfter() {
        return masteryAfter;
    }

    public void setMasteryAfter(Double masteryAfter) {
        this.masteryAfter = masteryAfter;
    }

    public Double getFacePresentPct() {
        return facePresentPct;
    }

    public void setFacePresentPct(Double facePresentPct) {
        this.facePresentPct = facePresentPct;
    }

    public Double getOnScreenPct() {
        return onScreenPct;
    }

    public void setOnScreenPct(Double onScreenPct) {
        this.onScreenPct = onScreenPct;
    }

    public Double getBlinkRatePerMin() {
        return blinkRatePerMin;
    }

    public void setBlinkRatePerMin(Double blinkRatePerMin) {
        this.blinkRatePerMin = blinkRatePerMin;
    }

    public Double getAttentionScore() {
        return attentionScore;
    }

    public void setAttentionScore(Double attentionScore) {
        this.attentionScore = attentionScore;
    }

    public String getAttentionLabel() {
        return attentionLabel;
    }

    public void setAttentionLabel(String attentionLabel) {
        this.attentionLabel = attentionLabel;
    }

    public LocalDateTime getServedAt() {
        return servedAt;
    }

    public void setServedAt(LocalDateTime servedAt) {
        this.servedAt = servedAt;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }
}
