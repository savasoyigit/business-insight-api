package de.savas.businessinsightapi.application.auth;

import de.savas.businessinsightapi.common.error.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sliding one-minute window of login attempts per key (username and client address).
 * Keys without attempts in the last minute are swept out at most once a minute, so the map cannot keep growing.
 * State is per instance; with several instances each one enforces the limit on its own.
 */
@Component
public class LoginRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AtomicReference<Instant> lastSweep;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
        this.lastSweep = new AtomicReference<>(clock.instant());
    }

    public void assertAllowed(String key, int limitPerMinute) {
        if (limitPerMinute <= 0) {
            return;
        }
        Instant now = clock.instant();
        Instant threshold = now.minus(WINDOW);
        sweepIfDue(now, threshold);

        boolean[] blocked = {false};
        // compute() is atomic per key, so a concurrent sweep cannot drop a deque that is being updated.
        attemptsByKey.compute(key, (k, attempts) -> {
            Deque<Instant> deque = attempts != null ? attempts : new ArrayDeque<>();
            prune(deque, threshold);
            if (deque.size() >= limitPerMinute) {
                blocked[0] = true;
            } else {
                deque.addLast(now);
            }
            return deque;
        });
        if (blocked[0]) {
            throw new TooManyRequestsException("Too many login attempts. Please retry later.");
        }
    }

    public void clear(String key) {
        attemptsByKey.remove(key);
    }

    int trackedKeys() {
        return attemptsByKey.size();
    }

    private void sweepIfDue(Instant now, Instant threshold) {
        Instant last = lastSweep.get();
        if (now.isBefore(last.plus(WINDOW)) || !lastSweep.compareAndSet(last, now)) {
            return;
        }
        for (String key : attemptsByKey.keySet()) {
            attemptsByKey.computeIfPresent(key, (k, deque) -> {
                prune(deque, threshold);
                return deque.isEmpty() ? null : deque;
            });
        }
    }

    private static void prune(Deque<Instant> attempts, Instant threshold) {
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(threshold)) {
            attempts.pollFirst();
        }
    }
}
