package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.dto.CourseInputs.CohortInput;
import uk.co.motormovers.platform.training.dto.CourseInputs.CourseInput;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.CohortSession;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.CourseTrack;
import uk.co.motormovers.platform.training.repository.CohortRepository;
import uk.co.motormovers.platform.training.repository.CohortSessionRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * The team sets up live courses and opens cohorts for them. Courses go live as soon as they are saved, so the
 * wording check runs on save rather than at a separate sign-off. Mapped under /api/admin (ADMIN only), except
 * {@link #trainerCohorts}, a trainer's own cohorts.
 */
@Service
public class CourseAdminService {

    private final CourseRepository courses;
    private final CohortRepository cohorts;
    private final EnrolmentRepository enrolments;
    private final WordingGuard wordingGuard;
    private final LiveSessionService live;
    private final AppUserRepository users;
    private final CohortSessionRepository sessions;
    private final CurrentUserResolver currentUser;
    private final Clock clock;

    public CourseAdminService(CourseRepository courses, CohortRepository cohorts, EnrolmentRepository enrolments,
            WordingGuard wordingGuard, LiveSessionService live, AppUserRepository users,
            CohortSessionRepository sessions, CurrentUserResolver currentUser, Clock clock) {
        this.courses = courses;
        this.cohorts = cohorts;
        this.enrolments = enrolments;
        this.wordingGuard = wordingGuard;
        this.live = live;
        this.users = users;
        this.sessions = sessions;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TrainingViews.AdminCourseView> all() {
        return courses.findAllByOrderByTrackAscTitleAsc().stream().map(this::view).toList();
    }

    /** The courses a trainer runs cohorts of, with only their own cohorts, for their bookings and assessments. */
    @Transactional(readOnly = true)
    public List<TrainingViews.AdminCourseView> trainerCohorts() {
        CurrentUser me = currentUser.current();
        Predicate<Cohort> mine = h -> LiveSessionService.canHost(me, h);
        return courses.findAllByOrderByTrackAscTitleAsc().stream()
                .map(c -> view(c, mine))
                .filter(c -> !c.cohorts().isEmpty())
                .toList();
    }

    @Transactional
    public TrainingViews.AdminCourseView create(CourseInput r) {
        check(r);
        return view(courses.save(new Course(r.track(), r.title().strip(), r.description().strip(), r.liveSessions(),
                r.sessionMinutes(), r.pricePence(), r.renewalPence(), r.awardsCertificate())));
    }

    @Transactional
    public TrainingViews.AdminCourseView update(Long courseId, CourseInput r) {
        Course course = findCourse(courseId);
        if (r.track() != course.getTrack()) {
            throw new IllegalArgumentException("A course cannot move between the professional and parent tracks. "
                    + "Set up a new course instead.");
        }
        check(r);
        course.revise(r.title().strip(), r.description().strip(), r.liveSessions(), r.sessionMinutes(),
                r.pricePence(), r.renewalPence(), r.awardsCertificate());
        // The number and length of live sessions may have changed.
        cohorts.findByCourseIdOrderByStartsOn(courseId).forEach(live::schedule);
        return view(course);
    }

    @Transactional
    public TrainingViews.AdminCourseView addCohort(Long courseId, CohortInput r) {
        Course course = findCourse(courseId);
        if (r.startsOn().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("A new cohort cannot start in the past");
        }
        Cohort cohort = new Cohort(course, r.name().strip(), r.startsOn(), r.startTime(), r.timeSlot().strip(),
                r.capacity(), r.trainerName().strip(), r.recordingAgreed());
        cohort.assignTrainer(trainer(r.trainerUserId()));
        live.schedule(cohorts.save(cohort));
        return view(course);
    }

    @Transactional
    public TrainingViews.AdminCourseView updateCohort(Long cohortId, CohortInput r) {
        Cohort cohort = cohorts.findById(cohortId).orElseThrow(() -> new NotFoundException("Cohort", cohortId));
        long booked = enrolments.countByCohortId(cohortId);
        if (r.capacity() < booked) {
            throw new IllegalArgumentException(
                    "This cohort already has " + booked + " places booked, so it needs at least that many places");
        }
        cohort.revise(r.name().strip(), r.startsOn(), r.startTime(), r.timeSlot().strip(), r.capacity(),
                r.trainerName().strip(), r.recordingAgreed());
        cohort.assignTrainer(trainer(r.trainerUserId()));
        live.schedule(cohort);
        return view(cohort.getCourse());
    }

    private void check(CourseInput r) {
        if (r.track() == CourseTrack.PARENT && r.awardsCertificate()) {
            throw new IllegalArgumentException("Parent courses do not award a certificate");
        }
        wordingGuard.requireClean(List.of(r.title(), r.description()));
    }

    /** The account chosen to run a cohort: a trainer or a member of the team. Null: none chosen. */
    private AppUser trainer(Long userId) {
        if (userId == null) {
            return null;
        }
        AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("Trainer", userId));
        if (user.getRole() != Role.TRAINER && user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Choose a trainer or a member of the Motor Movers team");
        }
        return user;
    }

    private Course findCourse(Long id) {
        return courses.findById(id).orElseThrow(() -> new NotFoundException("Course", id));
    }

    private TrainingViews.AdminCourseView view(Course c) {
        return view(c, h -> true);
    }

    private TrainingViews.AdminCourseView view(Course c, Predicate<Cohort> shown) {
        List<CohortSession> upcoming = sessions.findByStartsAtAfterOrderByStartsAt(clock.instant());
        return new TrainingViews.AdminCourseView(c.getId(), c.getTrack(), c.getTitle(), c.getDescription(),
                c.getLiveSessions(), c.getSessionMinutes(), c.getPricePence(), c.getRenewalPence(),
                c.isAwardsCertificate(),
                cohorts.findByCourseIdOrderByStartsOn(c.getId()).stream()
                        .filter(shown)
                        .map(h -> new TrainingViews.AdminCohortView(h.getId(), h.getName(), h.getStartsOn(),
                                h.getStartTime(), h.getTimeSlot(), h.getCapacity(),
                                (int) enrolments.countByCohortId(h.getId()), h.getTrainerName(),
                                h.getTrainer() == null ? null : h.getTrainer().getId(), h.isRecordingAgreed(),
                                live.summaries(h.getId()), clashes(h, upcoming)))
                        .toList());
    }

    /**
     * A Zoom login can run one meeting at a time, so sessions still to come that overlap another cohort's on the
     * same login (the trainer's own, or the platform's Zoom host for everyone without one) are named here.
     */
    private List<String> clashes(Cohort cohort, List<CohortSession> upcoming) {
        DateTimeFormatter when = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.UK).withZone(clock.getZone());
        String login = zoomLogin(cohort);
        return upcoming.stream()
                .filter(s -> s.getCohort().getId().equals(cohort.getId()))
                .flatMap(s -> upcoming.stream()
                        .filter(o -> !o.getCohort().getId().equals(cohort.getId()))
                        .filter(o -> Objects.equals(zoomLogin(o.getCohort()), login))
                        .filter(o -> overlap(s, o))
                        .map(o -> "Session " + s.getNumber() + " (" + when.format(s.getStartsAt()) + ") overlaps "
                                + o.getCohort().getCourse().getTitle() + " · " + o.getCohort().getName()
                                + " on the same Zoom login"))
                .toList();
    }

    /** The Zoom login a cohort's meetings run on; null for the platform's Zoom host. */
    private static String zoomLogin(Cohort cohort) {
        return cohort.getTrainer() == null ? null : cohort.getTrainer().getZoomEmail();
    }

    private static boolean overlap(CohortSession a, CohortSession b) {
        Instant aEnd = a.endsAt();
        Instant bEnd = b.endsAt();
        return a.getStartsAt().isBefore(bEnd) && b.getStartsAt().isBefore(aEnd);
    }
}
