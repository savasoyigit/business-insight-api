package de.savas.businessinsightapi.infrastructure.security;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CorrelationIdFilter correlationIdFilter;
    private final JwtAuthenticationFilter jwtFilter;
    private final SecurityErrorHandlers securityErrorHandlers;
    private final MetricsAccessManager metricsAccess;

    public SecurityConfig(CorrelationIdFilter correlationIdFilter, JwtAuthenticationFilter jwtFilter,
                          SecurityErrorHandlers securityErrorHandlers, MetricsAccessManager metricsAccess) {
        this.correlationIdFilter = correlationIdFilter;
        this.jwtFilter = jwtFilter;
        this.securityErrorHandlers = securityErrorHandlers;
        this.metricsAccess = metricsAccess;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/actuator/health", "/api/auth/**").permitAll()
                        // Registration is open, so "authenticated" alone would let anyone read operational metrics.
                        .requestMatchers("/actuator/prometheus").access(metricsAccess.scrapeOrPermission())
                        .requestMatchers("/actuator/**").hasAuthority(MetricsAccessManager.PERMISSION)
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorHandlers)
                        .accessDeniedHandler(securityErrorHandlers)
                )
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
