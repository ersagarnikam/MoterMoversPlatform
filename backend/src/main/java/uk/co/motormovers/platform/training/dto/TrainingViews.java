package uk.co.motormovers.platform.training.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
    public record AdminCohortView(Long id, String name, LocalDate startsOn, LocalTime startTime, String timeSlot,
            int capacity, int booked, String trainerName, Long trainerUserId, boolean recordingAgreed,
            List<SessionSummary> sessions) {
    }

    public record SessionSummary(Long id, int number, Instant startsAt, int durationMinutes) {
    }

    /** A live session as someone who may join it sees it. {@code host} is true for the Motor Movers team. */
    public record LiveSessionView(Long id, Long cohortId, String courseTitle, String cohortName, int number,
            int of, Instant startsAt, int durationMinutes, String trainerName, boolean open, boolean host) {
    }

    /**
     * Everything the browser needs for the meeting. With {@code zoomConnected} false the Zoom fields are null and
     * the page runs the session without video. With {@code embedded} the meeting shows inside the page (Meeting SDK:
     * sdkKey, signature, zak); otherwise {@code zoomUrl} opens it in Zoom (for the host, a start link that signs
     * them in as host).
     */
    public record JoinView(LiveSessionView session, boolean zoomConnected, boolean embedded, String zoomUrl,
            String sdkKey, String signature,
            String meetingNumber, String passcode, String zak, String userName, String userEmail, String rules,
            boolean recorded) {
    }

    /** {@code joinedAt} is when the learner first opened this session (null: not yet), {@code lastJoinedAt} the latest. */
    public record RegisterRow(Long enrolmentId, String learnerName, String learnerEmail, boolean present,
            int sessionsAttended, boolean certificateIssued, Instant joinedAt, Instant lastJoinedAt) {
    }

    public record RegisterView(LiveSessionView session, List<RegisterRow> learners) {
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
            LocalDate certificateIssuedOn, LocalDate certificateExpiresOn, String learnerName, String learnerEmail,
            List<Integer> attendedSessions) {

        public static EnrolmentView of(Enrolment e, List<Integer> attendedSessions) {
            Cohort c = e.getCohort();
            return new EnrolmentView(e.getId(), c.getId(), c.getCourse().getTitle(), c.getCourse().getTrack(),
                    c.getName(), c.getStartsOn(), c.getTimeSlot(), e.getSessionsAttended(),
                    c.getCourse().getLiveSessions(), e.isAssessmentPassed(), e.getCertificateIssuedOn(),
                    e.getCertificateExpiresOn(), e.getUser().getDisplayName(), e.getUser().getEmail(), attendedSessions);
        }
    }
}
