package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.CohortSession;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.CourseTrack;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.zoom.ZoomProperties;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;

/** The rules behind live sessions and certificates, without the API around them. */
class LiveSessionRulesTest {

    private static final Instant START = Instant.parse("2026-11-03T19:00:00Z");

    private static Course course(CourseTrack track, int sessions, boolean certificate) {
        return new Course(track, "Course", "About it.", sessions, 60, 10_000, null, certificate);
    }

    private static Cohort cohort(Course course) {
        return new Cohort(course, "Cohort", LocalDate.of(2026, 11, 3), LocalTime.of(19, 0), "Tuesdays", 30, "Pat",
                false);
    }

    private static Enrolment enrolment(Course course) {
        return new Enrolment(cohort(course), new AppUser("l@x.test", "Learner", "hash", Role.PRACTITIONER, null, null));
    }

    @Nested
    class JoinWindow {

        private final CohortSession session = new CohortSession(null, 1, START, 60);

        @Test
        void opensFifteenMinutesBeforeTheStart() {
            assertThat(session.isOpenAt(START.minus(Duration.ofMinutes(16)))).isFalse();
            assertThat(session.isOpenAt(START.minus(Duration.ofMinutes(15)))).isTrue();
            assertThat(session.isOpenAt(START)).isTrue();
        }

        @Test
        void staysOpenUntilThirtyMinutesAfterThePlannedEnd() {
            Instant end = START.plus(Duration.ofMinutes(60));
            assertThat(session.endsAt()).isEqualTo(end);
            assertThat(session.isOpenAt(end.plus(Duration.ofMinutes(29)))).isTrue();
            assertThat(session.isOpenAt(end.plus(Duration.ofMinutes(30)))).isFalse();
            assertThat(session.isOverAt(end.plus(Duration.ofMinutes(29)))).isFalse();
            assertThat(session.isOverAt(end.plus(Duration.ofMinutes(30)))).isTrue();
        }

        @Test
        void aRescheduledSessionOpensAtItsNewTime() {
            CohortSession moved = new CohortSession(null, 1, START, 60);
            moved.reschedule(START.plus(Duration.ofDays(1)), 90);
            assertThat(moved.isOpenAt(START)).isFalse();
            assertThat(moved.endsAt()).isEqualTo(START.plus(Duration.ofDays(1)).plus(Duration.ofMinutes(90)));
        }

        @Test
        void keepsTheZoomMeetingOnceAttached() {
            CohortSession withMeeting = new CohortSession(null, 1, START, 60);
            withMeeting.attachMeeting("81234567890", "pass", "https://zoom.us/j/81234567890?pwd=x");
            assertThat(withMeeting.getZoomMeetingId()).isEqualTo("81234567890");
            assertThat(withMeeting.getZoomPasscode()).isEqualTo("pass");
            assertThat(withMeeting.getZoomJoinUrl()).isEqualTo("https://zoom.us/j/81234567890?pwd=x");
        }
    }

    @Nested
    class Certificates {

        private final LocalDate today = LocalDate.of(2026, 12, 1);

        @Test
        void areIssuedOnlyWithEverySessionAttendedAndTheAssessmentPassed() {
            Enrolment e = enrolment(course(CourseTrack.PROFESSIONAL, 4, true));
            e.attendanceChanged(3);
            e.recordAssessment(true);
            assertThat(e.issueCertificateIfEligible(today, true)).isFalse();

            e.attendanceChanged(4);
            assertThat(e.issueCertificateIfEligible(today, true)).isTrue();
            assertThat(e.getCertificateIssuedOn()).isEqualTo(today);
            assertThat(e.getCertificateExpiresOn()).isEqualTo(today.plusYears(1));
        }

        @Test
        void needTheAssessmentEvenWithFullAttendance() {
            Enrolment e = enrolment(course(CourseTrack.PROFESSIONAL, 4, true));
            e.attendanceChanged(4);
            assertThat(e.issueCertificateIfEligible(today, true)).isFalse();
            assertThat(e.getCertificateIssuedOn()).isNull();
        }

        @Test
        void areIssuedOnceAndThenLockAttendanceAndTheAssessment() {
            Enrolment e = enrolment(course(CourseTrack.PROFESSIONAL, 2, true));
            e.attendanceChanged(2);
            e.recordAssessment(true);
            e.issueCertificateIfEligible(today, true);

            assertThat(e.issueCertificateIfEligible(today.plusDays(5), true)).isFalse();
            assertThat(e.getCertificateIssuedOn()).isEqualTo(today);
            assertThatThrownBy(() -> e.attendanceChanged(1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("certificate has been issued");
            assertThatThrownBy(() -> e.recordAssessment(false)).isInstanceOf(IllegalStateException.class);
            // Confirming a pass again changes nothing, so it is allowed.
            e.recordAssessment(true);
        }

        @Test
        void needEveryQuizPassedWhenTheCourseHasQuizzes() {
            Enrolment e = enrolment(course(CourseTrack.PROFESSIONAL, 2, true));
            e.attendanceChanged(2);
            e.recordAssessment(true);
            assertThat(e.issueCertificateIfEligible(today, false)).isFalse();
            assertThat(e.issueCertificateIfEligible(today, true)).isTrue();
        }

        @Test
        void areNeverIssuedForAParentCourse() {
            Enrolment e = enrolment(course(CourseTrack.PARENT, 3, false));
            e.attendanceChanged(3);
            e.recordAssessment(true);
            assertThat(e.issueCertificateIfEligible(today, true)).isFalse();
        }
    }

    @Nested
    class ZoomModes {

        @Test
        void needTheServerToServerKeysToConnectAtAll() {
            assertThat(new ZoomProperties("acc", "cid", "secret", null, null, null).configured()).isTrue();
            assertThat(new ZoomProperties("acc", "cid", "", null, null, null).configured()).isFalse();
            assertThat(new ZoomProperties(null, null, null, "sdk", "sdk-secret", null).configured()).isFalse();
        }

        @Test
        void showTheMeetingInsideThePageOnlyWithASeparateMeetingSdkApp() {
            assertThat(new ZoomProperties("acc", "cid", "secret", "sdk", "sdk-secret", null).embedded()).isTrue();
            assertThat(new ZoomProperties("acc", "cid", "secret", null, null, null).embedded()).isFalse();
            assertThat(new ZoomProperties("acc", "cid", "secret", "sdk", " ", null).embedded()).isFalse();
            // The Server-to-Server app's own keys cannot sign the Meeting SDK, so they mean "open in Zoom".
            assertThat(new ZoomProperties("acc", "cid", "secret", "cid", "secret", null).embedded()).isFalse();
            assertThat(new ZoomProperties("acc", "cid", "secret", " cid ", "secret", null).embedded()).isFalse();
        }
    }
}
