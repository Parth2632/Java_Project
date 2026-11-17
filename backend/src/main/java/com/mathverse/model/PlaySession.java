package com.mathverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One mission (boss battle): a run of questions that ends in victory, defeat or abandonment. */
@Entity
@Table(name = "play_sessions", indexes = @Index(columnList = "student_id"))
public class PlaySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "boss_id", nullable = false)
    private String bossId;

    @Column(name = "topic", nullable = false)
    private String topic;

    /** active | victory | defeat | abandoned */
    @Column(name = "status", nullable = false)
    private String status = "active";

    @Column(name = "correct_count", nullable = false)
    private int correctCount = 0;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount = 0;

    @Column(name = "streak", nullable = false)
    private int streak = 0;

    @Column(name = "points_awarded", nullable = false)
    private int pointsAwarded = 0;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = Times.utcnow();

    /** NULL while the mission is still active */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    protected PlaySession() {
        // for JPA
    }

    public PlaySession(Long studentId, String bossId, String topic) {
        this.studentId = studentId;
        this.bossId = bossId;
        this.topic = topic;
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getBossId() {
        return bossId;
    }

    public String getTopic() {
        return topic;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public int getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(int wrongCount) {
        this.wrongCount = wrongCount;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public void setPointsAwarded(int pointsAwarded) {
        this.pointsAwarded = pointsAwarded;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }
}
