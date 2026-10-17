package uk.co.motormovers.platform.security.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Sign-in tokens last a working day (30 days for parents). This filter stops a switched-off or deleted
 * account from using a token it already holds, re-checking each account at most once a minute.
 */
public class ActiveUserFilter extends OncePerRequestFilter {

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
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getClaim("uid") instanceof Number uid
                && !isActive(uid.longValue())) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"This account is switched off\"}");
            return;
        }
        chain.doFilter(request, response);
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
