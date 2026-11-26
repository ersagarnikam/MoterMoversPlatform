package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.config.CountrySettings;
import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.model.PaymentStatus;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.config.LiveCourses;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.CourseRequest;
import uk.co.motormovers.platform.training.model.CourseTrack;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.repository.CohortRepository;
import uk.co.motormovers.platform.training.repository.CohortSessionRepository;
import uk.co.motormovers.platform.training.repository.CourseQuizRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.CourseRequestRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
import uk.co.motormovers.platform.training.repository.SessionAttendanceRepository;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Courses for professionals and parents, and places on them. Courses are recorded (go-live task 89): a learner has a
 * place on the course itself and does its lessons in order ({@link CourseLessonService}). Live cohorts on Zoom are
 * kept behind a setting that is off ({@link LiveCourses}); a place on one belongs to its cohort. A school pays for
 * its staff's places: staff ask for a place, their head approves it, and approving books the place and invoices the
 * school. A head books their own place directly. Parents and practitioners book directly and pay on Payments.
 */
@Service
public class TrainingService {

    private final CourseRepository courses;
    private final CohortRepository cohorts;
    private final CohortSessionRepository sessions;
    private final EnrolmentRepository enrolments;
    private final CourseRequestRepository requests;
    private final SessionAttendanceRepository attendance;
    private final CourseQuizRepository quizzes;
    private final AppUserRepository users;
    private final PaymentRepository payments;
    private final SchoolRepository schools;
    private final AccessPolicy access;
    private final Mailer mailer;
    private final CertificateService certificates;
    private final CourseLessonService lessons;
    private final LiveCourses live;
    private final CurrentUserResolver currentUser;
    private final Clock clock;
    private final CountrySettings country;

    public TrainingService(CourseRepository courses, CohortRepository cohorts, CohortSessionRepository sessions,
            EnrolmentRepository enrolments, CourseRequestRepository requests, SessionAttendanceRepository attendance,
            CourseQuizRepository quizzes, AppUserRepository users, PaymentRepository payments,
            SchoolRepository schools, AccessPolicy access, Mailer mailer, CertificateService certificates,
            CourseLessonService lessons, LiveCourses live, CurrentUserResolver currentUser, Clock clock,
            CountrySettings country) {
        this.courses = courses;
        this.cohorts = cohorts;
        this.sessions = sessions;
        this.enrolments = enrolments;
        this.requests = requests;
        this.attendance = attendance;
        this.quizzes = quizzes;
        this.users = users;
        this.payments = payments;
        this.schools = schools;
        this.access = access;
        this.mailer = mailer;
        this.certificates = certificates;
        this.lessons = lessons;
        this.live = live;
        this.currentUser = currentUser;
        this.clock = clock;
        this.country = country;
    }

    /** The courses open to the person, each with its recorded lessons and, with live courses on, its cohorts. */
    @Transactional(readOnly = true)
    public List<TrainingViews.CourseView> catalogue() {
        LocalDate today = LocalDate.now(clock);
        CurrentUser me = currentUser.current();
        return courses.findAllByOrderByTrackAscTitleAsc().stream()
                .filter(c -> me.is(Role.ADMIN) || c.getTrack() == trackFor(me.role()))
                .map(c -> new TrainingViews.CourseView(c.getId(), c.getTrack(), c.getTitle(), c.getDescription(),
                        c.getLiveSessions(), c.getSessionMinutes(), c.getPricePence(), c.getRenewalPence(),
                        c.isAwardsCertificate(), live.on() ? upcoming(c, today) : List.of(),
                        lessons.approvedLessons(c), lessons.approvedMinutes(c),
                        quizzes.findByCourseIdOrderByAfterSessionAscIdAsc(c.getId()).size(), place(me, c),
                        needsApproval(me, c)))
                .toList();
    }

    private List<TrainingViews.CohortView> upcoming(Course c, LocalDate today) {
        return cohorts.findByCourseIdAndStartsOnGreaterThanEqualOrderByStartsOn(c.getId(), today).stream()
                .map(h -> new TrainingViews.CohortView(h.getId(), h.getName(), h.getStartsOn(), h.getTimeSlot(),
                        h.getTrainerName(), h.getCapacity() - (int) enrolments.countByCohortId(h.getId()),
                        h.isRecordingAgreed()))
                .toList();
    }

    /** Where the person stands on a recorded course: BOOKED, REQUESTED (asked of their head) or NONE. */
    private String place(CurrentUser me, Course c) {
        if (enrolments.findByCourseIdAndUserIdAndCohortIsNull(c.getId(), me.id()).isPresent()) {
            return "BOOKED";
        }
        return requests.findByCourseIdAndUserIdAndStatusAndCohortIsNull(c.getId(), me.id(),
                CourseRequest.Status.REQUESTED).isPresent() ? "REQUESTED" : "NONE";
    }

    // --- Recorded courses: a place on the course itself --------------------------------------------------------------

    /** Books a place on a recorded course. Staff whose school pays ask for one instead ({@link #requestCourse}). */
    @Transactional
    public TrainingViews.EnrolmentView enrolOnCourse(Long courseId) {
        CurrentUser me = currentUser.current();
        Course course = findCourse(courseId);
        requireTrack(me, course);
        return enrolments.findByCourseIdAndUserIdAndCohortIsNull(courseId, me.id())
                .map(this::view)
                .orElseGet(() -> {
                    requireLessons(course);
                    if (needsApproval(me, course)) {
                        throw new IllegalStateException(
                                "Your school pays for this course, so your head approves the place first. "
                                        + "Ask for a place instead.");
                    }
                    return view(book(course, null, users.getReferenceById(me.id()), me.schoolId(),
                            me.displayName()));
                });
    }

    /** A member of staff asks their head for a place on a recorded course. The school's heads are emailed. */
    @Transactional
    public TrainingViews.CourseRequestView requestCourse(Long courseId) {
        CurrentUser me = currentUser.current();
        Course course = findCourse(courseId);
        requireTrack(me, course);
        if (!needsApproval(me, course)) {
            throw new IllegalArgumentException("You can book this place directly");
        }
        if (enrolments.findByCourseIdAndUserIdAndCohortIsNull(courseId, me.id()).isPresent()) {
            throw new IllegalStateException("You already have a place on this course");
        }
        return requests.findByCourseIdAndUserIdAndStatusAndCohortIsNull(courseId, me.id(),
                CourseRequest.Status.REQUESTED)
                .map(TrainingViews.CourseRequestView::of)
                .orElseGet(() -> {
                    requireLessons(course);
                    CourseRequest request = requests.save(new CourseRequest(course, users.getReferenceById(me.id()),
                            schools.getReferenceById(me.schoolId()), clock.instant()));
                    tellHeads(me, describe(course, null));
                    return TrainingViews.CourseRequestView.of(request);
                });
    }

    /** Everyone with a place on a recorded course, for the team. */
    @Transactional(readOnly = true)
    public List<TrainingViews.EnrolmentView> courseEnrolments(Long courseId) {
        access.requireAdmin();
        findCourse(courseId);
        return enrolments.findByCourseIdAndCohortIsNullOrderByEnrolledAt(courseId).stream().map(this::view).toList();
    }

    // --- Live courses: a place on a cohort (only with live courses switched on) --------------------------------------

    /** Books a place. Staff whose school pays ask for one instead ({@link #requestPlace}). */
    @Transactional
    public TrainingViews.EnrolmentView enrol(Long cohortId) {
        CurrentUser me = currentUser.current();
        Cohort cohort = findCohort(cohortId);
        requireTrack(me, cohort.getCourse());
        return enrolments.findByCohortIdAndUserId(cohortId, me.id())
                .map(this::view)
                .orElseGet(() -> {
                    if (needsApproval(me, cohort.getCourse())) {
                        throw new IllegalStateException(
                                "Your school pays for this course, so your head approves the place first. "
                                        + "Ask for a place instead.");
                    }
                    return view(book(cohort.getCourse(), cohort, users.getReferenceById(me.id()), me.schoolId(),
                            me.displayName()));
                });
    }

    /** A member of staff asks their head for a place their school will pay for. The school's heads are emailed. */
    @Transactional
    public TrainingViews.CourseRequestView requestPlace(Long cohortId) {
        CurrentUser me = currentUser.current();
        Cohort cohort = findCohort(cohortId);
        requireTrack(me, cohort.getCourse());
        if (!needsApproval(me, cohort.getCourse())) {
            throw new IllegalArgumentException("You can book this place directly");
        }
        if (enrolments.findByCohortIdAndUserId(cohortId, me.id()).isPresent()) {
            throw new IllegalStateException("You already have a place on this cohort");
        }
        return requests.findByCohortIdAndUserIdAndStatus(cohortId, me.id(), CourseRequest.Status.REQUESTED)
                .map(TrainingViews.CourseRequestView::of)
                .orElseGet(() -> {
                    requireSeat(cohort);
                    CourseRequest request = requests.save(new CourseRequest(cohort, users.getReferenceById(me.id()),
                            schools.getReferenceById(me.schoolId()), clock.instant()));
                    tellHeads(me, describe(cohort.getCourse(), cohort));
                    return TrainingViews.CourseRequestView.of(request);
                });
    }

    // --- Requests, for both kinds ------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TrainingViews.CourseRequestView> myRequests() {
        return requests.findByUserIdOrderByRequestedAtDesc(currentUser.current().id()).stream()
                .filter(this::shown).map(TrainingViews.CourseRequestView::of).toList();
    }

    @Transactional
    public TrainingViews.CourseRequestView withdraw(Long requestId) {
        CurrentUser me = currentUser.current();
        CourseRequest request = findRequest(requestId);
        if (!request.getUser().getId().equals(me.id())) {
            throw new AccessDeniedException("You can withdraw only your own request");
        }
        request.decide(CourseRequest.Status.WITHDRAWN, me.displayName(), clock.instant());
        return TrainingViews.CourseRequestView.of(request);
    }

    /** Requests from a school's staff, newest first. Its heads, and the team. */
    @Transactional(readOnly = true)
    public List<TrainingViews.CourseRequestView> schoolRequests(Long schoolId) {
        School school = schools.findById(schoolId).orElseThrow(() -> new NotFoundException("School", schoolId));
        access.requireSchoolAccess(school);
        return requests.findBySchoolIdOrderByRequestedAtDesc(schoolId).stream()
                .filter(this::shown).map(TrainingViews.CourseRequestView::of).toList();
    }

    /** Books the place and invoices the school, then tells the member of staff. */
    @Transactional
    public TrainingViews.CourseRequestView approve(Long requestId) {
        CourseRequest request = findRequest(requestId);
        CurrentUser me = access.requireSchoolAccess(request.getSchool());
        if (request.getStatus() != CourseRequest.Status.REQUESTED) {
            throw new IllegalStateException("This request has already been " + request.getStatus().name().toLowerCase());
        }
        Cohort cohort = request.getCohort();
        Course course = request.getCourse();
        Long learner = request.getUser().getId();
        boolean booked = cohort == null
                ? enrolments.findByCourseIdAndUserIdAndCohortIsNull(course.getId(), learner).isPresent()
                : enrolments.findByCohortIdAndUserId(cohort.getId(), learner).isPresent();
        if (!booked) {
            book(course, cohort, request.getUser(), request.getSchool().getId(), me.displayName());
        }
        request.decide(CourseRequest.Status.APPROVED, me.displayName(), clock.instant());
        mailer.send(request.getUser().getEmail(), "Your course place is booked",
                me.displayName() + " approved your place on " + describe(course, cohort)
                        + ". It is on your Training page: " + mailer.link("/training") + "\n");
        return TrainingViews.CourseRequestView.of(request);
    }

    @Transactional
    public TrainingViews.CourseRequestView decline(Long requestId) {
        CourseRequest request = findRequest(requestId);
        CurrentUser me = access.requireSchoolAccess(request.getSchool());
        request.decide(CourseRequest.Status.DECLINED, me.displayName(), clock.instant());
        mailer.send(request.getUser().getEmail(), "Your course request",
                me.displayName() + " has not approved a place on " + describe(request.getCourse(), request.getCohort())
                        + " this time. Please speak to them about it.\n");
        return TrainingViews.CourseRequestView.of(request);
    }

    /**
     * Cancels a place, with the school's invoice for it while it is still due. Learners (and their head, for staff)
     * can cancel until they have started: on a recorded course until the first lesson is done, on a live one until
     * the first session starts. The team can cancel any place without a certificate.
     */
    @Transactional
    public TrainingViews.CancelView cancel(Long enrolmentId) {
        CurrentUser me = currentUser.current();
        Enrolment e = findEnrolment(enrolmentId);
        AppUser learner = e.getUser();
        boolean team = me.is(Role.ADMIN);
        boolean own = learner.getId().equals(me.id());
        boolean theirHead = me.is(Role.HEAD) && learner.getSchoolId() != null
                && Objects.equals(learner.getSchoolId(), me.schoolId());
        if (!team && !own && !theirHead) {
            throw new AccessDeniedException("You can cancel only your own place");
        }
        if (e.getCertificateIssuedOn() != null) {
            throw new IllegalStateException("The certificate has been issued, so this place cannot be cancelled");
        }
        if (!team && started(e)) {
            throw new IllegalStateException(
                    "The course has started, so please ask the programme team to cancel this place");
        }
        String note = "";
        Payment payment = e.getPayment();
        if (payment != null && payment.getStatus() == PaymentStatus.DUE) {
            payment.cancel();
            note = " The school's invoice for it is cancelled.";
        } else if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            note = " It was paid already: the programme team will arrange any refund.";
        }
        String what = describe(e.getCourse(), e.getCohort());
        enrolments.delete(e);
        return new TrainingViews.CancelView(learner.getDisplayName() + "'s place on " + what + " is cancelled." + note);
    }

    /** The person's places. With live courses off, a place on a cohort is not shown: there is nothing to do on it. */
    @Transactional(readOnly = true)
    public List<TrainingViews.EnrolmentView> mine() {
        return enrolments.findByUserIdOrderByEnrolledAtDesc(currentUser.current().id()).stream()
                .filter(e -> e.isRecorded() || live.on()).map(this::view).toList();
    }

    /** Who is booked on a cohort. The team, or the cohort's trainer. */
    @Transactional(readOnly = true)
    public List<TrainingViews.EnrolmentView> cohortEnrolments(Long cohortId) {
        requireHost(findCohort(cohortId));
        return enrolments.findByCohortIdOrderByEnrolledAt(cohortId).stream()
                .map(this::view).toList();
    }

    /** The end-of-course assessment, which with full attendance issues the certificate. The team or the trainer. */
    @Transactional
    public TrainingViews.EnrolmentView recordAssessment(Long enrolmentId, boolean passed) {
        Enrolment e = findEnrolment(enrolmentId);
        if (e.isRecorded()) {
            throw new IllegalArgumentException("A recorded course has no assessment by a trainer");
        }
        requireHost(e.getCohort());
        e.recordAssessment(passed);
        certificates.issueIfComplete(e);
        return view(e);
    }

    private TrainingViews.EnrolmentView view(Enrolment e) {
        return TrainingViews.EnrolmentView.of(e, e.getId() == null ? List.of() : attendance.attendedNumbers(e.getId()),
                certificates.quizProgress(e), certificates.lessonProgress(e),
                e.getCertificateIssuedOn() == null && !started(e));
    }

    /**
     * Books a place, on the course itself or (a live course) on a cohort. With a school, the school is invoiced for
     * it (free courses invoice nothing).
     */
    private Enrolment book(Course course, Cohort cohort, AppUser learner, Long schoolId, String bookedBy) {
        if (cohort != null) {
            requireSeat(cohort);
        }
        Enrolment e = enrolments.save(cohort == null ? new Enrolment(course, learner) : new Enrolment(cohort, learner));
        int price = course.getPricePence();
        if (schoolId != null && price > 0) {
            Payment invoice = Payment.bySchool(schools.getReferenceById(schoolId), PaymentPurpose.COURSE,
                    course.getTitle() + " · " + learner.getDisplayName()
                            + (cohort == null ? "" : " (" + cohort.getName() + ")"),
                    price, LocalDate.now(clock), null, bookedBy + " (booking)", clock.instant());
            invoice.includesTaxAt(country.taxRateBp());
            e.invoicedWith(payments.save(invoice));
        }
        return e;
    }

    private void tellHeads(CurrentUser me, String what) {
        users.findBySchoolIdOrderByDisplayName(me.schoolId()).stream()
                .filter(u -> u.getRole() == Role.HEAD && u.isEnabled())
                .forEach(head -> mailer.send(head.getEmail(), "A course place to approve",
                        me.displayName() + " has asked for a place on " + what + ", "
                                + "which the school would pay for.\n\nApprove or decline it here: "
                                + mailer.link("/school/payments") + "\n"));
    }

    /** A request for a cohort is shown only while live courses are on. */
    private boolean shown(CourseRequest request) {
        return request.getCohort() == null || live.on();
    }

    /** Staff other than heads need their head's approval for a place their school pays for. */
    private static boolean needsApproval(CurrentUser me, Course course) {
        return me.schoolId() != null && !me.is(Role.HEAD) && !me.is(Role.ADMIN) && course.getPricePence() > 0;
    }

    /** A course with no approved lessons yet cannot be booked: there would be nothing to do. */
    private void requireLessons(Course course) {
        if (lessons.approvedLessons(course) == 0) {
            throw new IllegalStateException("This course is being prepared. Please try again soon.");
        }
    }

    private void requireSeat(Cohort cohort) {
        if (enrolments.countByCohortId(cohort.getId()) >= cohort.getCapacity()) {
            throw new IllegalStateException("This cohort is full");
        }
    }

    private boolean started(Enrolment e) {
        if (e.isRecorded()) {
            return e.getId() != null && lessons.started(e);
        }
        Cohort cohort = e.getCohort();
        Instant now = clock.instant();
        return sessions.findByCohortIdOrderByNumber(cohort.getId()).stream().findFirst()
                .map(s -> !now.isBefore(s.getStartsAt()))
                .orElse(!LocalDate.now(clock).isBefore(cohort.getStartsOn()));
    }

    private void requireHost(Cohort cohort) {
        if (!LiveSessionService.canHost(currentUser.current(), cohort)) {
            throw new AccessDeniedException("Only the cohort's trainer or the programme team can do this");
        }
    }

    private static void requireTrack(CurrentUser me, Course course) {
        if (course.getTrack() != trackFor(me.role())) {
            throw new IllegalArgumentException("This course is not open to your account type");
        }
    }

    private static String describe(Course course, Cohort cohort) {
        return cohort == null ? "the " + course.getTitle()
                : course.getTitle() + " (" + cohort.getName() + ", starting " + cohort.getStartsOn() + ")";
    }

    private Course findCourse(Long id) {
        return courses.findById(id).orElseThrow(() -> new NotFoundException("Course", id));
    }

    private Cohort findCohort(Long id) {
        return cohorts.findById(id).orElseThrow(() -> new NotFoundException("Cohort", id));
    }

    private CourseRequest findRequest(Long id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException("Course request", id));
    }

    private Enrolment findEnrolment(Long id) {
        return enrolments.findById(id).orElseThrow(() -> new NotFoundException("Enrolment", id));
    }

    private static CourseTrack trackFor(Role role) {
        return role == Role.PARENT ? CourseTrack.PARENT : CourseTrack.PROFESSIONAL;
    }
}
