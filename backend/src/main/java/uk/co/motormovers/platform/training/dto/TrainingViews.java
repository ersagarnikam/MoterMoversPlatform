package uk.co.motormovers.platform.training.dto;

import java.time.LocalDate;
import java.util.List;

import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.CourseTrack;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.model.InductionLesson;

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

    /** A cohort as the team manages it: past ones too, with places booked rather than places left. */
    public record AdminCohortView(Long id, String name, LocalDate startsOn, String timeSlot, int capacity,
            int booked, String trainerName, boolean recordingAgreed) {
    }

    public record AdminCourseView(Long id, CourseTrack track, String title, String description, int liveSessions,
            int sessionMinutes, int pricePence, Integer renewalPence, boolean awardsCertificate,
            List<AdminCohortView> cohorts) {
    }

    public record LessonView(Long id, int position, String title, String summary, List<String> paragraphs,
            int minutes, String videoUrl, boolean done) {

        public static LessonView of(InductionLesson l, boolean done) {
            return new LessonView(l.getId(), l.getPosition(), l.getTitle(), l.getSummary(), l.paragraphs(),
                    l.getMinutes(), l.getVideoUrl(), done);
        }
    }

    /** A member of staff's progress through the induction. completedOn is null until every lesson is done. */
    public record InductionView(List<LessonView> lessons, int totalMinutes, int lessonsDone, LocalDate completedOn) {
    }

    public record EnrolmentView(Long id, Long cohortId, String courseTitle, CourseTrack track, String cohortName,
            LocalDate startsOn, String timeSlot, int sessionsAttended, int liveSessions, boolean assessmentPassed,
            LocalDate certificateIssuedOn, LocalDate certificateExpiresOn, String learnerName, String learnerEmail) {

        public static EnrolmentView of(Enrolment e) {
            Cohort c = e.getCohort();
            return new EnrolmentView(e.getId(), c.getId(), c.getCourse().getTitle(), c.getCourse().getTrack(),
                    c.getName(), c.getStartsOn(), c.getTimeSlot(), e.getSessionsAttended(),
                    c.getCourse().getLiveSessions(), e.isAssessmentPassed(), e.getCertificateIssuedOn(),
                    e.getCertificateExpiresOn(), e.getUser().getDisplayName(), e.getUser().getEmail());
        }
    }
}
