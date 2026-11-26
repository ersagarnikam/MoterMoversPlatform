package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.model.Region;
import uk.co.motormovers.platform.common.util.SchoolDays;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.model.LicencePlan;
import uk.co.motormovers.platform.school.model.School;

class LicenceTest {

    private final School school = new School("Test Primary", null, Region.UK);
    private final LocalDate today = LocalDate.of(2026, 10, 15);
    private final LicencePlan fullProgramme = new LicencePlan("FULL_PROGRAMME", "Full programme", false, 4);
    private final LicencePlan freeTrial = new LicencePlan(LicencePlan.FREE_TRIAL, "Free trial", true, 1);

    @Test
    void statusFollowsEndDate() {
        Licence active = licence(fullProgramme, today.plusMonths(6));
        Licence due = licence(fullProgramme, today.plusDays(30));
        Licence expired = licence(fullProgramme, today.minusDays(1));
        Licence trial = licence(freeTrial, today.plusDays(30));

        assertThat(active.statusOn(today)).isEqualTo(Licence.Status.ACTIVE);
        assertThat(due.statusOn(today)).isEqualTo(Licence.Status.RENEWAL_DUE);
        assertThat(expired.statusOn(today)).isEqualTo(Licence.Status.EXPIRED);
        assertThat(trial.statusOn(today)).isEqualTo(Licence.Status.FREE_TRIAL);
    }

    /** Task 88: a licence that has not started is never shown as Active, whatever its plan. */
    @Test
    void aLicenceThatHasNotStartedIsUpcoming() {
        Licence nextMonth = new Licence(school, fullProgramme, 0, today.plusMonths(1), today.plusMonths(13),
                EnumSet.of(ColourStage.RED));
        Licence trialNextWeek = new Licence(school, freeTrial, 0, today.plusDays(7), today.plusDays(37),
                EnumSet.of(ColourStage.RED));
        assertThat(nextMonth.statusOn(today)).isEqualTo(Licence.Status.UPCOMING);
        assertThat(trialNextWeek.statusOn(today)).isEqualTo(Licence.Status.UPCOMING);
        // From its first day it runs, as isValidOn says.
        assertThat(nextMonth.statusOn(today.plusMonths(1))).isEqualTo(Licence.Status.ACTIVE);
        assertThat(nextMonth.isValidOn(today.plusMonths(1))).isTrue();
    }

    /** The licence a school's status is shown from: running today, else the next to start, else the last ended. */
    @Test
    void aSchoolWithSeveralLicencesShowsTheOneThatMatters() {
        Licence ended = licence(fullProgramme, today.minusDays(10));
        Licence olderEnded = licence(fullProgramme, today.minusMonths(13));
        Licence running = licence(fullProgramme, today.plusDays(20));
        Licence renewal = new Licence(school, fullProgramme, 0, today.plusDays(21), today.plusDays(386),
                EnumSet.of(ColourStage.RED));
        Licence later = new Licence(school, fullProgramme, 0, today.plusDays(387), today.plusDays(700),
                EnumSet.of(ColourStage.RED));

        // The one running today, even though the renewal ends later: its status is Renewal due, not Active.
        assertThat(Licence.shownOn(List.of(ended, running, renewal, later), today)).contains(running);
        assertThat(running.statusOn(today)).isEqualTo(Licence.Status.RENEWAL_DUE);
        // None running: the next to start.
        assertThat(Licence.shownOn(List.of(olderEnded, ended, later, renewal), today)).contains(renewal);
        // None running or to come: the last that ended.
        assertThat(Licence.shownOn(List.of(olderEnded, ended), today)).contains(ended);
        assertThat(Licence.shownOn(List.of(), today)).isEmpty();
    }

    @Test
    void validFromItsStartToItsEndDate() {
        Licence licence = new Licence(school, fullProgramme, 0, today, today.plusYears(1), EnumSet.of(ColourStage.RED));
        assertThat(licence.isValidOn(today.minusDays(1))).isFalse();
        assertThat(licence.isValidOn(today)).isTrue();
        assertThat(licence.isValidOn(today.plusMonths(6))).isTrue();
        assertThat(licence.isValidOn(today.plusYears(1))).isTrue();
        assertThat(licence.isValidOn(today.plusYears(1).plusDays(1))).isFalse();
    }

    @Test
    void onlyLicensedStagesOpen() {
        Licence licence = licence(fullProgramme, today.plusYears(1));
        assertThat(licence.covers(ColourStage.ORANGE)).isTrue();
        assertThat(licence.covers(ColourStage.BLUE)).isFalse();
    }

    @Test
    void schoolDaysSkipWeekends() {
        // 15 Oct 2026 is a Thursday; the last 5 school days are Fri 9 to Thu 15.
        assertThat(SchoolDays.lastSchoolDays(today, 5))
                .containsExactly(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13),
                        LocalDate.of(2026, 10, 14), today);
    }

    private Licence licence(LicencePlan plan, LocalDate endsOn) {
        return new Licence(school, plan, 0, endsOn.minusYears(1), endsOn,
                EnumSet.of(ColourStage.RED, ColourStage.ORANGE, ColourStage.YELLOW));
    }
}
