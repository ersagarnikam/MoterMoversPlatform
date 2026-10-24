package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.CourseTrack;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.repository.CohortRepository;
import uk.co.motormovers.platform.training.repository.CourseRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
import uk.co.motormovers.platform.training.repository.SessionAttendanceRepository;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Live courses for professionals and parents. Phase 0 runs on an off-the-shelf course platform; this
 * module takes over enrolment, attendance and certificates when learners move onto the platform.
 * Online payment is not integrated yet. A place booked by school staff is invoiced to their school.
 */
@Service
public class TrainingService {

    private final CourseRepository courses;
    private final CohortRepository cohorts;
    private final EnrolmentRepository enrolments;
    private final SessionAttendanceRepository attendance;
    private final AppUserRepository users;
    private final PaymentRepository payments;
    private final SchoolRepository schools;
    private final CurrentUserResolver currentUser;
    private final Clock clock;

    public TrainingService(CourseRepository courses, CohortRepository cohorts, EnrolmentRepository enrolments,
            SessionAttendanceRepository attendance, AppUserRepository users, PaymentRepository payments,
            SchoolRepository schools, CurrentUserResolver currentUser, Clock clock) {
        this.courses = courses;
        this.cohorts = cohorts;
        this.enrolments = enrolments;
        this.attendance = attendance;
        this.users = users;
        this.payments = payments;
        this.schools = schools;
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

    @Transactional
    public TrainingViews.EnrolmentView enrol(Long cohortId) {
        CurrentUser me = currentUser.current();
        Cohort cohort = cohorts.findById(cohortId).orElseThrow(() -> new NotFoundException("Cohort", cohortId));
        if (cohort.getCourse().getTrack() != trackFor(me.role())) {
            throw new IllegalArgumentException("This course is not open to your account type");
        }
        return enrolments.findByCohortIdAndUserId(cohortId, me.id())
                .map(this::view)
                .orElseGet(() -> {
                    if (enrolments.countByCohortId(cohortId) >= cohort.getCapacity()) {
                        throw new IllegalStateException("This cohort is full");
                    }
                    Enrolment e = new Enrolment(cohort, users.getReferenceById(me.id()));
                    invoiceSchool(me, cohort);
                    return view(enrolments.save(e));
                });
    }

    @Transactional(readOnly = true)
    public List<TrainingViews.EnrolmentView> mine() {
        return enrolments.findByUserIdOrderByEnrolledAtDesc(currentUser.current().id()).stream()
                .map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<TrainingViews.EnrolmentView> cohortEnrolments(Long cohortId) {
        return enrolments.findByCohortIdOrderByEnrolledAt(cohortId).stream()
                .map(this::view).toList();
    }

    @Transactional
    public TrainingViews.EnrolmentView recordAssessment(Long enrolmentId, boolean passed) {
        Enrolment e = findEnrolment(enrolmentId);
        e.recordAssessment(passed);
        e.issueCertificateIfEligible(LocalDate.now(clock));
        return view(e);
    }

    private TrainingViews.EnrolmentView view(Enrolment e) {
        return TrainingViews.EnrolmentView.of(e, attendance.attendedNumbers(e.getId()));
    }

    /** Staff places are paid by their school: the booking adds a payment due on the school's account. */
    private void invoiceSchool(CurrentUser me, Cohort cohort) {
        int price = cohort.getCourse().getPricePence();
        if (me.schoolId() == null || price <= 0) {
            return;
        }
        payments.save(Payment.bySchool(schools.getReferenceById(me.schoolId()), PaymentPurpose.COURSE,
                cohort.getCourse().getTitle() + " · " + me.displayName() + " (" + cohort.getName() + ")", price,
                LocalDate.now(clock), null, me.displayName() + " (booking)", clock.instant()));
    }

    private Enrolment findEnrolment(Long id) {
        return enrolments.findById(id).orElseThrow(() -> new NotFoundException("Enrolment", id));
    }

    private static CourseTrack trackFor(Role role) {
        return role == Role.PARENT ? CourseTrack.PARENT : CourseTrack.PROFESSIONAL;
    }
}
