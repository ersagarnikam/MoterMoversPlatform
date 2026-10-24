package uk.co.motormovers.platform.training.model;

import java.time.Instant;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

/** A booked learner opened a live session. Joining again (e.g. after a dropped connection) updates the last time. */
@Entity
public class SessionJoin {

    @EmbeddedId
    private SessionAttendance.Key id;

    private Instant firstJoinedAt;

    private Instant lastJoinedAt;

    protected SessionJoin() {
    }

    public SessionJoin(Long sessionId, Long enrolmentId, Instant at) {
        this.id = new SessionAttendance.Key(sessionId, enrolmentId);
        this.firstJoinedAt = at;
        this.lastJoinedAt = at;
    }

    public void joinedAgain(Instant at) {
        this.lastJoinedAt = at;
    }

    public SessionAttendance.Key getId() {
        return id;
    }

    public Instant getFirstJoinedAt() {
        return firstJoinedAt;
    }

    public Instant getLastJoinedAt() {
        return lastJoinedAt;
    }
}
