package com.codewild.food.shared.idempotency;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Normalises idempotency keys on critical mutations (Supabase 0007
 * {@code idempotency_keys} UNIQUE(scope,key); Redis short-term ledger in
 * {@link IdempotencyService}; DB UNIQUE constraints as backstop).
 *
 * <p>Accepts {@code Idempotency-Key} (canonical) and {@code X-Idempotency-Key}
 * (common gateway alias); downstream services read the canonical request
 * attribute. Missing keys are NOT rejected here — services treat a missing
 * key as a non-idempotent single attempt.
 */
@Component
public class IdempotencyFilter implements Filter {
  public static final String HEADER = "Idempotency-Key";
  public static final String HEADER_ALIAS = "X-Idempotency-Key";
  public static final String ATTRIBUTE = "Idempotency-Key";

  private static final Set<String> CRITICAL_PREFIXES = Set.of(
      "/api/v1/orders", "/api/v1/payments", "/api/v1/refunds",
      "/api/v1/subscriptions", "/api/v1/deliveries");

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest h = (HttpServletRequest) req;
    if ("POST".equalsIgnoreCase(h.getMethod())
        && CRITICAL_PREFIXES.stream().anyMatch(p -> h.getRequestURI().startsWith(p))) {
      String key = h.getHeader(HEADER);
      if (key == null || key.isBlank()) {
        key = h.getHeader(HEADER_ALIAS);
      }
      if (key != null && !key.isBlank()) {
        h.setAttribute(ATTRIBUTE, key.trim());
      }
    }
    chain.doFilter(req, res);
  }
}
