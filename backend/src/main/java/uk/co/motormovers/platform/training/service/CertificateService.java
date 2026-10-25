package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.payment.dto.PaymentViews;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.model.CourseQuiz;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.repository.CourseQuizRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;
import uk.co.motormovers.platform.training.repository.QuizAttemptRepository;

/**
 * Practitioner certificates: issued once every live session is attended, every quiz passed and the assessment
 * passed; emailed to the learner with the PDF; and a reminder a month before the year runs out.
 */
@Service
public class CertificateService {

    /** How long before expiry the learner is reminded. */
    static final int REMIND_DAYS_BEFORE = 30;

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final EnrolmentRepository enrolments;
    private final CourseQuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final CurrentUserResolver currentUser;
    private final Mailer mailer;
    private final Clock clock;

    public CertificateService(EnrolmentRepository enrolments, CourseQuizRepository quizzes,
            QuizAttemptRepository attempts, CurrentUserResolver currentUser, Mailer mailer, Clock clock) {
        this.enrolments = enrolments;
        this.quizzes = quizzes;
        this.attempts = attempts;
        this.currentUser = currentUser;
        this.mailer = mailer;
        this.clock = clock;
    }

    /** Issues the certificate if everything is now complete, and emails it. Returns whether it was issued now. */
    @Transactional
    public boolean issueIfComplete(Enrolment e) {
        if (!e.issueCertificateIfEligible(LocalDate.now(clock), quizzesPassed(e))) {
            return false;
        }
        Course course = e.getCohort().getCourse();
        mailer.send(e.getUser().getEmail(), "Your Motor Movers certificate",
                "Congratulations, " + e.getUser().getDisplayName() + ".\n\nYou have completed the " + course.getTitle()
                        + ". Your certificate is attached, and on your Training page: "
                        + mailer.link("/training/certificate/" + e.getId()) + "\n\nIt is valid until "
                        + e.getCertificateExpiresOn() + ".\n",
                "motor-movers-certificate.pdf", pdf(e), "application/pdf");
        return true;
    }

    /** Whether the learner has passed every quiz of the course (true when it has none). */
    @Transactional(readOnly = true)
    public boolean quizzesPassed(Enrolment e) {
        return quizzes.findByCourseIdOrderByAfterSessionAscIdAsc(e.getCohort().getCourse().getId()).stream()
                .allMatch(q -> attempts.existsByQuizIdAndEnrolmentIdAndPassedTrue(q.getId(), e.getId()));
    }

    /** How many of the course's quizzes the learner has passed, of how many. */
    @Transactional(readOnly = true)
    public TrainingViews.QuizProgress quizProgress(Enrolment e) {
        List<CourseQuiz> all = quizzes.findByCourseIdOrderByAfterSessionAscIdAsc(e.getCohort().getCourse().getId());
        int passed = (int) all.stream()
                .filter(q -> attempts.existsByQuizIdAndEnrolmentIdAndPassedTrue(q.getId(), e.getId())).count();
        return new TrainingViews.QuizProgress(passed, all.size());
    }

    /** The certificate as a PDF: for the learner, the team, or the cohort's trainer. */
    @Transactional(readOnly = true)
    public byte[] download(Long enrolmentId) {
        Enrolment e = enrolments.findById(enrolmentId).orElseThrow(() -> new NotFoundException("Enrolment", enrolmentId));
        CurrentUser me = currentUser.current();
        if (!e.getUser().getId().equals(me.id()) && !LiveSessionService.canHost(me, e.getCohort())) {
            throw new AccessDeniedException("This is someone else's certificate");
        }
        if (e.getCertificateIssuedOn() == null) {
            throw new IllegalStateException("There is no certificate for this course yet");
        }
        return pdf(e);
    }

    /** Every morning: reminds learners whose certificate runs out within a month, once. */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public int remindExpiring() {
        LocalDate today = LocalDate.now(clock);
        List<Enrolment> due = enrolments.findByCertificateExpiresOnBetweenAndExpiryReminderSentOnIsNull(today,
                today.plusDays(REMIND_DAYS_BEFORE));
        for (Enrolment e : due) {
            Course course = e.getCohort().getCourse();
            String renewal = course.getRenewalPence() == null ? ""
                    : " Renewing costs " + PaymentViews.pounds(course.getRenewalPence()) + ".";
            mailer.send(e.getUser().getEmail(), "Your Motor Movers certificate expires soon",
                    "Hello " + e.getUser().getDisplayName() + ",\n\nYour " + course.getTitle() + " certificate is valid "
                            + "until " + e.getCertificateExpiresOn() + "." + renewal + " To renew it, reply to this "
                            + "email or contact the Motor Movers team.\n\nYour certificate: "
                            + mailer.link("/training/certificate/" + e.getId()) + "\n");
            e.expiryReminderSent(today);
        }
        if (!due.isEmpty()) {
            log.info("Certificate reminders: {} sent", due.size());
        }
        return due.size();
    }

    private byte[] pdf(Enrolment e) {
        return CertificatePdf.render(e, !quizzes.findByCourseIdOrderByAfterSessionAscIdAsc(
                e.getCohort().getCourse().getId()).isEmpty());
    }
}
