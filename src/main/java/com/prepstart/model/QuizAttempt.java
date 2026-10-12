package com.prepstart.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quizattempt")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Integer attemptId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "quiz_id")
    private Integer quizId;

    @Column(name = "score")
    private Integer score;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public QuizAttempt() {
    }

    public Integer getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Integer attemptId) {
        this.attemptId = attemptId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getQuizId() {
        return quizId;
    }

    public void setQuizId(Integer quizId) {
        this.quizId = quizId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}