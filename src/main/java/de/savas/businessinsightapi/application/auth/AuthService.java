package de.savas.businessinsightapi.application.auth;

import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.common.error.UnauthorizedException;
import de.savas.businessinsightapi.domain.user.User;
import de.savas.businessinsightapi.domain.user.UserRepository;
import de.savas.businessinsightapi.infrastructure.security.JwtService;
import de.savas.businessinsightapi.infrastructure.security.SecurityHardeningProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityHardeningProperties hardeningProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final MeterRegistry meterRegistry;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       SecurityHardeningProperties hardeningProperties,
                       LoginRateLimiter loginRateLimiter,
                       MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.hardeningProperties = hardeningProperties;
        this.loginRateLimiter = loginRateLimiter;
        this.meterRegistry = meterRegistry;
    }

    public void register(String email, String username, String password) {

        if (userRepository.findByEmail(email).isPresent()) {
            throw new BadRequestException("Email already registered");
        }

        validatePasswordPolicy(password);

        String hashed = passwordEncoder.encode(password);
        User user = new User(email, username, hashed);
        userRepository.save(user);
        meterRegistry.counter("auth.register.success").increment();
    }

    public String login(String username, String password, String clientIp) {
        String principal = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String source = clientIp == null ? "unknown" : clientIp.trim();
        String limiterKey = principal + "|" + source;

        Timer.Sample loginTimer = Timer.start(meterRegistry);
        try {
            loginRateLimiter.assertAllowed(limiterKey, hardeningProperties.loginRateLimitPerMinute());

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

            if (user.isLocked()) {
                meterRegistry.counter("auth.login.locked").increment();
                throw new UnauthorizedException("Account locked. Please retry later.");
            }

            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                user.registerFailedLogin(
                        hardeningProperties.maxFailedLoginAttempts(),
                        Duration.ofMinutes(hardeningProperties.lockoutMinutes())
                );
                userRepository.save(user);
                meterRegistry.counter("auth.login.failed").increment();
                throw new UnauthorizedException("Invalid credentials");
            }

            user.clearFailedLoginState();
            userRepository.save(user);
            loginRateLimiter.clear(limiterKey);
            meterRegistry.counter("auth.login.success").increment();

            return jwtService.generateAccessToken(user);
        } finally {
            loginTimer.stop(Timer.builder("auth.login.duration")
                    .description("Duration of login attempts")
                    .register(meterRegistry));
        }
    }

    private void validatePasswordPolicy(String password) {
        if (password == null || password.length() < hardeningProperties.minPasswordLength()) {
            throw new BadRequestException("Password must be at least " + hardeningProperties.minPasswordLength() + " characters");
        }
        if (!password.chars().anyMatch(Character::isUpperCase)) {
            throw new BadRequestException("Password must include at least one uppercase letter");
        }
        if (!password.chars().anyMatch(Character::isLowerCase)) {
            throw new BadRequestException("Password must include at least one lowercase letter");
        }
        if (!password.chars().anyMatch(Character::isDigit)) {
            throw new BadRequestException("Password must include at least one digit");
        }
        if (password.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch))) {
            throw new BadRequestException("Password must include at least one special character");
        }
        if (password.toLowerCase(Locale.ROOT).contains("password")) {
            throw new BadRequestException("Password must not contain weak dictionary fragments");
        }
        log.debug("Password policy validated for new registration");
    }
}