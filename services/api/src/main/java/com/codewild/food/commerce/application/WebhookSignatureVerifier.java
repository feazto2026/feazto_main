package com.codewild.food.commerce.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies payment-provider webhook authenticity via HMAC-SHA256.
 *
 * <p>Contract: the provider signs the raw request body with the shared
 * webhook secret and sends the lowercase hex digest in
 * {@code X-Webhook-Signature}. Verification is constant-time.
 *
 * <p>Fail-closed when {@code payments.webhook.secret} is configured; when it
 * is blank (local dev only) webhooks are accepted with a warning so provider
 * sandboxes without signing stay usable. Production MUST set the secret.
 */
@Component
public class WebhookSignatureVerifier {
  private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);

  private final String secret;

  public WebhookSignatureVerifier(
      @Value("${payments.webhook.secret:${PAYMENTS_WEBHOOK_SECRET:}}") String secret) {
    this.secret = secret == null ? "" : secret.trim();
  }

  public boolean isConfigured() {
    return !secret.isBlank();
  }

  /** Returns true when the signature is valid (or the verifier is unconfigured in dev). */
  public boolean verify(String rawBody, String signatureHeader) {
    if (!isConfigured()) {
      log.warn("payments.webhook.secret is not configured; accepting webhook without signature (dev only)");
      return true;
    }
    if (signatureHeader == null || signatureHeader.isBlank()) {
      return false;
    }
    String expected = hmacHex(rawBody == null ? "" : rawBody);
    if (expected == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        signatureHeader.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
  }

  private String hmacHex(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      log.warn("Webhook HMAC computation failed");
      return null;
    }
  }
}
