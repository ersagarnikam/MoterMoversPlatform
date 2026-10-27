package uk.co.motormovers.platform.assessment.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.child.model.Child;

/**
 * A child's 1 to 4 score on one exercise at one point. A later score at the same point replaces the earlier one;
 * {@code clientId} is from the device, so a score sent twice from the offline queue is stored once.
 */
@Entity
@Table(name = "exercise_score")
public class ExerciseScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Child child;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Exercise exercise;

    private String point;

    private int score;

    private LocalDate assessedOn;

    private String recordedBy;

    protected ExerciseScore() {
    }

    public ExerciseScore(String clientId, Child child, Exercise exercise, String point, int score,
            LocalDate assessedOn, String recordedBy) {
        this.clientId = clientId;
        this.child = child;
        this.exercise = exercise;
        this.point = point;
        rescore(clientId, score, assessedOn, recordedBy);
    }

    public void rescore(String clientId, int score, LocalDate assessedOn, String recordedBy) {
        this.clientId = clientId;
        this.score = score;
        this.assessedOn = assessedOn;
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public Child getChild() {
        return child;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public String getPoint() {
        return point;
    }

    public int getScore() {
        return score;
    }

    public LocalDate getAssessedOn() {
        return assessedOn;
    }

    public String getRecordedBy() {
        return recordedBy;
    }
}
