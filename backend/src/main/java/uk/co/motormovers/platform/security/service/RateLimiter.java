package uk.co.motormovers.platform.security.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.common.exception.TooManyRequestsException;

/**
 * Limits guessable actions (sign-in, registration, parent codes, password resets) to a few attempts per
 * window. Counts are held in memory, so each API instance counts separately: fine for one or two
 * instances, move to a shared store before scaling further.
 */
@Component
public class RateLimiter {

    private record Window(Instant start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts one attempt for this key and throws once more than {@code limit} fall in one window. */
    public void check(String action, String key, int limit, Duration window) {
        Instant now = clock.instant();
        String id = action + ":" + (key == null ? "" : key.toLowerCase(Locale.ROOT));
        Window w = windows.compute(id, (k, old) -> old == null || old.start().plus(window).isBefore(now)
                ? new Window(now, 1)
                : new Window(old.start(), old.count() + 1));
        if (w.count() > limit) {
            long retry = Math.max(1, Duration.between(now, w.start().plus(window)).toSeconds());
            throw new TooManyRequestsException(retry);
        }
    }

    /** Forgets a key, e.g. after a successful sign-in. */
    public void reset(String action, String key) {
        windows.remove(action + ":" + (key == null ? "" : key.toLowerCase(Locale.ROOT)));
    }

    @Scheduled(fixedDelay = 600_000)
    void evictOld() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(2));
        windows.values().removeIf(w -> w.start().isBefore(cutoff));
    }
}
