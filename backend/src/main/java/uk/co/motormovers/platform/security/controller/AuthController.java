package uk.co.motormovers.platform.security.controller;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.security.service.TokenService;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final CurrentUserResolver currentUser;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
            CurrentUserResolver currentUser) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.currentUser = currentUser;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record LoginResponse(String token, CurrentUser user) {
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password not recognised"));
        CurrentUser me = new CurrentUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getSchoolId(), user.getTrustId());
        return new LoginResponse(tokens.issue(user), me);
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 200) String password, @NotBlank @Size(max = 100) String displayName) {
    }

    /**
     * Parents and carers create their own account from the parent app. Every other role is set up by
     * the platform team or a school. A new account sees no child until the school issues an invite code.
     */
    @PostMapping("/register")
    @Transactional
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser user = users.save(new AppUser(email, request.displayName().strip(),
                passwordEncoder.encode(request.password()), Role.PARENT, null, null));
        CurrentUser me = new CurrentUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                null, null);
        return new LoginResponse(tokens.issue(user), me);
    }

    @GetMapping("/me")
    public CurrentUser me() {
        return currentUser.current();
    }
}
