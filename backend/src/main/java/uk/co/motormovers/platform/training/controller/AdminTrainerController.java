package uk.co.motormovers.platform.training.controller;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;
import uk.co.motormovers.platform.user.service.PasswordService;

/**
 * The training team: who can run live sessions. Trainers see only the cohorts assigned to them; members of the
 * Motor Movers team can run any. Each can have their own Zoom login, so sessions can run at the same time.
 */
@RestController
@RequestMapping("/api/admin/trainers")
public class AdminTrainerController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordService passwords;
    private final CurrentUserResolver currentUser;

    public AdminTrainerController(AppUserRepository users, PasswordEncoder passwordEncoder, PasswordService passwords,
            CurrentUserResolver currentUser) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwords = passwords;
        this.currentUser = currentUser;
    }

    /** {@code team} is true for members of the Motor Movers team, who can also run sessions. */
    public record TrainerRow(Long id, String displayName, String email, String zoomEmail, boolean team,
            boolean enabled) {

        static TrainerRow of(AppUser u) {
            return new TrainerRow(u.getId(), u.getDisplayName(), u.getEmail(), u.getZoomEmail(),
                    u.getRole() == Role.ADMIN, u.isEnabled());
        }
    }

    public record NewTrainer(@NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(max = 100) String displayName, @Email @Size(max = 200) String zoomEmail) {
    }

    public record ZoomEmail(@Email @Size(max = 200) String zoomEmail) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TrainerRow> list() {
        return users.findByRoleInOrderByDisplayName(EnumSet.of(Role.TRAINER, Role.ADMIN)).stream()
                .map(TrainerRow::of).toList();
    }

    /** Creates a trainer account and emails them a link to choose their password. */
    @PostMapping
    @Transactional
    public TrainerRow add(@Valid @RequestBody NewTrainer request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser trainer = new AppUser(email, request.displayName().strip(),
                passwordEncoder.encode(UUID.randomUUID().toString()), Role.TRAINER, null, null);
        trainer.setZoomEmail(request.zoomEmail());
        trainer = users.save(trainer);
        passwords.sendInvite(trainer, currentUser.current().displayName());
        return TrainerRow.of(trainer);
    }

    public record Enabled(boolean enabled) {
    }

    /**
     * Switches a trainer's account off (or back on). Off, they can no longer sign in, within a minute; their cohorts
     * stay assigned to them until the team gives them to someone else. Team accounts are managed elsewhere.
     */
    @PutMapping("/{userId}/enabled")
    @Transactional
    public TrainerRow setEnabled(@PathVariable Long userId, @RequestBody Enabled request) {
        AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("Trainer", userId));
        if (user.getRole() != Role.TRAINER) {
            throw new IllegalArgumentException("Only trainers' accounts are switched off here");
        }
        user.setEnabled(request.enabled());
        return TrainerRow.of(user);
    }

    /** The Zoom login this trainer hosts with; blank goes back to the platform's default Zoom host. */
    @PutMapping("/{userId}/zoom-email")
    @Transactional
    public TrainerRow setZoomEmail(@PathVariable Long userId, @Valid @RequestBody ZoomEmail request) {
        AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("Trainer", userId));
        if (user.getRole() != Role.TRAINER && user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Only trainers and the Motor Movers team run live sessions");
        }
        user.setZoomEmail(request.zoomEmail());
        return TrainerRow.of(user);
    }
}
