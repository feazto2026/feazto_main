package com.codewild.food.shared.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Validates Supabase-issued JWTs (access tokens) on every authenticated request.
 *
 * <p>Production wiring (see {@code application-security.example.yml}):
 * <ol>
 *   <li>Fetch the Supabase JWKS from {@code ${SUPABASE_URL}/auth/v1/.well-known/jwks.json}
 *       (keyed by {@code kid}), cache it for {@code security.jwt.jwks-ttl} (default 10 min).</li>
 *   <li>Verify the JWS signature with the matching JWK (ES256/RS256 per Supabase project keys).
 *       Recommended library: {@code com.nimbusds:nimbus-jose-jwt}.</li>
 *   <li>Then run {@link #validateClaimsUntrusted(JsonNode, String, long)} for claim checks.</li>
 * </ol>
 *
 * <p>This class ships the claim-validation half fully implemented and leaves the
 * cryptographic signature half behind a small {@link SignatureVerifier} seam so the
 * service compiles and tests without network access, while production injects the
 * Nimbus-backed verifier. Requests MUST fail closed when no verifier is configured.
 *
 * <p>Required claims: {@code sub}, {@code exp}. Validated: {@code iss}, {@code aud} (optional
 * but recommended), expiry with 60s clock skew, {@code nbf} when present.
 */
public class SupabaseJwtValidator {

    /** Clock skew tolerance for exp/nbf comparisons. */
    public static final long CLOCK_SKEW_SECONDS = 60L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String expectedIssuer;
    private final String expectedAudience; // nullable: enforced only when configured
    private final SignatureVerifier signatureVerifier; // null => fail closed

    /**
     * @param expectedIssuer   e.g. {@code https://<ref>.supabase.co/auth/v1}. Null disables iss check (not recommended).
     * @param expectedAudience e.g. {@code authenticated} or project ref. Null skips aud check.
     * @param signatureVerifier verifies JWS signature against Supabase JWKS; null fails closed.
     */
    public SupabaseJwtValidator(String expectedIssuer, String expectedAudience,
            SignatureVerifier signatureVerifier) {
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.signatureVerifier = signatureVerifier;
    }

    /** Fail-closed constructor used in tests/local stubs: signature not verified. */
    public static SupabaseJwtValidator insecureStubForTests(String expectedIssuer) {
        return new SupabaseJwtValidator(expectedIssuer, null, null);
    }

    /**
     * Full validation entry point for the security filter.
     *
     * @throws JwtValidationException on any failure; callers translate to 401.
     */
    public DecodedJwt validate(String token) throws JwtValidationException {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException("missing_token", "Missing bearer token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtValidationException("malformed_token", "Malformed JWT");
        }
        if (signatureVerifier == null) {
            // FAIL CLOSED: without a JWKS-backed verifier we must not authenticate anyone.
            throw new JwtValidationException("verifier_not_configured",
                    "JWT signature verifier is not configured");
        }
        try {
            if (!signatureVerifier.verify(parts[0] + "." + parts[1], parts[2], kidOf(parts[0]))) {
                throw new JwtValidationException("bad_signature", "Invalid token signature");
            }
        } catch (JwtValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtValidationException("bad_signature", "Invalid token signature");
        }
        JsonNode payload = decodePayload(parts[1]);
        return validateClaimsUntrusted(payload, expectedAudience, Instant.now().getEpochSecond());
    }

    /**
     * Validates already-decoded claims (used after signature verification, and directly
     * unit-testable). Package-visible for tests.
     */
    DecodedJwt validateClaimsUntrusted(JsonNode payload, String audience, long nowEpochSeconds)
            throws JwtValidationException {
        String sub = textOrNull(payload, "sub");
        if (sub == null || sub.isBlank()) {
            throw new JwtValidationException("missing_sub", "Token has no subject");
        }
        JsonNode expNode = payload.get("exp");
        if (expNode == null || !expNode.isNumber()) {
            throw new JwtValidationException("missing_exp", "Token has no expiry");
        }
        long exp = expNode.asLong();
        if (nowEpochSeconds > exp + CLOCK_SKEW_SECONDS) {
            throw new JwtValidationException("expired", "Token has expired");
        }
        JsonNode nbfNode = payload.get("nbf");
        if (nbfNode != null && nbfNode.isNumber()
                && nowEpochSeconds + CLOCK_SKEW_SECONDS < nbfNode.asLong()) {
            throw new JwtValidationException("not_yet_valid", "Token is not yet valid");
        }
        if (expectedIssuer != null) {
            String iss = textOrNull(payload, "iss");
            if (!Objects.equals(expectedIssuer, iss)) {
                throw new JwtValidationException("bad_issuer", "Unexpected token issuer");
            }
        }
        if (audience != null) {
            // aud may be a string or array per RFC 7519.
            JsonNode aud = payload.get("aud");
            boolean match = false;
            if (aud != null) {
                if (aud.isArray()) {
                    for (JsonNode a : aud) {
                        if (audience.equals(a.asText())) {
                            match = true;
                            break;
                        }
                    }
                } else if (audience.equals(aud.asText())) {
                    match = true;
                }
            }
            if (!match) {
                throw new JwtValidationException("bad_audience", "Unexpected token audience");
            }
        }
        String phone = textOrNull(payload, "phone");
        String roleClaim = textOrNull(payload, "role");
        return new DecodedJwt(sub, exp, phone, roleClaim);
    }

    private JsonNode decodePayload(String b64) throws JwtValidationException {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(pad(b64));
            return mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new JwtValidationException("malformed_token", "Cannot decode token payload");
        }
    }

    private static String pad(String s) {
        int rem = s.length() % 4;
        if (rem == 2) {
            return s + "==";
        }
        if (rem == 3) {
            return s + "=";
        }
        return s;
    }

    private String kidOf(String b64Header) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(pad(b64Header));
            JsonNode header = mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
            return textOrNull(header, "kid");
        } catch (Exception e) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /** Verifies {@code signingInput} against {@code signature} using the JWK for {@code kid}. */
    public interface SignatureVerifier {
        boolean verify(String signingInput, String signature, String kid) throws Exception;
    }

    /** Minimal validated view passed to {@link PlatformUserService}. */
    public record DecodedJwt(String sub, long exp, String phone, String roleClaim) {
    }

    /** Machine-readable auth failure; the filter maps {@code code} to error responses. */
    public static class JwtValidationException extends Exception {
        private final String code;

        public JwtValidationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
