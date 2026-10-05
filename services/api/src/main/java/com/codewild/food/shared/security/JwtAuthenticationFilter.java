package com.codewild.food.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless JWT filter: validates the Supabase bearer token, loads the
 * server-side {@link PlatformUser}, and publishes it into the SecurityContext.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>No {@code Authorization} header on a protected route =&gt; 401 {@code UNAUTHENTICATED}.</li>
 *   <li>Invalid/expired token =&gt; 401 with the validator's machine-readable code.</li>
 *   <li>Valid token but unknown/disabled user =&gt; 401 {@code UNKNOWN_USER} (fail closed).</li>
 *   <li>Public paths (see {@link SecurityConfig}) bypass this filter.</li>
 *   <li>Sets {@code request.setAttribute("requestId", ...)} when absent so every
 *       response — including auth failures — carries a request id.</li>
 * </ul>
 *
 * <p>Never logs tokens, phone numbers, or OTP values. Logs masked phone + request id only.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SupabaseJwtValidator jwtValidator;
    private final PlatformUserService userService;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtAuthenticationFilter(SupabaseJwtValidator jwtValidator,
            PlatformUserService userService) {
        this.jwtValidator = jwtValidator;
        this.userService = userService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityConfig.isPublicPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        ensureRequestId(request, response);

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "Authentication required",
                    (String) request.getAttribute("requestId"));
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        final SupabaseJwtValidator.DecodedJwt decoded;
        try {
            decoded = jwtValidator.validate(token);
        } catch (SupabaseJwtValidator.JwtValidationException ex) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    mapCode(ex.getCode()), ex.getMessage(),
                    (String) request.getAttribute("requestId"));
            return;
        }
        Optional<PlatformUser> user = userService.loadBySupabaseSub(decoded.sub());
        if (user.isEmpty()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNKNOWN_USER", "Account is not registered on this platform",
                    (String) request.getAttribute("requestId"));
            return;
        }
        PlatformUser principal = user.get();
        var authorities = principal.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        auth.setDetails(Map.of(
                "supabaseSub", principal.getSupabaseSub(),
                "requestId", request.getAttribute("requestId")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        // Downstream ownership checks read this without re-parsing the token.
        request.setAttribute("platformUser", principal);
        chain.doFilter(request, response);
    }

    private static void ensureRequestId(HttpServletRequest request, HttpServletResponse response) {
        Object existing = request.getAttribute("requestId");
        if (existing == null) {
            String id = UUID.randomUUID().toString();
            request.setAttribute("requestId", id);
            response.setHeader("X-Request-Id", id);
        } else {
            response.setHeader("X-Request-Id", String.valueOf(existing));
        }
    }

    private static String mapCode(String validatorCode) {
        return switch (validatorCode) {
            case "expired" -> "TOKEN_EXPIRED";
            case "bad_signature", "malformed_token" -> "TOKEN_INVALID";
            case "bad_issuer", "bad_audience" -> "TOKEN_INVALID";
            case "missing_token" -> "UNAUTHENTICATED";
            case "not_yet_valid" -> "TOKEN_INVALID";
            case "verifier_not_configured" -> "AUTH_MISCONFIGURED";
            default -> "UNAUTHENTICATED";
        };
    }

    private void writeError(HttpServletResponse response, int status, String code,
            String message, String requestId) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "success", false,
                "error", Map.of("code", code, "message", message, "details", Map.of()),
                "requestId", requestId == null ? UUID.randomUUID().toString() : requestId);
        mapper.writeValue(response.getWriter(), body);
    }
}
