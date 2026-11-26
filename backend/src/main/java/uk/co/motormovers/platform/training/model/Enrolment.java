package uk.co.motormovers.platform.training.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.user.model.AppUser;

@Entity
public class Enrolment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The course the place is on. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    /** The cohort of a live course; null for a place on a recorded course, which has none (task 89). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser user;

    private Instant enrolledAt;

    private int sessionsAttended;

    private boolean assessmentPassed;

    private LocalDate certificateIssuedOn;

    private LocalDate certificateExpiresOn;

    /** When the learner was reminded that the certificate expires soon; null: not yet. */
    private LocalDate expiryReminderSentOn;

    /** The school's invoice for a staff place; null when nothing was invoiced with the booking. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Payment payment;

    protected Enrolment() {
    }

    /** A place on a recorded course: the learner starts its lessons at once. */
    public Enrolment(Course course, AppUser user) {
        this.course = course;
        this.user = user;
        this.enrolledAt = Instant.now();
    }

    /** A place on a cohort of a live course. */
    public Enrolment(Cohort cohort, AppUser user) {
        this.course = cohort.getCourse();
        this.cohort = cohort;
        this.user = user;
        this.enrolledAt = Instant.now();
    }

    /** Takes the count from the session register. A certificate already issued stands, so it is refused then. */
    public void attendanceChanged(int sessionsMarked) {
        if (certificateIssuedOn != null) {
            throw new IllegalStateException("The certificate has been issued, so attendance can no longer be changed");
        }
        this.sessionsAttended = sessionsMarked;
    }

    public void recordAssessment(boolean passed) {
        if (!passed && certificateIssuedOn != null) {
            throw new IllegalStateException("The certificate has been issued, so the assessment can no longer be changed");
        }
        this.assessmentPassed = passed;
    }

    /**
     * Issues the yearly certificate. On a recorded course: once every lesson is done ({@code lessonsDone}) and every
     * quiz of the course is passed ({@code quizzesPassed}, true for a course without quizzes). On a live course:
     * once every live session is attended, every quiz passed and the trainer's assessment passed.
     */
    public boolean issueCertificateIfEligible(LocalDate today, boolean quizzesPassed) {
        return issueCertificateIfEligible(today, quizzesPassed, false);
    }

    public boolean issueCertificateIfEligible(LocalDate today, boolean quizzesPassed, boolean lessonsDone) {
        if (!course.isAwardsCertificate() || certificateIssuedOn != null) {
            return false;
        }
        boolean complete = isRecorded() ? lessonsDone
                : sessionsAttended >= course.getLiveSessions() && assessmentPassed;
        if (complete && quizzesPassed) {
            certificateIssuedOn = today;
            certificateExpiresOn = today.plusYears(1);
            return true;
        }
        return false;
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    /** Null for a place on a recorded course. */
    public Cohort getCohort() {
        return cohort;
    }

    /** A place on the course itself, with no cohort: the learner does its recorded lessons. */
    public boolean isRecorded() {
        return cohort == null;
    }

    public AppUser getUser() {
        return user;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public int getSessionsAttended() {
        return sessionsAttended;
    }

    public boolean isAssessmentPassed() {
        return assessmentPassed;
    }

    public LocalDate getCertificateIssuedOn() {
        return certificateIssuedOn;
    }

    public LocalDate getCertificateExpiresOn() {
        return certificateExpiresOn;
    }

    public LocalDate getExpiryReminderSentOn() {
        return expiryReminderSentOn;
    }

    public void expiryReminderSent(LocalDate on) {
        this.expiryReminderSentOn = on;
    }

    public Payment getPayment() {
        return payment;
    }

    public void invoicedWith(Payment payment) {
        this.payment = payment;
    }
}
