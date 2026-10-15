package uk.co.motormovers.platform.training;

import java.time.LocalDate;
import java.util.List;

public final class TrainingViews {

    /** Shown on every live course page. */
    public static final String LIVE_SESSION_RULES = "Adults only on camera. No discussion of an individual "
            + "child's condition: questions about a particular child are signposted to the family's GP or school.";

    private TrainingViews() {
    }

    public record CohortView(Long id, String name, LocalDate startsOn, String timeSlot, String trainerName,
            int seatsLeft, boolean recordingAgreed) {
    }

    public record CourseView(Long id, CourseTrack track, String title, String description, int liveSessions,
            int sessionMinutes, int pricePence, Integer renewalPence, boolean awardsCertificate,
            List<CohortView> cohorts) {
    }

    public record EnrolmentView(Long id, Long cohortId, String courseTitle, CourseTrack track, String cohortName,
            LocalDate startsOn, String timeSlot, int sessionsAttended, int liveSessions, boolean assessmentPassed,
            LocalDate certificateIssuedOn, LocalDate certificateExpiresOn, String learnerName) {

        static EnrolmentView of(Enrolment e) {
            Cohort c = e.getCohort();
            return new EnrolmentView(e.getId(), c.getId(), c.getCourse().getTitle(), c.getCourse().getTrack(),
                    c.getName(), c.getStartsOn(), c.getTimeSlot(), e.getSessionsAttended(),
                    c.getCourse().getLiveSessions(), e.isAssessmentPassed(), e.getCertificateIssuedOn(),
                    e.getCertificateExpiresOn(), e.getUser().getDisplayName());
        }
    }
}
