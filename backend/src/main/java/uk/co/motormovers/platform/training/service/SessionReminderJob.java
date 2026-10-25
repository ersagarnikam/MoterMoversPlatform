package uk.co.motormovers.platform.training.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.CohortSession;
import uk.co.motormovers.platform.training.model.Enrolment;
import uk.co.motormovers.platform.training.repository.CohortSessionRepository;
import uk.co.motormovers.platform.training.repository.EnrolmentRepository;

/**
 * Emails everyone booked on a live session, and its trainer, a day before it starts: the time, how to join and the
 * rules. Each session is reminded once; moving it to a new time reminds again.
 */
@Component
public class SessionReminderJob {

    /** How far ahead learners are reminded. */
    static final Duration AHEAD = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(SessionReminderJob.class);

    private final CohortSessionRepository sessions;
    private final EnrolmentRepository enrolments;
    private final Mailer mailer;
    private final Clock clock;

    public SessionReminderJob(CohortSessionRepository sessions, EnrolmentRepository enrolments, Mailer mailer,
            Clock clock) {
        this.sessions = sessions;
        this.enrolments = enrolments;
        this.mailer = mailer;
        this.clock = clock;
    }

    /** Every ten minutes. Returns how many emails were sent. */
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public int remind() {
        Instant now = clock.instant();
        List<CohortSession> due = sessions.findByStartsAtBetweenAndReminderSentAtIsNullOrderByStartsAt(now,
                now.plus(AHEAD));
        int sent = 0;
        for (CohortSession s : due) {
            Cohort cohort = s.getCohort();
            String what = cohort.getCourse().getTitle() + " · " + cohort.getName() + ", live session " + s.getNumber()
                    + " of " + cohort.getCourse().getLiveSessions();
            String when = DateTimeFormatter.ofPattern("EEEE d MMMM 'at' HH:mm", Locale.UK).withZone(clock.getZone())
                    .format(s.getStartsAt()) + " (UK time), for " + s.getDurationMinutes() + " minutes";
            for (Enrolment e : enrolments.findByCohortIdOrderByEnrolledAt(cohort.getId())) {
                mailer.send(e.getUser().getEmail(), "Reminder: your live session " + s.getNumber() + " is coming up",
                        "Hello " + e.getUser().getDisplayName() + ",\n\n" + what + " is on " + when + ".\n\n"
                                + "Join from your Training page from 15 minutes before the start: "
                                + mailer.link("/training") + "\n\n" + TrainingViews.LIVE_SESSION_RULES + "\n");
                sent++;
            }
            if (cohort.getTrainer() != null && cohort.getTrainer().isEnabled()) {
                mailer.send(cohort.getTrainer().getEmail(), "Reminder: you are running live session " + s.getNumber(),
                        what + " is on " + when + ".\n\nStart it from Live sessions: " + mailer.link("/live") + "\n");
                sent++;
            }
            s.reminderSent(now);
        }
        if (sent > 0) {
            log.info("Live session reminders: {} emails for {} sessions", sent, due.size());
        }
        return sent;
    }
}
