package uk.co.motormovers.platform.training.model;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One member of staff has finished one induction lesson. */
@Entity
@Table(name = "induction_progress")
public class InductionProgress {

    @Embeddable
    public record Key(Long userId, Long lessonId) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    private Instant completedAt;

    protected InductionProgress() {
    }

    public InductionProgress(Long userId, Long lessonId) {
        this.id = new Key(userId, lessonId);
        this.completedAt = Instant.now();
    }

    public Key getId() {
        return id;
    }

    public Long getLessonId() {
        return id.lessonId();
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
