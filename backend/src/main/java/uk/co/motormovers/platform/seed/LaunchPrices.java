package uk.co.motormovers.platform.seed;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.config.CountrySettings;
import uk.co.motormovers.platform.home.model.HomeMonthlyPrice;
import uk.co.motormovers.platform.home.model.HomePassPrice;
import uk.co.motormovers.platform.home.repository.HomeMonthlyPriceRepository;
import uk.co.motormovers.platform.home.repository.HomePassPriceRepository;
import uk.co.motormovers.platform.school.model.LicencePlan;
import uk.co.motormovers.platform.school.model.LicencePlanPrice;
import uk.co.motormovers.platform.school.repository.LicencePlanPriceRepository;
import uk.co.motormovers.platform.school.repository.LicencePlanRepository;

/**
 * Gives a deployment its country's launch prices (go-live task 85), from {@code launch-prices} in its country
 * settings. On every start, and only where the team has not set a price itself:
 * <ul>
 * <li>a licence plan whose price in force today is still the "platform setup" one (the proposal's, in pounds, from
 * migration V6) gets the launch price from today;</li>
 * <li>with no home pass price at all, the launch price is set from today;</li>
 * <li>with no monthly price for the home programme at all, and one in the settings ({@code home-monthly}, blank
 * until it is decided), that price is set from today (task 68).</li>
 * </ul>
 * A price the team set on Plans and prices is never touched, and once applied a second start changes nothing: the
 * price in force is then this class's or the team's. Later changes are made on Plans and prices, not here. Courses
 * take their launch prices when the starter content creates them ({@link StarterContent}).
 *
 * <p>Runs before the demo and the starter content load, so both see the launch prices.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class LaunchPrices implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LaunchPrices.class);

    /** Who the price history names for the prices migration V6 put in. */
    static final String SETUP = "platform setup";
    /** Who the price history names for a price set here. */
    public static final String CHANGED_BY = "launch prices";

    private final CountrySettings country;
    private final LicencePlanRepository plans;
    private final LicencePlanPriceRepository planPrices;
    private final HomePassPriceRepository passPrices;
    private final HomeMonthlyPriceRepository monthlyPrices;
    private final Clock clock;

    public LaunchPrices(CountrySettings country, LicencePlanRepository plans, LicencePlanPriceRepository planPrices,
            HomePassPriceRepository passPrices, HomeMonthlyPriceRepository monthlyPrices, Clock clock) {
        this.country = country;
        this.plans = plans;
        this.planPrices = planPrices;
        this.passPrices = passPrices;
        this.monthlyPrices = monthlyPrices;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        apply();
    }

    /** Applies what is still missing and returns how many prices it set. */
    @Transactional
    public int apply() {
        CountrySettings.LaunchPrices launch = country.launchPrices();
        LocalDate today = LocalDate.now(clock);
        Map<String, Integer> byPlan = new LinkedHashMap<>();
        byPlan.put("EARLY_YEARS", launch.childcarePence());
        byPlan.put("PRIMARY_CORE", launch.schoolPence());
        byPlan.put("TRUST", launch.groupPence());
        int set = 0;
        for (Map.Entry<String, Integer> entry : byPlan.entrySet()) {
            LicencePlan plan = plans.findById(entry.getKey()).orElse(null);
            LicencePlanPrice inForce = plan == null ? null : planPrices
                    .findFirstByPlanCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(plan.getCode(), today)
                    .orElse(null);
            if (inForce != null && SETUP.equals(inForce.getChangedBy())
                    && inForce.getAnnualPricePence() != entry.getValue()) {
                planPrices.save(new LicencePlanPrice(plan, entry.getValue(), today, CHANGED_BY, clock.instant()));
                set++;
            }
        }
        if (passPrices.count() == 0) {
            passPrices.save(new HomePassPrice(launch.homePassPence(), today, CHANGED_BY, clock.instant()));
            set++;
        }
        if (launch.homeMonthlyPence() != null && monthlyPrices.count() == 0) {
            monthlyPrices.save(new HomeMonthlyPrice(launch.homeMonthlyPence(), today, CHANGED_BY, clock.instant()));
            set++;
        }
        if (set > 0) {
            log.info("Launch prices applied: {} set from today", set);
        }
        return set;
    }
}
