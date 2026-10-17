package uk.co.motormovers.platform.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.PasswordToken;
import uk.co.motormovers.platform.user.repository.AppUserRepository;
import uk.co.motormovers.platform.user.repository.PasswordTokenRepository;

/** One-time links to set a password: invites for new staff, and "forgot password" for everyone. */
@Service
public class PasswordService {

    public static final Duration RESET_LIFETIME = Duration.ofHours(1);
    public static final Duration INVITE_LIFETIME = Duration.ofDays(7);
    public static final int MIN_LENGTH = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final PasswordTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final Mailer mailer;
    private final Clock clock;

    public PasswordService(AppUserRepository users, PasswordTokenRepository tokens, PasswordEncoder passwordEncoder,
            Mailer mailer, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.clock = clock;
    }

    /** Emails a reset link if the account exists and is on. Says nothing either way, so emails cannot be probed. */
    @Transactional
    public void requestReset(String email) {
        users.findByEmailIgnoreCase(email.strip()).filter(AppUser::isEnabled).ifPresent(user -> {
            String token = issue(user, PasswordToken.Purpose.RESET, RESET_LIFETIME);
            mailer.send(user.getEmail(), "Reset your Motor Movers password",
                    "Hello " + user.getDisplayName() + ",\n\n"
                            + "Use this link to choose a new password. It works once, for one hour:\n\n"
                            + mailer.link("/reset-password?token=" + token) + "\n\n"
                            + "If you did not ask for this, you can ignore this email. Your password has not changed.\n\n"
                            + "Motor Movers");
        });
    }

    /** Emails a new member of staff a link to set their first password. */
    @Transactional
    public void sendInvite(AppUser user, String invitedBy) {
        String token = issue(user, PasswordToken.Purpose.INVITE, INVITE_LIFETIME);
        mailer.send(user.getEmail(), "Your Motor Movers account",
                "Hello " + user.getDisplayName() + ",\n\n"
                        + invitedBy + " has set up a Motor Movers account for you. Use this link to choose your "
                        + "password. It works once, for seven days:\n\n"
                        + mailer.link("/reset-password?token=" + token) + "\n\n"
                        + "Then sign in at " + mailer.link("/login") + " with this email address.\n\n"
                        + "Motor Movers");
    }

    /** Sets the password for the link's account. Returns the account's email so the person can sign in. */
    @Transactional
    public String setPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_LENGTH || newPassword.length() > 200) {
            throw new IllegalArgumentException("Choose a password of at least " + MIN_LENGTH + " characters");
        }
        Instant now = clock.instant();
        PasswordToken link = tokens.findByTokenHash(hash(token == null ? "" : token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new IllegalArgumentException(
                        "This link has expired or has already been used. Ask for a new one."));
        AppUser user = link.getUser();
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("This link has expired or has already been used. Ask for a new one.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        // Any other outstanding link for this account stops working too.
        tokens.findByUserIdAndUsedAtIsNull(user.getId()).forEach(t -> t.use(now));
        return user.getEmail();
    }

    private String issue(AppUser user, PasswordToken.Purpose purpose, Duration lifetime) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.save(new PasswordToken(hash(token), user, purpose, now, now.plus(lifetime)));
        return token;
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
