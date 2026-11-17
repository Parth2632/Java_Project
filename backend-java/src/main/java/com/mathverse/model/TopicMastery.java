package com.mathverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** A student's estimated mastery of one topic (BKT, see service/Mastery). Table "topic_mastery". */
@Entity
@Table(name = "topic_mastery",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "topic"}),
        indexes = @Index(columnList = "student_id"))
public class TopicMastery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "p_mastery", nullable = false)
    private double pMastery;

    @Column(name = "attempts_count", nullable = false)
    private int attemptsCount = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = Times.utcnow();

    protected TopicMastery() {
        // for JPA
    }

    public TopicMastery(Long studentId, String topic, double pMastery) {
        this.studentId = studentId;
        this.topic = topic;
        this.pMastery = pMastery;
    }

    /** Like onupdate=utcnow in the Python model. */
    @PreUpdate
    void touch() {
        updatedAt = Times.utcnow();
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getTopic() {
        return topic;
    }

    public double getPMastery() {
        return pMastery;
    }

    public void setPMastery(double pMastery) {
        this.pMastery = pMastery;
    }

    public int getAttemptsCount() {
        return attemptsCount;
    }

    public void setAttemptsCount(int attemptsCount) {
        this.attemptsCount = attemptsCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
