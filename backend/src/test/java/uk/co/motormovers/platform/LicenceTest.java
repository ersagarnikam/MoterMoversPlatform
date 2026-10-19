package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumSet;

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
