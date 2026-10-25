package uk.co.motormovers.platform.training.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A learner's go at a quiz: the score only, not the answers. */
@Entity
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long quizId;

    private Long enrolmentId;

    private int scorePercent;

    private boolean passed;

    private Instant takenAt;

    protected QuizAttempt() {
    }

    public QuizAttempt(Long quizId, Long enrolmentId, int scorePercent, boolean passed, Instant takenAt) {
        this.quizId = quizId;
        this.enrolmentId = enrolmentId;
        this.scorePercent = scorePercent;
        this.passed = passed;
        this.takenAt = takenAt;
    }

    public Long getQuizId() {
        return quizId;
    }

    public int getScorePercent() {
        return scorePercent;
    }

    public boolean isPassed() {
        return passed;
    }
}
