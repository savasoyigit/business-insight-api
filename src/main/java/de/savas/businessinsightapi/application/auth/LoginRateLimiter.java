package de.savas.businessinsightapi.application.auth;

import de.savas.businessinsightapi.common.error.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private final Map<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public void assertAllowed(String key, int limitPerMinute) {
        if (limitPerMinute <= 0) {
            return;
        }
        Instant now = Instant.now();
        Instant threshold = now.minus(Duration.ofMinutes(1));

        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, unused -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(threshold)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= limitPerMinute) {
                throw new TooManyRequestsException("Too many login attempts. Please retry later.");
            }
            attempts.addLast(now);
        }
    }

    public void clear(String key) {
        attemptsByKey.remove(key);
    }
}
