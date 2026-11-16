package uk.co.motormovers.platform;

import java.time.Clock;
import java.time.Duration;

import org.springframework.jdbc.core.JdbcTemplate;

import uk.co.motormovers.platform.training.model.Cohort;
import uk.co.motormovers.platform.training.model.CohortSession;
import uk.co.motormovers.platform.training.repository.CohortRepository;
import uk.co.motormovers.platform.training.repository.CohortSessionRepository;

/**
 * For the tests of the "your live session is open" notice ({@code SessionReminderJob.announceOpening}), which only
 * looks at sessions starting in the next 15 minutes. The demo's "starting now" session is usually one of them, but
 * not when the demo was loaded in the last ten minutes of the day (it then starts at once, so it is already in the
 * past) or long before the test runs. A test puts the session in the window itself (task 86).
 */
final class OpeningSession {

    /** The demo cohort Jo is booked on. */
    static final String COHORT = "Cohort A · starting now";

    private OpeningSession() {
    }

    /** Moves that cohort's first session to five minutes from now, with its notice not yet sent. */
    static void inFiveMinutes(CohortRepository cohorts, CohortSessionRepository sessions, JdbcTemplate jdbc,
            Clock clock) {
        Cohort cohort = cohorts.findAll().stream().filter(c -> COHORT.equals(c.getName())).findFirst().orElseThrow();
        CohortSession first = sessions.findByCohortIdOrderByNumber(cohort.getId()).get(0);
        first.reschedule(clock.instant().plus(Duration.ofMinutes(5)), first.getDurationMinutes());
        sessions.save(first);
        // Rescheduling clears the notice only when the time changes, which a fixed clock would not do twice.
        jdbc.update("UPDATE cohort_session SET opening_push_sent_at = NULL WHERE id = ?", first.getId());
    }
}
