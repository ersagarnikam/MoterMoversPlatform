package uk.co.motormovers.platform.user.service;

import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Every other account is created by someone already signed in, so a new deployment needs one way in.
 * When {@code PLATFORM_ADMIN_EMAIL} is set and there is no Motor Movers team account yet, this creates one
 * and emails it a link to choose a password. Once any admin exists it does nothing.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordService passwords;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;

    public AdminBootstrap(AppUserRepository users, PasswordService passwords, PasswordEncoder passwordEncoder,
            @Value("${platform.admin-email:}") String adminEmail) {
        this.users = users;
        this.passwords = passwords;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || users.existsByRole(Role.ADMIN)) {
            return;
        }
        String email = adminEmail.strip().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            log.warn("PLATFORM_ADMIN_EMAIL {} already has a non-admin account; no admin created", email);
            return;
        }
        // No usable password until they follow the invite link.
        AppUser admin = users.save(new AppUser(email, "Motor Movers team",
                passwordEncoder.encode(UUID.randomUUID().toString()), Role.ADMIN, null, null));
        passwords.sendInvite(admin, "Motor Movers");
        log.info("Created the first admin account for {} and emailed a sign-in link", email);
    }
}
