package uk.co.motormovers.platform.security.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import uk.co.motormovers.platform.common.exception.SafeErrors;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Sign-in tokens last a working day (30 days for parents). This filter stops a switched-off or deleted
 * account from using a token it already holds, re-checking each account at most once a minute.
 */
public class ActiveUserFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ActiveUserFilter.class);
    private static final Duration RECHECK = Duration.ofMinutes(1);

    private record Status(boolean active, Instant checkedAt) {
    }

    private final AppUserRepository users;
    private final Clock clock;
    private final Map<Long, Status> cache = new ConcurrentHashMap<>();

    public ActiveUserFilter(AppUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getClaim("uid") instanceof Number uid) {
            boolean active;
            try {
                active = isActive(uid.longValue());
            } catch (RuntimeException e) {
                // This runs before the API's error handling, so it answers in the same shape itself.
                log.error("Could not check an account: {}", SafeErrors.describe(e));
                problem(response, HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
                return;
            }
            if (!active) {
                SecurityContextHolder.clearContext();
                problem(response, HttpStatus.UNAUTHORIZED, "This account is switched off");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static void problem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status.value() + ",\"title\":\"" + status.getReasonPhrase()
                + "\",\"detail\":\"" + detail + "\"}");
    }

    private boolean isActive(long userId) {
        Instant now = clock.instant();
        Status status = cache.get(userId);
        if (status == null || status.checkedAt().plus(RECHECK).isBefore(now)) {
            status = new Status(users.findById(userId).map(AppUser::isEnabled).orElse(false), now);
            cache.put(userId, status);
        }
        return status.active();
    }

    /** Forgets an account's cached status, so a change takes effect on its next request. */
    public void evict(long userId) {
        cache.remove(userId);
    }
}
