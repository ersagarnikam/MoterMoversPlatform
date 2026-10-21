package uk.co.motormovers.platform.training.model;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** One dated live session of a cohort, run as a Zoom meeting inside the platform. */
@Entity
public class CohortSession {

    /** Learners can join from this long before the start. */
    public static final Duration OPENS_BEFORE = Duration.ofMinutes(15);
    /** ...and until this long after the planned end, in case a session runs over. */
    public static final Duration CLOSES_AFTER = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Cohort cohort;

    private int number;

    private Instant startsAt;

    private int durationMinutes;

    private String zoomMeetingId;

    private String zoomPasscode;

    protected CohortSession() {
    }

    public CohortSession(Cohort cohort, int number, Instant startsAt, int durationMinutes) {
        this.cohort = cohort;
        this.number = number;
        this.startsAt = startsAt;
        this.durationMinutes = durationMinutes;
    }

    public void reschedule(Instant startsAt, int durationMinutes) {
        this.startsAt = startsAt;
        this.durationMinutes = durationMinutes;
    }

    public void attachMeeting(String meetingId, String passcode) {
        this.zoomMeetingId = meetingId;
        this.zoomPasscode = passcode;
    }

    public Instant endsAt() {
        return startsAt.plus(Duration.ofMinutes(durationMinutes));
    }

    /** Whether a learner may join at this moment. */
    public boolean isOpenAt(Instant now) {
        return !now.isBefore(startsAt.minus(OPENS_BEFORE)) && now.isBefore(endsAt().plus(CLOSES_AFTER));
    }

    public boolean isOverAt(Instant now) {
        return !now.isBefore(endsAt().plus(CLOSES_AFTER));
    }

    public Long getId() {
        return id;
    }

    public Cohort getCohort() {
        return cohort;
    }

    public int getNumber() {
        return number;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getZoomMeetingId() {
        return zoomMeetingId;
    }

    public String getZoomPasscode() {
        return zoomPasscode;
    }
}
