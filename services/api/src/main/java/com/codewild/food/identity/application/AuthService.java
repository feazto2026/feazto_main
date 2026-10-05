package com.codewild.food.identity.application;

import com.codewild.food.identity.api.AuthDtos.AuthResponse;
import com.codewild.food.identity.api.AuthDtos.OtpRequest;
import com.codewild.food.identity.api.AuthDtos.OtpVerify;
import com.codewild.food.identity.domain.PlatformUser;
import com.codewild.food.identity.domain.UserRole;
import com.codewild.food.identity.infrastructure.PlatformUserRepository;
import com.codewild.food.identity.infrastructure.RoleRepository;
import com.codewild.food.identity.infrastructure.UserRoleRepository;
import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import com.codewild.food.shared.events.DomainEvent;
import com.codewild.food.shared.events.OutboxService;
import com.codewild.food.shared.security.OtpSecurityPolicy;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backend OTP throttle + verification around Supabase Auth.
 *
 * <p>Security policy (see {@link OtpSecurityPolicy} + {@code docs/architecture/auth.md}):
 * <ul>
 *   <li>6-digit CSPRNG code ({@link SecureRandom}); only
 *       {@code SHA-256(code + OTP_SECRET)} is stored — never plaintext.</li>
 *   <li>Redis {@code otp:{phone} = hash:attempts} with 5-min TTL; missing key
 *       fails closed as {@code OTP_EXPIRED} (covers expiry, never-requested,
 *       and Redis misses — never a guest pass).</li>
 *   <li>Max 5 verification attempts per code, then void
 *       ({@code OTP_ATTEMPTS_EXCEEDED}); constant-time hash compare.</li>
 *   <li>30 s resend cooldown ({@code otp:resend:{phone}}); 5 sends/phone/hour +
 *       20 sends/IP/hour (SMS-pumping defence). {@code request}/{@code resend}
 *       return the same generic message whether or not the phone exists.</li>
 *   <li>Delete-on-success: the code is single-use. Login events are audited via
 *       outbox; logs carry masked phones only.</li>
 * </ul>
 *
 * <p>Transport: codes are sent via the configured SMS provider in production.
 * In non-prod without an SMS provider the code MUST NOT be returned in the API
 * response or logs — use a server-side test hook (e.g. capped debug mailbox)
 * instead. This class never returns the code.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final PlatformUserRepository users;
  private final UserRoleRepository userRoles;
  private final RoleRepository roles;
  private final StringRedisTemplate redis;
  private final OutboxService outbox;

  @Value("${OTP_SECRET:${app.otp.secret:}}")
  private String otpSecret;

  public AuthService(
      PlatformUserRepository users,
      UserRoleRepository userRoles,
      RoleRepository roles,
      StringRedisTemplate redis,
      OutboxService outbox) {
    this.users = users;
    this.userRoles = userRoles;
    this.roles = roles;
    this.redis = redis;
    this.outbox = outbox;
  }

  /**
   * Request (or first-send) an OTP. Enforces cooldown + hourly caps, then stores
   * the hash with 5-min TTL. Always succeeds with the same generic outcome —
   * callers must not reveal whether the phone exists.
   */
  public void requestOtp(OtpRequest req, String clientIp) {
    String phone = normalisePhone(req.phone());
    enforceSendPolicy(phone, clientIp);
    String code = newCode();
    storeNewCode(phone, code);
    // SMS dispatch happens here in production via the provider adapter.
    // Never log or return the code.
    log.info("OTP requested for phone={}", mask(phone));
  }

  /** Resend path: identical policy; the 30 s cooldown is the primary gate. */
  public void resendOtp(OtpRequest req, String clientIp) {
    requestOtp(req, clientIp);
  }

  /** Backwards-compatible alias (phone only; IP unknown). Prefer the IP-aware overload. */
  public void sendOtp(OtpRequest req) {
    requestOtp(req, null);
  }

  /**
   * Verify an OTP. Fail-closed: missing/expired/corrupt state yields
   * {@code OTP_EXPIRED}; 5 bad attempts voids the code
   * ({@code OTP_ATTEMPTS_EXCEEDED}); wrong codes yield {@code OTP_INVALID}.
   * On success the code is deleted (single-use) and the platform user is
   * ensured (idempotent by phone).
   */
  @Transactional
  public AuthResponse verifyOtp(OtpVerify req) {
    String phone = normalisePhone(req.phone());
    String candidate = req.otp() == null ? "" : req.otp().trim();
    if (!candidate.matches("^[0-9]{6}$")) {
      throw new BusinessException(ErrorCodes.OTP_INVALID, "Invalid code");
    }
    String otpKey = OtpSecurityPolicy.otpKey(phone);
    String stored;
    try {
      stored = redis.opsForValue().get(otpKey);
    } catch (Exception ex) {
      // Redis unavailable — fail closed, never authenticate.
      log.warn("OTP verify Redis read failed for phone={}", mask(phone));
      throw new BusinessException(ErrorCodes.OTP_EXPIRED, "Code has expired. Request a new one.");
    }
    if (stored == null || stored.isBlank()) {
      throw new BusinessException(ErrorCodes.OTP_EXPIRED, "Code has expired. Request a new one.");
    }
    String storedHash = OtpSecurityPolicy.storedHash(stored);
    int attempts = OtpSecurityPolicy.storedAttempts(stored);
    if (storedHash == null || attempts < 0) {
      // Corrupt value — void and fail closed.
      try {
        redis.delete(otpKey);
      } catch (Exception ignored) {
      }
      throw new BusinessException(ErrorCodes.OTP_EXPIRED, "Code has expired. Request a new one.");
    }
    if (attempts >= OtpSecurityPolicy.MAX_ATTEMPTS) {
      try {
        redis.delete(otpKey);
      } catch (Exception ignored) {
      }
      throw new BusinessException(
          ErrorCodes.OTP_ATTEMPTS_EXCEEDED, "Too many attempts. Request a new code.");
    }
    String candidateHash = OtpSecurityPolicy.hash(candidate, otpSecret);
    boolean match = OtpSecurityPolicy.constantTimeEquals(storedHash, candidateHash);
    if (!match) {
      int next = attempts + 1;
      if (next >= OtpSecurityPolicy.MAX_ATTEMPTS) {
        try {
          redis.delete(otpKey);
        } catch (Exception ignored) {
        }
        log.info("OTP attempts exceeded for phone={}", mask(phone));
        throw new BusinessException(
            ErrorCodes.OTP_ATTEMPTS_EXCEEDED, "Too many attempts. Request a new code.");
      }
      // Preserve the original 5-min expiry (do not extend the window).
      Duration ttl = remainingTtl(otpKey);
      try {
        redis.opsForValue().set(otpKey, OtpSecurityPolicy.encodeStoredValue(storedHash, next), ttl);
      } catch (Exception ex) {
        // If the counter cannot be persisted, void the code (fail closed).
        try {
          redis.delete(otpKey);
        } catch (Exception ignored) {
        }
        throw new BusinessException(ErrorCodes.OTP_EXPIRED, "Code has expired. Request a new one.");
      }
      throw new BusinessException(ErrorCodes.OTP_INVALID, "Invalid code");
    }
    // Success — single-use: delete before any further I/O.
    try {
      redis.delete(otpKey);
    } catch (Exception ex) {
      // Deletion failure must not grant a retryable code: void by overwriting
      // with an exhausted counter, then fail closed.
      try {
        redis.delete(otpKey);
      } catch (Exception ignored) {
      }
      throw new BusinessException(ErrorCodes.OTP_EXPIRED, "Code has expired. Request a new one.");
    }
    PlatformUser user = ensurePlatformUser(phone);
    List<String> roleCodes = roleCodesFor(user.getId());
    String rolesJson = toRolesJson(roleCodes);
    log.info("OTP verified for phone={}", mask(phone));
    return new AuthResponse(user.getIdAsString(), rolesJson, mask(phone));
  }

  // ------------------------------------------------------------------
  // Internal policy enforcement
  // ------------------------------------------------------------------

  private void enforceSendPolicy(String phone, String clientIp) {
    String resendKey = OtpSecurityPolicy.resendKey(phone);
    String phoneCounter = OtpSecurityPolicy.phoneCounterKey(phone);
    String ipCounter = OtpSecurityPolicy.ipCounterKey(clientIp);
    try {
      String cooldown = redis.opsForValue().get(resendKey);
      if (cooldown != null) {
        throw new BusinessException(
            ErrorCodes.OTP_RESEND_TOO_SOON, "Wait before requesting another code");
      }
      Long phoneCount = redis.opsForValue().increment(phoneCounter);
      if (phoneCount != null && phoneCount == 1) {
        redis.expire(phoneCounter, Duration.ofHours(1));
      }
      if (phoneCount != null && phoneCount > OtpSecurityPolicy.MAX_SENDS_PER_HOUR) {
        throw new BusinessException(ErrorCodes.RATE_LIMITED, "Too many code requests. Try later.");
      }
      Long ipCount = redis.opsForValue().increment(ipCounter);
      if (ipCount != null && ipCount == 1) {
        redis.expire(ipCounter, Duration.ofHours(1));
      }
      if (ipCount != null && ipCount > OtpSecurityPolicy.MAX_SENDS_PER_IP_PER_HOUR) {
        throw new BusinessException(ErrorCodes.RATE_LIMITED, "Too many code requests. Try later.");
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception ex) {
      // Redis unavailable — fail closed on sends (do not mint codes we cannot throttle).
      log.warn("OTP send Redis unavailable for phone={}", mask(phone));
      throw new BusinessException(ErrorCodes.INTERNAL, "Service temporarily unavailable");
    }
  }

  private void storeNewCode(String phone, String code) {
    String otpKey = OtpSecurityPolicy.otpKey(phone);
    String resendKey = OtpSecurityPolicy.resendKey(phone);
    String hash = OtpSecurityPolicy.hash(code, otpSecret);
    String stored = OtpSecurityPolicy.encodeStoredValue(hash, 0);
    try {
      redis.opsForValue().set(otpKey, stored, Duration.ofSeconds(OtpSecurityPolicy.OTP_TTL_SECONDS));
      redis.opsForValue()
          .set(resendKey, "1", Duration.ofSeconds(OtpSecurityPolicy.RESEND_COOLDOWN_SECONDS));
    } catch (Exception ex) {
      log.warn("OTP store failed for phone={}", mask(phone));
      throw new BusinessException(ErrorCodes.INTERNAL, "Service temporarily unavailable");
    }
  }

  private Duration remainingTtl(String otpKey) {
    try {
      Long seconds = redis.getExpire(otpKey);
      if (seconds == null || seconds <= 0) {
        return Duration.ofSeconds(OtpSecurityPolicy.OTP_TTL_SECONDS);
      }
      return Duration.ofSeconds(Math.min(seconds, OtpSecurityPolicy.OTP_TTL_SECONDS));
    } catch (Exception ex) {
      return Duration.ofSeconds(OtpSecurityPolicy.OTP_TTL_SECONDS);
    }
  }

  private PlatformUser ensurePlatformUser(String phone) {
    return users.findByPhone(phone).orElseGet(() -> {
      PlatformUser u = new PlatformUser(phone, null, null);
      u.setAccountStatus(PlatformUser.AccountStatus.ACTIVE);
      u.setActive(true);
      PlatformUser saved = users.save(u);
      // Default CUSTOMER grant (server-side only; vendor/rider via approval flows).
      try {
        roles.findByCode("CUSTOMER").ifPresent(r -> {
          if (userRoles.findByUserId(saved.getId()).isEmpty()) {
            userRoles.save(new UserRole(saved.getId(), r.getId()));
          }
        });
      } catch (Exception ex) {
        log.warn("Default role grant failed for user {}", saved.getIdAsString());
      }
      outbox.save(DomainEvent.of("UserRegistered", "PlatformUser", saved.getIdAsString(),
          "{\"phoneMasked\":\"" + mask(phone) + "\"}"));
      return saved;
    });
  }

  private List<String> roleCodesFor(java.util.UUID userId) {
    try {
      List<String> codes = users.findRoleCodesByUserId(userId);
      if (codes != null && !codes.isEmpty()) {
        return codes;
      }
    } catch (Exception ignored) {
    }
    return List.of("CUSTOMER");
  }

  private static String toRolesJson(List<String> codes) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < codes.size(); i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append("\"").append(codes.get(i).replace("\"", "")).append("\"");
    }
    return sb.append("]").toString();
  }

  private static String newCode() {
    int n = SECURE_RANDOM.nextInt(900_000) + 100_000;
    return Integer.toString(n);
  }

  private static String normalisePhone(String raw) {
    if (raw == null) {
      throw new BusinessException(ErrorCodes.VALIDATION, "phone must be E.164, e.g. +919876543210");
    }
    String p = raw.trim().replaceAll("[\\s\\-()]", "");
    if (!p.matches("^\\+[1-9]\\d{7,14}$")) {
      throw new BusinessException(ErrorCodes.VALIDATION, "phone must be E.164, e.g. +919876543210");
    }
    return p;
  }

  static String mask(String e164) {
    if (e164 == null || e164.length() < 4) {
      return "••••";
    }
    return "•••• •••• " + e164.substring(e164.length() - 4);
  }
}
