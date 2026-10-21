package uk.co.motormovers.platform.training.model;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

/** A learner marked present at one live session. No row means not marked present. */
@Entity
public class SessionAttendance {

    @Embeddable
    public record Key(Long sessionId, Long enrolmentId) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    private String markedBy;

    private Instant markedAt;

    protected SessionAttendance() {
    }

    public SessionAttendance(Long sessionId, Long enrolmentId, String markedBy, Instant markedAt) {
        this.id = new Key(sessionId, enrolmentId);
        this.markedBy = markedBy;
        this.markedAt = markedAt;
    }

    public Key getId() {
        return id;
    }
}
