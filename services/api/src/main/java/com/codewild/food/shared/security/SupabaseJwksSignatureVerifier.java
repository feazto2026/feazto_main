package com.codewild.food.shared.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Production {@link SupabaseJwtValidator.SignatureVerifier} backed by the Supabase JWKS.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Downloads {@code ${SUPABASE_URL}/auth/v1/.well-known/jwks.json} (URL overridable
 *       via {@code security.jwt.jwks-url}), caches the raw JWKS document for 10 minutes
 *       (Caffeine, {@code expireAfterWrite}).</li>
 *   <li>Selects the JWK by {@code kid}; unknown or missing {@code kid} forces exactly one
 *       refresh, then fails closed.</li>
 *   <li>Verifies the JWS with Nimbus JOSE: {@code JWKSet.parse}, {@code getKeyByKeyId},
 *       {@code SignedJWT.parse(...).verify(verifier)} with a strict
 *       {@code kid + alg} allowlist ({@code RS256}/{@code ES256} only).</li>
 * </ul>
 *
 * <p>Fail-closed contract: any fetch/parse/verify error returns {@code false}.
 * The filter then answers 401; it never skips authentication. No tokens, keys,
 * or PII are logged.
 */
@Component
public class SupabaseJwksSignatureVerifier
        implements SupabaseJwtValidator.SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SupabaseJwksSignatureVerifier.class);

    /** Strict JWS algorithm allowlist — Supabase project keys are RS256/ES256. */
    static final Set<String> ALLOWED_ALGS = Set.of("RS256", "ES256");

    private final String jwksUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final Cache<String, String> jwksCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(4)
            .build();

    public SupabaseJwksSignatureVerifier(
            @Value("${security.jwt.jwks-url:${SUPABASE_URL:}/auth/v1/.well-known/jwks.json}")
            String jwksUrl) {
        this.jwksUrl = jwksUrl;
    }

    @Override
    public boolean verify(String signingInput, String signature, String kid) throws Exception {
        String jwks = cachedJwks();
        if (jwks == null || jwks.isBlank()) {
            return false;
        }
        try {
            return verifyWithJwks(jwks, signingInput, signature, kid);
        } catch (UnknownKidException e) {
            // Key rotation: refresh once, then retry exactly once.
            jwksCache.invalidateAll();
            String fresh = cachedJwks();
            if (fresh == null || fresh.isBlank()) {
                return false;
            }
            try {
                return verifyWithJwks(fresh, signingInput, signature, kid);
            } catch (Exception retryEx) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String cachedJwks() {
        String cached = jwksCache.getIfPresent("jwks");
        if (cached != null) {
            return cached;
        }
        if (jwksUrl == null || jwksUrl.isBlank() || jwksUrl.endsWith("/auth/v1/.well-known/jwks.json")
                && jwksUrl.length() <= "/auth/v1/.well-known/jwks.json".length()) {
            // SUPABASE_URL not configured — fail closed without network call.
            log.warn("JWKS URL is not configured; JWT validation will fail closed");
            return null;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(jwksUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200 || res.body() == null) {
                return null;
            }
            jwksCache.put("jwks", res.body());
            return res.body();
        } catch (Exception e) {
            log.debug("JWKS fetch failed; failing closed");
            return null; // fail closed; caller returns false
        }
    }

    /**
     * Nimbus-backed verification with strict {@code kid + alg} allowlisting.
     *
     * @throws UnknownKidException when {@code kid} is missing or not in the set
     *         (caller refreshes once, then fails closed).
     */
    boolean verifyWithJwks(String jwksJson, String signingInput, String signature,
            String kid) throws Exception {
        if (kid == null || kid.isBlank()) {
            throw new UnknownKidException("missing kid");
        }
        final JWKSet set;
        try {
            set = JWKSet.parse(jwksJson);
        } catch (Exception ex) {
            log.debug("JWKS parse failed; failing closed");
            return false;
        }
        JWK key = set.getKeyByKeyId(kid);
        if (key == null) {
            throw new UnknownKidException(kid);
        }
        // JWK-level allowlist: a key advertising a non-allowlisted alg is never trusted.
        if (key.getAlgorithm() != null && !ALLOWED_ALGS.contains(key.getAlgorithm().getName())) {
            log.debug("JWK alg not allowlisted; failing closed");
            return false;
        }
        final SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(signingInput + "." + signature);
        } catch (Exception ex) {
            return false;
        }
        String headerAlg = jwt.getHeader() != null && jwt.getHeader().getAlgorithm() != null
                ? jwt.getHeader().getAlgorithm().getName()
                : null;
        if (headerAlg == null || !ALLOWED_ALGS.contains(headerAlg)) {
            return false;
        }
        final JWSVerifier verifier;
        try {
            if (key instanceof RSAKey rsa) {
                // RS256 must verify with an RSA key; cross-family use is rejected.
                if (!"RS256".equals(headerAlg)) {
                    return false;
                }
                verifier = new RSASSAVerifier(rsa);
            } else if (key instanceof ECKey ec) {
                // ES256 must verify with an EC key.
                if (!"ES256".equals(headerAlg)) {
                    return false;
                }
                verifier = new ECDSAVerifier(ec);
            } else {
                // oct / OKP / unknown key types are never trusted for Supabase JWTs.
                return false;
            }
            return jwt.verify(verifier);
        } catch (Exception ex) {
            return false;
        }
    }

    static final class UnknownKidException extends Exception {
        UnknownKidException(String msg) {
            super(msg);
        }
    }
}
