package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.training.dto.CourseInputs.CohortInput;
import uk.co.motormovers.platform.training.dto.CourseInputs.CourseInput;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.CourseTrack;
import uk.co.motormovers.platform.training.repository.CohortRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * The team sets up live courses and opens cohorts for them. Courses go live as soon as they are saved, so the
 * wording check runs on save rather than at a separate sign-off. Mapped under /api/admin (ADMIN only).
 */
@Service
public class CourseAdminService {

    private final CourseRepository courses;
    private final CohortRepository cohorts;
    private final EnrolmentRepository enrolments;
    private final WordingGuard wordingGuard;
    private final LiveSessionService live;
    private final AppUserRepository users;
    private final Clock clock;

    public CourseAdminService(CourseRepository courses, CohortRepository cohorts, EnrolmentRepository enrolments,
            WordingGuard wordingGuard, LiveSessionService live, AppUserRepository users, Clock clock) {
        this.courses = courses;
        this.cohorts = cohorts;
        this.enrolments = enrolments;
        this.wordingGuard = wordingGuard;
        this.live = live;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TrainingViews.AdminCourseView> all() {
        return courses.findAllByOrderByTrackAscTitleAsc().stream().map(this::view).toList();
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
        return new TrainingViews.AdminCourseView(c.getId(), c.getTrack(), c.getTitle(), c.getDescription(),
                c.getLiveSessions(), c.getSessionMinutes(), c.getPricePence(), c.getRenewalPence(),
                c.isAwardsCertificate(),
                cohorts.findByCourseIdOrderByStartsOn(c.getId()).stream()
                        .map(h -> new TrainingViews.AdminCohortView(h.getId(), h.getName(), h.getStartsOn(),
                                h.getStartTime(), h.getTimeSlot(), h.getCapacity(),
                                (int) enrolments.countByCohortId(h.getId()), h.getTrainerName(),
                                h.getTrainer() == null ? null : h.getTrainer().getId(), h.isRecordingAgreed(),
                                live.summaries(h.getId())))
                        .toList());
    }
}
