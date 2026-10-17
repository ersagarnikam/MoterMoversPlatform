package uk.co.motormovers.platform.school.controller;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.school.dto.SchoolSetupViews;
import uk.co.motormovers.platform.school.model.Trust;
import uk.co.motormovers.platform.school.repository.TrustRepository;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;
import uk.co.motormovers.platform.user.service.PasswordService;

/** Academy trusts and their trust-lead accounts, managed by the platform team. ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/trusts")
public class AdminTrustController {

    private final TrustRepository trusts;
    private final AppUserRepository users;
    private final PasswordService passwords;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserResolver currentUser;

    public AdminTrustController(TrustRepository trusts, AppUserRepository users, PasswordService passwords,
            PasswordEncoder passwordEncoder, CurrentUserResolver currentUser) {
        this.trusts = trusts;
        this.users = users;
        this.passwords = passwords;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    public record TrustRow(Long id, String name, List<SchoolSetupViews.StaffMember> leads) {
    }

    public record NewTrust(@NotBlank @Size(max = 200) String name) {
    }

    public record NewLead(@NotBlank @Email @Size(max = 200) String email, @NotBlank @Size(max = 100) String displayName) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TrustRow> list() {
        return trusts.findAll().stream().map(this::row).toList();
    }

    @PostMapping
    @Transactional
    public TrustRow create(@Valid @RequestBody NewTrust request) {
        return row(trusts.save(new Trust(request.name().strip())));
    }

    /** Creates a trust lead and emails them a link to choose their password. */
    @PostMapping("/{trustId}/leads")
    @Transactional
    public SchoolSetupViews.StaffMember addLead(@PathVariable Long trustId, @Valid @RequestBody NewLead request) {
        Trust trust = trusts.findById(trustId).orElseThrow(() -> new NotFoundException("Trust", trustId));
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser lead = users.save(new AppUser(email, request.displayName().strip(),
                passwordEncoder.encode(UUID.randomUUID().toString()), Role.TRUST_LEAD, null, trust.getId()));
        passwords.sendInvite(lead, currentUser.current().displayName());
        return SchoolSetupViews.StaffMember.of(lead);
    }

    private TrustRow row(Trust t) {
        return new TrustRow(t.getId(), t.getName(), users.findByTrustIdAndRoleOrderByDisplayName(t.getId(),
                Role.TRUST_LEAD).stream().map(SchoolSetupViews.StaffMember::of).toList());
    }
}
