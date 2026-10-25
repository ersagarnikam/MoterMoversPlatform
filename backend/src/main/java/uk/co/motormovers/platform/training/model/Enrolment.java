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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser user;

    private Instant enrolledAt;

    private int sessionsAttended;

    private boolean assessmentPassed;

    private LocalDate certificateIssuedOn;

    private LocalDate certificateExpiresOn;

    /** The school's invoice for a staff place; null when nothing was invoiced with the booking. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Payment payment;

    protected Enrolment() {
    }

    public Enrolment(Cohort cohort, AppUser user) {
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

    /** Issues the yearly practitioner certificate once every live session is attended and the assessment passed. */
    public boolean issueCertificateIfEligible(LocalDate today) {
        Course course = cohort.getCourse();
        if (!course.isAwardsCertificate() || certificateIssuedOn != null) {
            return false;
        }
        if (sessionsAttended >= course.getLiveSessions() && assessmentPassed) {
            certificateIssuedOn = today;
            certificateExpiresOn = today.plusYears(1);
            return true;
        }
        return false;
    }

    public Long getId() {
        return id;
    }

    public Cohort getCohort() {
        return cohort;
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

    public Payment getPayment() {
        return payment;
    }

    public void invoicedWith(Payment payment) {
        this.payment = payment;
    }
}
