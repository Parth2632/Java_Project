package com.mathverse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** A student, parent or teacher account (table "users"). */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** student | parent | teacher */
    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "points", nullable = false)
    private int points = 0;

    @Column(name = "level", nullable = false)
    private int level = 1;

    /** Students only: the code a parent or teacher must enter to link to this child. */
    @Column(name = "link_code", unique = true)
    private String linkCode;

    /** Demo accounts hold synthetic data and can never be linked with real accounts. */
    @Column(name = "is_demo", nullable = false)
    private boolean isDemo = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = Times.utcnow();

    protected User() {
        // for JPA
    }

    public User(String name, String email, String passwordHash, String role, String linkCode, boolean isDemo) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.linkCode = linkCode;
        this.isDemo = isDemo;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getLinkCode() {
        return linkCode;
    }

    public boolean isDemo() {
        return isDemo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
