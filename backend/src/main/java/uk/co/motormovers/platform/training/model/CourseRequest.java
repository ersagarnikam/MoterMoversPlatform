package uk.co.motormovers.platform.training.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.user.model.AppUser;

/**
 * A member of staff asks for a place on a paid course. Their school pays, so the head approves it first: approving
 * books the place and invoices the school; declining or withdrawing books nothing.
 */
@Entity
public class CourseRequest {

    public enum Status {
        REQUESTED, APPROVED, DECLINED, WITHDRAWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    /** The cohort asked for on a live course; null for a recorded course (task 89). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private School school;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant requestedAt;

    private String decidedBy;

    private Instant decidedAt;

    protected CourseRequest() {
    }

    /** A place on a recorded course. */
    public CourseRequest(Course course, AppUser user, School school, Instant at) {
        this.course = course;
        this.user = user;
        this.school = school;
        this.status = Status.REQUESTED;
        this.requestedAt = at;
    }

    public CourseRequest(Cohort cohort, AppUser user, School school, Instant at) {
        this.course = cohort.getCourse();
        this.cohort = cohort;
        this.user = user;
        this.school = school;
        this.status = Status.REQUESTED;
        this.requestedAt = at;
    }

    /** APPROVED, DECLINED or WITHDRAWN; only a request still waiting can be decided. */
    public void decide(Status outcome, String by, Instant at) {
        if (status != Status.REQUESTED) {
            throw new IllegalStateException("This request has already been " + status.name().toLowerCase());
        }
        this.status = outcome;
        this.decidedBy = by;
        this.decidedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    /** Null for a recorded course. */
    public Cohort getCohort() {
        return cohort;
    }

    public AppUser getUser() {
        return user;
    }

    public School getSchool() {
        return school;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
