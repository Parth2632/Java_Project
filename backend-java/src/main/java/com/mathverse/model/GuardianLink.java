package com.mathverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** A parent or teacher who may view one student's reports. Many-to-many (table "guardian_links"). */
@Entity
@Table(name = "guardian_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"guardian_id", "student_id"}),
        indexes = {@Index(columnList = "guardian_id"), @Index(columnList = "student_id")})
public class GuardianLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** users.id of the parent or teacher */
    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;

    /** users.id of the student */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = Times.utcnow();

    protected GuardianLink() {
        // for JPA
    }

    public GuardianLink(Long guardianId, Long studentId) {
        this.guardianId = guardianId;
        this.studentId = studentId;
    }

    public Long getId() {
        return id;
    }

    public Long getGuardianId() {
        return guardianId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
