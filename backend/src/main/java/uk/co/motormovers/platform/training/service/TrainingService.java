package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.CourseRequest;
import uk.co.motormovers.platform.training.model.CourseTrack;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.repository.CohortRepository;
import uk.co.motormovers.platform.training.repository.CohortSessionRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.CourseRequestRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
import uk.co.motormovers.platform.training.repository.SessionAttendanceRepository;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Live courses for professionals and parents. Phase 0 runs on an off-the-shelf course platform; this
 * module takes over enrolment, attendance and certificates when learners move onto the platform.
 * Online payment is not integrated yet. A school pays for its staff's places: staff ask for a place, their head
 * approves it, and approving books the place and invoices the school. A head books their own place directly.
 */
@Service
public class TrainingService {

    private final CourseRepository courses;
    private final CohortRepository cohorts;
    private final CohortSessionRepository sessions;
    private final EnrolmentRepository enrolments;
    private final CourseRequestRepository requests;
    private final SessionAttendanceRepository attendance;
    private final AppUserRepository users;
    private final PaymentRepository payments;
    private final SchoolRepository schools;
    private final AccessPolicy access;
    private final Mailer mailer;
    private final CertificateService certificates;
    private final CurrentUserResolver currentUser;
    private final Clock clock;

    public TrainingService(CourseRepository courses, CohortRepository cohorts, CohortSessionRepository sessions,
            EnrolmentRepository enrolments, CourseRequestRepository requests, SessionAttendanceRepository attendance,
            AppUserRepository users, PaymentRepository payments, SchoolRepository schools, AccessPolicy access,
            Mailer mailer, CertificateService certificates, CurrentUserResolver currentUser, Clock clock) {
        this.courses = courses;
        this.cohorts = cohorts;
        this.sessions = sessions;
        this.enrolments = enrolments;
        this.requests = requests;
        this.attendance = attendance;
        this.users = users;
        this.payments = payments;
        this.schools = schools;
        this.access = access;
        this.mailer = mailer;
        this.certificates = certificates;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TrainingViews.CourseView> catalogue() {
        LocalDate today = LocalDate.now(clock);
        CurrentUser me = currentUser.current();
        return courses.findAllByOrderByTrackAscTitleAsc().stream()
                .filter(c -> me.is(Role.ADMIN) || c.getTrack() == trackFor(me.role()))
                .map(c -> new TrainingViews.CourseView(c.getId(), c.getTrack(), c.getTitle(), c.getDescription(),
                        c.getLiveSessions(), c.getSessionMinutes(), c.getPricePence(), c.getRenewalPence(),
                        c.isAwardsCertificate(),
                        cohorts.findByCourseIdAndStartsOnGreaterThanEqualOrderByStartsOn(c.getId(), today).stream()
                                .map(h -> new TrainingViews.CohortView(h.getId(), h.getName(), h.getStartsOn(),
                                        h.getTimeSlot(), h.getTrainerName(),
                                        h.getCapacity() - (int) enrolments.countByCohortId(h.getId()),
                                        h.isRecordingAgreed()))
                                .toList()))
                .toList();
    }

    /** Books a place. Staff whose school pays ask for one instead ({@link #requestPlace}). */
    @Transactional
    public TrainingViews.EnrolmentView enrol(Long cohortId) {
        CurrentUser me = currentUser.current();
        Cohort cohort = findCohort(cohortId);
        requireTrack(me, cohort);
        return enrolments.findByCohortIdAndUserId(cohortId, me.id())
                .map(this::view)
                .orElseGet(() -> {
                    if (needsApproval(me, cohort)) {
                        throw new IllegalStateException(
                                "Your school pays for this course, so your head approves the place first. "
                                        + "Ask for a place instead.");
                    }
                    return view(book(cohort, users.getReferenceById(me.id()), me.schoolId(), me.displayName()));
                });
    }

    /** A member of staff asks their head for a place their school will pay for. The school's heads are emailed. */
    @Transactional
    public TrainingViews.CourseRequestView requestPlace(Long cohortId) {
        CurrentUser me = currentUser.current();
        Cohort cohort = findCohort(cohortId);
        requireTrack(me, cohort);
        if (!needsApproval(me, cohort)) {
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
                    users.findBySchoolIdOrderByDisplayName(me.schoolId()).stream()
                            .filter(u -> u.getRole() == Role.HEAD && u.isEnabled())
                            .forEach(head -> mailer.send(head.getEmail(), "A course place to approve",
                                    me.displayName() + " has asked for a place on " + describe(cohort) + ", "
                                            + "which the school would pay for.\n\nApprove or decline it here: "
                                            + mailer.link("/school/payments") + "\n"));
                    return TrainingViews.CourseRequestView.of(request);
                });
    }

    @Transactional(readOnly = true)
    public List<TrainingViews.CourseRequestView> myRequests() {
        return requests.findByUserIdOrderByRequestedAtDesc(currentUser.current().id()).stream()
                .map(TrainingViews.CourseRequestView::of).toList();
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
                .map(TrainingViews.CourseRequestView::of).toList();
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
        if (enrolments.findByCohortIdAndUserId(cohort.getId(), request.getUser().getId()).isEmpty()) {
            book(cohort, request.getUser(), request.getSchool().getId(), me.displayName());
        }
        request.decide(CourseRequest.Status.APPROVED, me.displayName(), clock.instant());
        mailer.send(request.getUser().getEmail(), "Your course place is booked",
                me.displayName() + " approved your place on " + describe(cohort) + ". It is on your Training page: "
                        + mailer.link("/training") + "\n");
        return TrainingViews.CourseRequestView.of(request);
    }

    @Transactional
    public TrainingViews.CourseRequestView decline(Long requestId) {
        CourseRequest request = findRequest(requestId);
        CurrentUser me = access.requireSchoolAccess(request.getSchool());
        request.decide(CourseRequest.Status.DECLINED, me.displayName(), clock.instant());
        mailer.send(request.getUser().getEmail(), "Your course request",
                me.displayName() + " has not approved a place on " + describe(request.getCohort())
                        + " this time. Please speak to them about it.\n");
        return TrainingViews.CourseRequestView.of(request);
    }

    /**
     * Cancels a place, with the school's invoice for it while it is still due. Learners (and their head, for staff)
     * can cancel until the first session starts; the team can cancel any place without a certificate.
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
        if (!team && started(e.getCohort())) {
            throw new IllegalStateException(
                    "The course has started, so please ask the Motor Movers team to cancel this place");
        }
        String note = "";
        Payment payment = e.getPayment();
        if (payment != null && payment.getStatus() == PaymentStatus.DUE) {
            payment.cancel();
            note = " The school's invoice for it is cancelled.";
        } else if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            note = " It was paid already: the Motor Movers team will arrange any refund.";
        }
        String what = describe(e.getCohort());
        enrolments.delete(e);
        return new TrainingViews.CancelView(learner.getDisplayName() + "'s place on " + what + " is cancelled." + note);
    }

    @Transactional(readOnly = true)
    public List<TrainingViews.EnrolmentView> mine() {
        return enrolments.findByUserIdOrderByEnrolledAtDesc(currentUser.current().id()).stream()
                .map(this::view).toList();
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
        requireHost(e.getCohort());
        e.recordAssessment(passed);
        certificates.issueIfComplete(e);
        return view(e);
    }

    private TrainingViews.EnrolmentView view(Enrolment e) {
        return TrainingViews.EnrolmentView.of(e, attendance.attendedNumbers(e.getId()), certificates.quizProgress(e));
    }

    /** Books a place; with a school, the school is invoiced for it (free courses invoice nothing). */
    private Enrolment book(Cohort cohort, AppUser learner, Long schoolId, String bookedBy) {
        requireSeat(cohort);
        Enrolment e = enrolments.save(new Enrolment(cohort, learner));
        int price = cohort.getCourse().getPricePence();
        if (schoolId != null && price > 0) {
            e.invoicedWith(payments.save(Payment.bySchool(schools.getReferenceById(schoolId), PaymentPurpose.COURSE,
                    cohort.getCourse().getTitle() + " · " + learner.getDisplayName() + " (" + cohort.getName() + ")",
                    price, LocalDate.now(clock), null, bookedBy + " (booking)", clock.instant())));
        }
        return e;
    }

    /** Staff other than heads need their head's approval for a place their school pays for. */
    private static boolean needsApproval(CurrentUser me, Cohort cohort) {
        return me.schoolId() != null && !me.is(Role.HEAD) && cohort.getCourse().getPricePence() > 0;
    }

    private void requireSeat(Cohort cohort) {
        if (enrolments.countByCohortId(cohort.getId()) >= cohort.getCapacity()) {
            throw new IllegalStateException("This cohort is full");
        }
    }

    private boolean started(Cohort cohort) {
        Instant now = clock.instant();
        return sessions.findByCohortIdOrderByNumber(cohort.getId()).stream().findFirst()
                .map(s -> !now.isBefore(s.getStartsAt()))
                .orElse(!LocalDate.now(clock).isBefore(cohort.getStartsOn()));
    }

    private void requireHost(Cohort cohort) {
        if (!LiveSessionService.canHost(currentUser.current(), cohort)) {
            throw new AccessDeniedException("Only the cohort's trainer or the Motor Movers team can do this");
        }
    }

    private static void requireTrack(CurrentUser me, Cohort cohort) {
        if (cohort.getCourse().getTrack() != trackFor(me.role())) {
            throw new IllegalArgumentException("This course is not open to your account type");
        }
    }

    private static String describe(Cohort cohort) {
        return cohort.getCourse().getTitle() + " (" + cohort.getName() + ", starting " + cohort.getStartsOn() + ")";
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
