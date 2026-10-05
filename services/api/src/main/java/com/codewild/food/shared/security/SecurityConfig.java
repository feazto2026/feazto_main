package com.codewild.food.shared.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Canonical single security chain for the modular monolith (Supabase JWKS).
 *
 * <p>There is exactly ONE {@code SecurityFilterChain} in production. The legacy
 * HS256 shared-secret chain ({@code config.SecurityConfig} +
 * {@code JwtAuthFilter} trusting the JWT {@code roles} claim) has been deleted:
 * Supabase mints access tokens (not our HS256 secret), and roles MUST be loaded
 * server-side from {@code user_roles} — never from a token claim.
 *
 * <p>Public surface (no JWT) — everything else requires authentication:
 * <ul>
 *   <li>{@code POST /api/v1/auth/otp/request}, {@code /otp/send} (legacy alias),
 *       {@code /otp/verify}, {@code /otp/resend} — rate-limited OTP entry points.</li>
 *   <li>{@code POST /api/v1/payments/webhook} (+ {@code /payments/webhooks/**} for
 *       provider-routed webhooks) — verified by provider HMAC signature, NOT by JWT.</li>
 *   <li>{@code GET /actuator/health}, {@code /actuator/info} — load-balancer probes.</li>
 *   <li>{@code GET /v3/api-docs/**}, {@code /swagger-ui/**} — only exposed in non-prod profiles.</li>
 * </ul>
 * Fine-grained role/permission/ownership checks use {@code @PreAuthorize} +
 * {@link PermissionEvaluator} + {@link VendorRiderApprovalGate} inside services.
 *
 * <p>Required dependencies:
 * {@code spring-boot-starter-security}, {@code spring-boot-starter-web},
 * {@code com.nimbusds:nimbus-jose-jwt}, {@code com.fasterxml.jackson.core:jackson-databind},
 * {@code com.github.ben-manes.caffeine:caffeine}.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /** Prefix-exact public paths (no wildcards beyond what is listed). */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/v1/auth/otp/request",
            "/api/v1/auth/otp/send",
            "/api/v1/auth/otp/verify",
            "/api/v1/auth/otp/resend",
            "/api/v1/auth/refresh",
            "/api/v1/payments/webhook",
            "/api/v1/payments/webhooks",
            "/actuator/health",
            "/actuator/info");

    /** Returns true when the URI is public. Keep in sync with {@link #filterChain}. */
    public static boolean isPublicPath(String uri) {
        if (uri == null) {
            return false;
        }
        // Strip query string for prefix matching.
        String path = uri.split("\\?")[0];
        for (String p : PUBLIC_PREFIXES) {
            if (path.equals(p) || path.startsWith(p + "/")) {
                return true;
            }
        }
        return path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html");
    }

    @Bean
    public SupabaseJwtValidator supabaseJwtValidator(
            @Value("${security.jwt.issuer:}") String issuer,
            @Value("${security.jwt.audience:}") String audience,
            SupabaseJwksSignatureVerifier verifier) {
        String iss = issuer == null || issuer.isBlank() ? null : issuer.trim();
        String aud = audience == null || audience.isBlank() ? null : audience.trim();
        return new SupabaseJwtValidator(iss, aud, verifier);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(SupabaseJwtValidator validator,
            PlatformUserService userService) {
        return new JwtAuthenticationFilter(validator, userService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            @Value("${security.cors.allowed-origins:}") String allowedOrigins) throws Exception {
        http
                // Single chain owns API + actuator paths; docs are permitted below
                // but blocked at infra in prod.
                .securityMatcher("/api/**", "/actuator/**", "/v3/api-docs/**",
                        "/swagger-ui/**", "/swagger-ui.html")
                .csrf(AbstractHttpConfigurer::disable) // stateless JWT API; no cookies
                .cors(cors -> cors.configurationSource(buildCors(allowedOrigins)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/otp/request",
                                "/api/v1/auth/otp/send",
                                "/api/v1/auth/otp/verify",
                                "/api/v1/auth/otp/resend",
                                "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/payments/webhook",
                                "/api/v1/payments/webhook/**",
                                "/api/v1/payments/webhooks/**")
                        .permitAll() // HMAC-verified in the controller; never JWT
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll() // blocked at infra in prod
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Standard envelope for authN/authZ failures (never stack traces).
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            String rid = String.valueOf(
                                    req.getAttribute("requestId") != null
                                            ? req.getAttribute("requestId")
                                            : java.util.UUID.randomUUID());
                            res.getWriter().write("{\"success\":false,\"error\":{\"code\":\"UNAUTHENTICATED\","
                                    + "\"message\":\"Authentication required\",\"details\":{}},"
                                    + "\"requestId\":\"" + rid + "\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            String rid = String.valueOf(
                                    req.getAttribute("requestId") != null
                                            ? req.getAttribute("requestId")
                                            : java.util.UUID.randomUUID());
                            res.getWriter().write("{\"success\":false,\"error\":{\"code\":\"FORBIDDEN\","
                                    + "\"message\":\"You do not have access to this resource\",\"details\":{}},"
                                    + "\"requestId\":\"" + rid + "\"}");
                        }));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Tight CORS: mobile apps use non-browser networking (no Origin enforcement
     * benefit), this list only governs Admin Web + docs. Configure via
     * {@code security.cors.allowed-origins} in production.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${security.cors.allowed-origins:}") String allowedOrigins) {
        return buildCors(allowedOrigins);
    }

    private static CorsConfigurationSource buildCors(String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            for (String o : allowedOrigins.split(",")) {
                String origin = o.trim();
                if (!origin.isBlank()) {
                    config.addAllowedOrigin(origin);
                }
            }
        }
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(false); // JWTs ride in headers, never cookies
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
