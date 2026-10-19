package uk.co.motormovers.platform.school.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.common.config.PlatformProperties;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.model.LicencePlan;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.Trust;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.school.repository.TrustRepository;
import uk.co.motormovers.platform.school.service.PlanService;

/** Schools and licences, managed by the platform team. ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/schools")
public class AdminSchoolController {

    private final SchoolRepository schools;
    private final TrustRepository trusts;
    private final LicenceRepository licences;
    private final PlanService plans;
    private final PlatformProperties properties;
    private final Clock clock;

    public AdminSchoolController(SchoolRepository schools, TrustRepository trusts, LicenceRepository licences,
            PlanService plans, PlatformProperties properties, Clock clock) {
        this.schools = schools;
        this.trusts = trusts;
        this.licences = licences;
        this.plans = plans;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * agreedPricePence is what the school pays a year on its current licence; listPricePence is that plan's
     * price today, so a discount shows as the difference.
     */
    public record SchoolRow(Long id, String name, String trustName, String region, String plan, String planCode,
            Integer agreedPricePence, Integer listPricePence, Licence.Status licenceStatus, LocalDate endsOn,
            Set<ColourStage> stages, boolean parentLinksEnabled) {
    }

    public record ParentLinksRequest(boolean enabled) {
    }

    /** agreedPricePence defaults to the plan's price on the start date. */
    public record NewSchool(@NotBlank @Size(max = 200) String name, Long trustId, @NotBlank String plan,
            @Min(0) @Max(10_000_000) Integer agreedPricePence, @NotEmpty Set<ColourStage> stages,
            @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {
    }

    public record AgreedPrice(@Min(0) @Max(10_000_000) int pricePence) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SchoolRow> list() {
        return schools.findAll().stream()
                .map(s -> row(s, licences.findFirstBySchoolIdOrderByEndsOnDesc(s.getId()).orElse(null)))
                .toList();
    }

    /** Signs a school up: the school is created in this deployment's data region. */
    @PostMapping
    @Transactional
    public SchoolRow create(@Valid @RequestBody NewSchool request) {
        if (!request.endsOn().isAfter(request.startsOn())) {
            throw new IllegalArgumentException("Licence must end after it starts");
        }
        LicencePlan plan = plans.find(request.plan());
        if (!plan.isActive()) {
            throw new IllegalArgumentException("The " + plan.getLabel() + " plan is no longer on sale");
        }
        int price = request.agreedPricePence() != null ? request.agreedPricePence()
                : plans.priceOn(plan.getCode(), request.startsOn());
        checkTrialPrice(plan, price);
        Trust trust = request.trustId() == null ? null
                : trusts.findById(request.trustId()).orElseThrow(() -> new NotFoundException("Trust", request.trustId()));
        School school = schools.save(new School(request.name(), trust, properties.region()));
        Licence licence = licences.save(new Licence(school, plan, price, request.startsOn(), request.endsOn(),
                request.stages()));
        return row(school, licence);
    }

    /** Changes what the school pays a year on its current licence, for example an agreed discount. */
    @PostMapping("/{id}/agreed-price")
    @Transactional
    public SchoolRow agreePrice(@PathVariable Long id, @Valid @RequestBody AgreedPrice request) {
        School school = schools.findById(id).orElseThrow(() -> new NotFoundException("School", id));
        Licence licence = licences.findFirstBySchoolIdOrderByEndsOnDesc(id)
                .orElseThrow(() -> new IllegalArgumentException("This school has no licence"));
        checkTrialPrice(licence.getPlan(), request.pricePence());
        licence.agreePrice(request.pricePence());
        return row(school, licence);
    }

    private static void checkTrialPrice(LicencePlan plan, int price) {
        if (plan.isFreeTrial() && price != 0) {
            throw new IllegalArgumentException("A free trial costs nothing");
        }
    }

    private SchoolRow row(School s, Licence l) {
        LocalDate today = LocalDate.now(clock);
        return new SchoolRow(s.getId(), s.getName(), s.getTrust() == null ? null : s.getTrust().getName(),
                s.getRegion().name(), l == null ? null : l.getPlan().getLabel(),
                l == null ? null : l.getPlan().getCode(), l == null ? null : l.getAgreedPricePence(),
                l == null ? null : plans.priceOn(l.getPlan().getCode(), today),
                l == null ? Licence.Status.EXPIRED : l.statusOn(today), l == null ? null : l.getEndsOn(),
                l == null ? Set.of() : Set.copyOf(l.getStages()), s.isParentLinksEnabled());
    }

    /** Lets the school's staff invite parents into the parent app. Switching it off hides every link at once. */
    @PostMapping("/{id}/parent-links")
    @Transactional
    public void setParentLinks(@PathVariable Long id, @RequestBody ParentLinksRequest request) {
        schools.findById(id).orElseThrow(() -> new NotFoundException("School", id))
                .setParentLinksEnabled(request.enabled());
    }
}
