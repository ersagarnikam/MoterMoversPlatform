package uk.co.motormovers.platform.training.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.CourseRequest;
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

    /**
     * A cohort as the team manages it: past ones too, with places booked rather than places left. {@code clashes}
     * names sessions still to come that overlap another cohort's on the same Zoom login, which Zoom cannot run at once.
     */
    public record AdminCohortView(Long id, String name, LocalDate startsOn, LocalTime startTime, String timeSlot,
            int capacity, int booked, String trainerName, Long trainerUserId, boolean recordingAgreed,
            List<SessionSummary> sessions, List<String> clashes) {
    }

    /** A member of staff's request for a course place their school pays for, as they and their head see it. */
    public record CourseRequestView(Long id, Long cohortId, String courseTitle, String cohortName, LocalDate startsOn,
            String timeSlot, int pricePence, String learnerName, String learnerEmail, CourseRequest.Status status,
            Instant requestedAt, String decidedBy, Instant decidedAt) {

        public static CourseRequestView of(CourseRequest r) {
            Cohort c = r.getCohort();
            return new CourseRequestView(r.getId(), c.getId(), c.getCourse().getTitle(), c.getName(), c.getStartsOn(),
                    c.getTimeSlot(), c.getCourse().getPricePence(), r.getUser().getDisplayName(),
                    r.getUser().getEmail(), r.getStatus(), r.getRequestedAt(), r.getDecidedBy(), r.getDecidedAt());
        }
    }

    /** What happened when a place was cancelled, including to its invoice. */
    public record CancelView(String message) {
    }

    public record SessionSummary(Long id, int number, Instant startsAt, int durationMinutes) {
    }

    /**
     * A live session as someone who may join it sees it. {@code host} is true for whoever runs it (the team or the
     * cohort's trainer), who also sees how many are {@code booked}; null for learners.
     */
    public record LiveSessionView(Long id, Long cohortId, String courseTitle, String cohortName, int number,
            int of, Instant startsAt, int durationMinutes, String trainerName, boolean open, boolean host,
            Integer booked) {
    }

    /**
     * Everything the browser needs for the meeting. With {@code zoomConnected} false the Zoom fields are null and
     * the page runs the session without video. With {@code embedded} the meeting shows inside the page (Meeting SDK:
     * sdkKey, signature, zak); otherwise {@code zoomUrl} opens it in Zoom (for the host, a start link that signs
     * them in as host). Learners get {@code zoomUrl} in both modes, for the parent app to open the Zoom app.
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
            List<Integer> attendedSessions, int quizzesPassed, int quizzes) {

        public static EnrolmentView of(Enrolment e, List<Integer> attendedSessions, QuizProgress quizProgress) {
            Cohort c = e.getCohort();
            return new EnrolmentView(e.getId(), c.getId(), c.getCourse().getTitle(), c.getCourse().getTrack(),
                    c.getName(), c.getStartsOn(), c.getTimeSlot(), e.getSessionsAttended(),
                    c.getCourse().getLiveSessions(), e.isAssessmentPassed(), e.getCertificateIssuedOn(),
                    e.getCertificateExpiresOn(), e.getUser().getDisplayName(), e.getUser().getEmail(), attendedSessions,
                    quizProgress.passed(), quizProgress.total());
        }
    }

    /** How many of a course's quizzes a learner has passed, of how many. */
    public record QuizProgress(int passed, int total) {
    }
}
