package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.model.Region;
import uk.co.motormovers.platform.home.repository.HomePassPriceRepository;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.model.LicencePlanPrice;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.repository.LicencePlanPriceRepository;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.school.service.PlanService;
import uk.co.motormovers.platform.seed.LaunchPrices;
import uk.co.motormovers.platform.training.model.Course;
import uk.co.motormovers.platform.training.repository.CourseRepository;

/**
 * The launch prices (go-live task 85) on a UK deployment: Kay's plans at the UK's prices from the first start, set
 * once, and never over a price the team has set. Ireland's and Australia's prices are checked in RegionIrelandTest and
 * RegionAustraliaTest, each started in its own country.
 */
// Its own database: these tests change the price history, which the other tests read.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:launchprices;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
class LaunchPricesTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    LaunchPrices launchPrices;
    @Autowired
    PlanService plans;
    @Autowired
    LicencePlanPriceRepository planPrices;
    @Autowired
    HomePassPriceRepository passPrices;
    @Autowired
    CourseRepository courses;
    @Autowired
    SchoolRepository schools;
    @Autowired
    LicenceRepository licences;
    @Autowired
    Clock clock;

    /** Checks a deployment's prices, whichever country it started in. Amounts in the minor unit. */
    static void assertPrices(PlanService plans, CourseRepository courses, HomePassPriceRepository passPrices,
            LocalDate today, int childcare, int school, int group, int practitioner, int renewal, int parentCourse,
            int homePass) {
        assertThat(plans.priceOn("EARLY_YEARS", today)).as("childcare centre or kindergarten").isEqualTo(childcare);
        assertThat(plans.priceOn("PRIMARY_CORE", today)).as("school").isEqualTo(school);
        assertThat(plans.priceOn("TRUST", today)).as("group of schools").isEqualTo(group);
        assertThat(group * 10).as("groups pay 10% less than a school").isEqualTo(school * 9);
        assertThat(plans.priceOn("FREE_TRIAL", today)).isZero();
        Course practitionerCourse = course(courses, "Practitioner course");
        assertThat(practitionerCourse.getPricePence()).isEqualTo(practitioner);
        assertThat(practitionerCourse.getRenewalPence()).isEqualTo(renewal);
        assertThat(course(courses, "Parent course").getPricePence()).isEqualTo(parentCourse);
        assertThat(passPrices.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(today).orElseThrow()
                .getPricePence()).as("home pass").isEqualTo(homePass);
    }

    private static Course course(CourseRepository courses, String title) {
        return courses.findAll().stream().filter(c -> c.getTitle().equals(title)).findFirst().orElseThrow();
    }

    @Autowired
    uk.co.motormovers.platform.home.repository.HomeMonthlyPriceRepository monthlyPrices;

    /** Task 68: no country has a monthly price yet, so none is applied and monthly is not on sale. */
    @Test
    void theHomeProgrammesMonthlyPriceWaitsForTheTeam() throws Exception {
        assertThat(launchPrices.apply()).isZero();
        assertThat(monthlyPrices.count()).isZero();
    }

    @Test
    void aUkDeploymentStartsWithTheUkPrices() {
        assertPrices(plans, courses, passPrices, today(), 39_500, 63_500, 57_150, 10_500, 3_000, 5_500, 53_000);
    }

    @Test
    void thePlansAreKaysAndTheFullProgrammeIsRetired() throws Exception {
        mvc.perform(get("/api/plans")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(contains("FREE_TRIAL", "EARLY_YEARS", "PRIMARY_CORE", "TRUST")))
                .andExpect(jsonPath("$[*].label").value(contains("Free trial", "Childcare centre or kindergarten",
                        "School", "Group of schools")))
                .andExpect(jsonPath("$[*].annualPricePence").value(contains(0, 39_500, 63_500, 57_150)));
        // Retired, not removed: its code and its old price stay for any licence that holds it.
        assertThat(plans.find("FULL_PROGRAMME").isActive()).isFalse();
        assertThat(plans.priceOn("FULL_PROGRAMME", today())).isEqualTo(175_000);
    }

    @Test
    void theHistoryKeepsTheOldPriceAndNamesTheLaunchPrice() {
        List<LicencePlanPrice> history = planPrices.findByPlanCodeOrderByEffectiveFromDesc("PRIMARY_CORE");
        assertThat(history).extracting(LicencePlanPrice::getChangedBy).containsExactly("launch prices", "platform setup");
        assertThat(history).extracting(LicencePlanPrice::getAnnualPricePence).containsExactly(63_500, 90_000);
        assertThat(history.get(0).getEffectiveFrom()).isEqualTo(today());
    }

    @Test
    void aSecondStartChangesNothing() {
        long planRows = planPrices.count();
        long passRows = passPrices.count();
        assertThat(launchPrices.apply()).isZero();
        assertThat(planPrices.count()).isEqualTo(planRows);
        assertThat(passPrices.count()).isEqualTo(passRows);
    }

    @Test
    void aPriceTheTeamSetIsLeftAloneOnTheNextStart() {
        // The team's own price for a childcare centre, in force today.
        LicencePlanPrice inForce = planPrices.findByPlanCodeAndEffectiveFrom("EARLY_YEARS", today()).orElseThrow();
        inForce.revise(41_000, "admin@motormovers.test", clock.instant());
        planPrices.save(inForce);
        try {
            assertThat(launchPrices.apply()).isZero();
            assertThat(plans.priceOn("EARLY_YEARS", today())).isEqualTo(41_000);
        } finally {
            inForce.revise(39_500, LaunchPrices.CHANGED_BY, clock.instant());
            planPrices.save(inForce);
        }
        // A pass price exists (the team's or the launch one), so it is never set again.
        assertThat(passPrices.count()).isPositive();
    }

    @Test
    void aPlanStillAtItsSetupPriceGetsTheLaunchPriceAndLicencesKeepTheirAgreedPrice() {
        School school = schools.save(new School("Agreed Price Nursery", null, Region.UK));
        Licence licence = licences.save(new Licence(school, plans.find("TRUST"), 148_750, today().minusMonths(3),
                today().plusMonths(9), EnumSet.of(ColourStage.GREEN)));
        // As on a deployment made before task 85: only the setup price.
        planPrices.delete(planPrices.findByPlanCodeAndEffectiveFrom("TRUST", today()).orElseThrow());
        assertThat(plans.priceOn("TRUST", today())).isEqualTo(148_750);

        assertThat(launchPrices.apply()).isEqualTo(1);

        assertThat(plans.priceOn("TRUST", today())).isEqualTo(57_150);
        assertThat(licences.findById(licence.getId()).orElseThrow().getAgreedPricePence()).isEqualTo(148_750);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
