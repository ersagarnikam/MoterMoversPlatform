package uk.co.motormovers.platform.school.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.school.dto.PlanViews;
import uk.co.motormovers.platform.school.model.LicencePlan;
import uk.co.motormovers.platform.school.model.LicencePlanPrice;
import uk.co.motormovers.platform.school.repository.LicencePlanPriceRepository;
import uk.co.motormovers.platform.school.repository.LicencePlanRepository;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;

/**
 * Licence plans and their prices, set by the Motor Movers team (under /api/admin, ADMIN only). A price change
 * applies from a date that has not passed yet, so licences already sold and invoices already sent keep their
 * price; every change records who made it and when.
 */
@Service
public class PlanService {

    private final LicencePlanRepository plans;
    private final LicencePlanPriceRepository prices;
    private final LicenceRepository licences;
    private final CurrentUserResolver currentUser;
    private final Clock clock;

    public PlanService(LicencePlanRepository plans, LicencePlanPriceRepository prices, LicenceRepository licences,
            CurrentUserResolver currentUser, Clock clock) {
        this.plans = plans;
        this.prices = prices;
        this.licences = licences;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlanViews.PlanView> all() {
        return plans.findAllByOrderBySortOrderAscLabelAsc().stream().map(this::view).toList();
    }

    /** A new plan, priced from today. Its code is made from the name and never changes. */
    @Transactional
    public PlanViews.PlanView create(PlanViews.NewPlan r) {
        if (r.freeTrial() && r.pricePence() != 0) {
            throw new IllegalArgumentException("A free trial plan costs nothing");
        }
        String base = r.label().strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        base = base.isEmpty() ? "PLAN" : base.substring(0, Math.min(base.length(), 24));
        String code = base;
        for (int n = 2; plans.existsById(code); n++) {
            code = base + "_" + n;
        }
        int order = plans.findAll().stream().mapToInt(LicencePlan::getSortOrder).max().orElse(0) + 1;
        LicencePlan plan = plans.save(new LicencePlan(code, r.label().strip(), r.freeTrial(), order));
        prices.save(new LicencePlanPrice(plan, r.pricePence(), LocalDate.now(clock), currentUser.current().email(),
                clock.instant()));
        return view(plan);
    }

    /** Renames a plan, or retires it (existing licences keep it; new ones cannot choose it). */
    @Transactional
    public PlanViews.PlanView update(String code, PlanViews.PlanUpdate r) {
        LicencePlan plan = find(code);
        plan.rename(r.label().strip());
        plan.setActive(r.active());
        return view(plan);
    }

    /** Sets the price from today or a later date. A change scheduled for a later date can be replaced. */
    @Transactional
    public PlanViews.PlanView changePrice(String code, PlanViews.PriceChange r) {
        LicencePlan plan = find(code);
        if (r.effectiveFrom().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("A price change cannot start in the past");
        }
        if (plan.isFreeTrial() && r.pricePence() != 0) {
            throw new IllegalArgumentException("A free trial plan costs nothing");
        }
        String me = currentUser.current().email();
        var existing = prices.findByPlanCodeAndEffectiveFrom(code, r.effectiveFrom());
        if (existing.isPresent()) {
            // Only a change that has not started yet may be replaced; one in force today stays as it was.
            if (!r.effectiveFrom().isAfter(LocalDate.now(clock))) {
                throw new IllegalArgumentException("A price from that date is already in force. "
                        + "Set the change from tomorrow or later.");
            }
            existing.get().revise(r.pricePence(), me, clock.instant());
        } else {
            prices.save(new LicencePlanPrice(plan, r.pricePence(), r.effectiveFrom(), me, clock.instant()));
        }
        return view(plan);
    }

    /** The plan's yearly price in force on a date. */
    @Transactional(readOnly = true)
    public int priceOn(String code, LocalDate on) {
        return prices.findFirstByPlanCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(code, on)
                .map(LicencePlanPrice::getAnnualPricePence)
                .orElseThrow(() -> new IllegalStateException("Plan " + code + " has no price on " + on));
    }

    public LicencePlan find(String code) {
        return plans.findById(code).orElseThrow(() -> new NotFoundException("Plan", code));
    }

    private PlanViews.PlanView view(LicencePlan plan) {
        LocalDate today = LocalDate.now(clock);
        List<PlanViews.PriceRow> history = prices.findByPlanCodeOrderByEffectiveFromDesc(plan.getCode()).stream()
                .map(p -> new PlanViews.PriceRow(p.getEffectiveFrom(), p.getAnnualPricePence(), p.getChangedBy(),
                        p.getChangedAt()))
                .toList();
        PlanViews.PriceRow next = history.stream().filter(p -> p.effectiveFrom().isAfter(today))
                .reduce((later, earlier) -> earlier).orElse(null);
        return new PlanViews.PlanView(plan.getCode(), plan.getLabel(), plan.isFreeTrial(), plan.isActive(),
                priceOn(plan.getCode(), today), next, licences.countByPlanCode(plan.getCode()), history);
    }
}
