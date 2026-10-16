package uk.co.motormovers.platform.school.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

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

/** Schools and licences, managed by the platform team. ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/schools")
public class AdminSchoolController {

    private final SchoolRepository schools;
    private final TrustRepository trusts;
    private final LicenceRepository licences;
    private final PlatformProperties properties;
    private final Clock clock;

    public AdminSchoolController(SchoolRepository schools, TrustRepository trusts, LicenceRepository licences,
            PlatformProperties properties, Clock clock) {
        this.schools = schools;
        this.trusts = trusts;
        this.licences = licences;
        this.properties = properties;
        this.clock = clock;
    }

    public record SchoolRow(Long id, String name, String trustName, String region, String plan,
            Licence.Status licenceStatus, LocalDate endsOn, Set<ColourStage> stages) {
    }

    public record NewSchool(@NotBlank String name, Long trustId, @NotNull LicencePlan plan,
            @NotEmpty Set<ColourStage> stages, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SchoolRow> list() {
        LocalDate today = LocalDate.now(clock);
        return schools.findAll().stream().map(s -> {
            Licence l = licences.findFirstBySchoolIdOrderByEndsOnDesc(s.getId()).orElse(null);
            return new SchoolRow(s.getId(), s.getName(), s.getTrust() == null ? null : s.getTrust().getName(),
                    s.getRegion().name(), l == null ? null : l.getPlan().label(),
                    l == null ? Licence.Status.EXPIRED : l.statusOn(today), l == null ? null : l.getEndsOn(),
                    l == null ? Set.of() : Set.copyOf(l.getStages()));
        }).toList();
    }

    /** Signs a school up: the school is created in this deployment's data region. */
    @PostMapping
    @Transactional
    public SchoolRow create(@Valid @RequestBody NewSchool request) {
        if (!request.endsOn().isAfter(request.startsOn())) {
            throw new IllegalArgumentException("Licence must end after it starts");
        }
        Trust trust = request.trustId() == null ? null
                : trusts.findById(request.trustId()).orElseThrow(() -> new NotFoundException("Trust", request.trustId()));
        School school = schools.save(new School(request.name(), trust, properties.region()));
        Licence licence = licences.save(new Licence(school, request.plan(), request.startsOn(), request.endsOn(),
                request.stages()));
        return new SchoolRow(school.getId(), school.getName(), trust == null ? null : trust.getName(),
                school.getRegion().name(), licence.getPlan().label(), licence.statusOn(LocalDate.now(clock)),
                licence.getEndsOn(), Set.copyOf(licence.getStages()));
    }
}
