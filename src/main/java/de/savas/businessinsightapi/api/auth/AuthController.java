package de.savas.businessinsightapi.api.auth;

import de.savas.businessinsightapi.application.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.email(), request.username(),request.password());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String token = authService.login(request.username(), request.password(), clientIp(servletRequest));
        return new LoginResponse(token);
    }

    /**
     * The connection's address. X-Forwarded-For is not read here: any client can set it, and a new value per
     * request would get around the login rate limit. Behind a reverse proxy, server.forward-headers-strategy=native
     * makes the container fill in the client address, accepting the header only from trusted proxies.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
