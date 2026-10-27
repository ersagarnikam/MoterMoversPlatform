package uk.co.motormovers.platform.security.controller;

import java.time.Duration;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.security.service.RateLimiter;
import uk.co.motormovers.platform.security.service.TokenService;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;
import uk.co.motormovers.platform.user.service.PasswordService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final CurrentUserResolver currentUser;
    private final RateLimiter rateLimiter;
    private final PasswordService passwords;
    /** Sign-ins per network address per window: 50. Raised only for load and browser tests (LOGIN_LIMIT_PER_IP). */
    private final int loginsPerAddress;
    /** Checked when the email is unknown, so a wrong email takes as long as a wrong password. */
    private final String dummyHash;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
            CurrentUserResolver currentUser, RateLimiter rateLimiter, PasswordService passwords,
            @Value("${platform.login-limit-per-ip:50}") int loginsPerAddress) {
        this.loginsPerAddress = loginsPerAddress;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
        this.passwords = passwords;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public record LoginRequest(@NotBlank @Size(max = 200) String email, @NotBlank @Size(max = 200) String password) {
    }

    public record LoginResponse(String token, CurrentUser user) {
    }

    /** Ten tries per email and fifty per network address every 15 minutes. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String email = request.email().strip();
        rateLimiter.check("login-ip", http.getRemoteAddr(), loginsPerAddress, WINDOW);
        rateLimiter.check("login", email, 10, WINDOW);
        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);
        boolean matches = passwordEncoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches || !user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password not recognised");
        }
        rateLimiter.reset("login", email);
        return new LoginResponse(tokens.issue(user), toCurrentUser(user));
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = PasswordService.MIN_LENGTH, max = 200) String password,
            @NotBlank @Size(max = 100) String displayName) {
    }

    /**
     * Parents and carers create their own account from the parent app. Every other role is set up by
     * the platform team or a school. A new account sees no child until the school issues an invite code.
     */
    @PostMapping("/register")
    @Transactional
    public LoginResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimiter.check("register-ip", http.getRemoteAddr(), 10, Duration.ofHours(1));
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser user = users.save(new AppUser(email, request.displayName().strip(),
                passwordEncoder.encode(request.password()), Role.PARENT, null, null));
        return new LoginResponse(tokens.issue(user), toCurrentUser(user));
    }

    public record ResetRequest(@NotBlank @Size(max = 200) String email) {
    }

    /** Always 202, whether or not the email has an account. */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody ResetRequest request, HttpServletRequest http) {
        rateLimiter.check("reset-ip", http.getRemoteAddr(), 20, Duration.ofHours(1));
        rateLimiter.check("reset", request.email().strip(), 3, Duration.ofHours(1));
        passwords.requestReset(request.email());
    }

    public record NewPassword(@NotBlank @Size(max = 100) String token, @NotBlank String password) {
    }

    public record PasswordSet(String email) {
    }

    /** Sets a password from an invite or reset link. */
    @PostMapping("/password")
    public PasswordSet setPassword(@Valid @RequestBody NewPassword request, HttpServletRequest http) {
        rateLimiter.check("set-password-ip", http.getRemoteAddr(), 20, WINDOW);
        return new PasswordSet(passwords.setPassword(request.token(), request.password()));
    }

    @GetMapping("/me")
    public CurrentUser me() {
        return currentUser.current();
    }

    private static CurrentUser toCurrentUser(AppUser user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getSchoolId(), user.getTrustId());
    }
}
