package uk.co.motormovers.platform.dashboard.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.repository.GuidanceArticleRepository;
import uk.co.motormovers.platform.content.repository.HomeActivityRepository;
import uk.co.motormovers.platform.content.repository.LibraryResourceRepository;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentStatus;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.training.model.CohortSession;
import uk.co.motormovers.platform.training.model.CourseRequest;
import uk.co.motormovers.platform.training.repository.CohortSessionRepository;
import uk.co.motormovers.platform.training.repository.CourseRequestRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
import uk.co.motormovers.platform.training.repository.InductionLessonRepository;
import uk.co.motormovers.platform.training.service.CourseAdminService;

/**
 * What needs the Motor Movers team's attention today, for their home page: content waiting for sign-off, today's
 * live sessions, invoices overdue, licences to renew, Zoom clashes, staff course requests and certificates running
 * out.
 */
@Service
public class TeamOverviewService {

    /** An invoice still due this long after it was sent is overdue. */
    static final int OVERDUE_DAYS = 30;
    /** Licences ending this soon are listed to renew. */
    static final int RENEWAL_DAYS = 60;
    /** Certificates running out this soon are listed. */
    static final int CERTIFICATE_DAYS = 30;

    public record SessionToday(Long id, String courseTitle, String cohortName, int number, Instant startsAt,
            String trainerName, int booked) {
    }

    public record OverdueInvoice(Long id, String payerName, String description, int amountPence, LocalDate invoicedOn) {
    }

    public record Renewal(Long schoolId, String schoolName, LocalDate endsOn) {
    }

    public record ExpiringCertificate(Long enrolmentId, String learnerName, String courseTitle, LocalDate expiresOn) {
    }

    public record TeamOverview(long draftsAwaiting, List<SessionToday> sessionsToday, List<OverdueInvoice> overdue,
            long dueTotalPence, List<Renewal> renewals, int clashingCohorts, long courseRequestsWaiting,
            List<ExpiringCertificate> certificatesExpiring) {
    }

    private final ProgrammeSessionRepository sessionsContent;
    private final HomeActivityRepository homeActivities;
    private final GuidanceArticleRepository guidance;
    private final LibraryResourceRepository resources;
    private final InductionLessonRepository induction;
    private final CohortSessionRepository liveSessions;
    private final EnrolmentRepository enrolments;
    private final PaymentRepository payments;
    private final LicenceRepository licences;
    private final CourseRequestRepository requests;
    private final CourseAdminService courses;
    private final Clock clock;

    public TeamOverviewService(ProgrammeSessionRepository sessionsContent, HomeActivityRepository homeActivities,
            GuidanceArticleRepository guidance, LibraryResourceRepository resources,
            InductionLessonRepository induction, CohortSessionRepository liveSessions, EnrolmentRepository enrolments,
            PaymentRepository payments, LicenceRepository licences, CourseRequestRepository requests,
            CourseAdminService courses, Clock clock) {
        this.sessionsContent = sessionsContent;
        this.homeActivities = homeActivities;
        this.guidance = guidance;
        this.resources = resources;
        this.induction = induction;
        this.liveSessions = liveSessions;
        this.enrolments = enrolments;
        this.payments = payments;
        this.licences = licences;
        this.requests = requests;
        this.courses = courses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TeamOverview overview() {
        LocalDate today = LocalDate.now(clock);
        long drafts = sessionsContent.countByStatus(ContentStatus.DRAFT) + homeActivities.countByStatus(ContentStatus.DRAFT)
                + guidance.countByStatus(ContentStatus.DRAFT) + resources.countByStatus(ContentStatus.DRAFT)
                + induction.countByStatus(ContentStatus.DRAFT);

        Instant dayStart = today.atStartOfDay(clock.getZone()).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        List<SessionToday> sessionsToday = liveSessions.findByStartsAtAfterOrderByStartsAt(dayStart.minusSeconds(1))
                .stream()
                .filter(s -> s.getStartsAt().isBefore(dayEnd))
                .map(this::today)
                .toList();

        List<Payment> due = payments.findAllByOrderByInvoicedOnDescIdDesc().stream()
                .filter(p -> p.getStatus() == PaymentStatus.DUE).toList();
        List<OverdueInvoice> overdue = due.stream()
                .filter(p -> p.getInvoicedOn().isBefore(today.minusDays(OVERDUE_DAYS)))
                .sorted(Comparator.comparing(Payment::getInvoicedOn))
                .map(p -> new OverdueInvoice(p.getId(),
                        p.getSchool() != null ? p.getSchool().getName() : p.getPayer().getDisplayName(),
                        p.getDescription(), p.getAmountPence(), p.getInvoicedOn()))
                .toList();

        // Each school's latest licence, when it ends soon.
        BinaryOperator<Licence> later = (a, b) -> a.getEndsOn().isAfter(b.getEndsOn()) ? a : b;
        Map<Long, Licence> latest = licences.findAll().stream()
                .collect(Collectors.toMap(l -> l.getSchool().getId(), l -> l, later));
        List<Renewal> renewals = latest.values().stream()
                .filter(l -> !l.getEndsOn().isBefore(today) && !l.getEndsOn().isAfter(today.plusDays(RENEWAL_DAYS)))
                .sorted(Comparator.comparing(Licence::getEndsOn))
                .map(l -> new Renewal(l.getSchool().getId(), l.getSchool().getName(), l.getEndsOn()))
                .toList();

        int clashing = (int) courses.all().stream().flatMap(c -> c.cohorts().stream())
                .filter(h -> !h.clashes().isEmpty()).count();

        List<ExpiringCertificate> expiring = enrolments
                .findByCertificateExpiresOnBetweenOrderByCertificateExpiresOn(today, today.plusDays(CERTIFICATE_DAYS))
                .stream()
                .map(e -> new ExpiringCertificate(e.getId(), e.getUser().getDisplayName(),
                        e.getCohort().getCourse().getTitle(), e.getCertificateExpiresOn()))
                .toList();

        return new TeamOverview(drafts, sessionsToday, overdue, due.stream().mapToLong(Payment::getAmountPence).sum(),
                renewals, clashing, requests.countByStatus(CourseRequest.Status.REQUESTED), expiring);
    }

    private SessionToday today(CohortSession s) {
        return new SessionToday(s.getId(), s.getCohort().getCourse().getTitle(), s.getCohort().getName(), s.getNumber(),
                s.getStartsAt(), s.getCohort().getTrainerName(), (int) enrolments.countByCohortId(s.getCohort().getId()));
    }
}
