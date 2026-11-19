package de.savas.businessinsightapi.application.auth;

import de.savas.businessinsightapi.common.error.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-19T10:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    @Test
    void blocksWithinTheWindowAndRecoversAfterIt() {
        for (int i = 0; i < 3; i++) limiter.assertAllowed("alice|1.2.3.4", 3);
        assertThatThrownBy(() -> limiter.assertAllowed("alice|1.2.3.4", 3)).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.assertAllowed("alice|5.6.7.8", 3)).doesNotThrowAnyException();

        clock.advance(Duration.ofSeconds(61));
        assertThatCode(() -> limiter.assertAllowed("alice|1.2.3.4", 3)).doesNotThrowAnyException();
    }

    @Test
    void staleKeysAreSweptSoTheMapDoesNotGrow() {
        for (int i = 0; i < 1000; i++) limiter.assertAllowed("user" + i + "|10.0.0." + (i % 250), 5);
        assertThat(limiter.trackedKeys()).isEqualTo(1000);

        clock.advance(Duration.ofSeconds(90));
        limiter.assertAllowed("fresh|1.1.1.1", 5);
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }
}
