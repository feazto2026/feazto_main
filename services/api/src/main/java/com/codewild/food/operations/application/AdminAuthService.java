package com.codewild.food.operations.application;

import com.codewild.food.identity.infrastructure.PlatformUserRepository;
import com.codewild.food.identity.infrastructure.UserRoleRepository;
import com.codewild.food.operations.api.OperationsDtos.AdminUserDto;
import com.codewild.food.operations.api.OperationsDtos.LoginResponse;
import com.codewild.food.shared.audit.AuditService;
import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import com.codewild.food.shared.security.Permission;
import com.codewild.food.shared.security.PlatformUser;
import com.codewild.food.shared.security.Role;
import com.codewild.food.shared.security.SupabaseJwtValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin Web authentication with Supabase Auth as password truth.
 *
 * <p>Login flow (fail closed on every branch):
 * <ol>
 *   <li>Redis throttle: {@code admin:login:req:ip:{ip}} (30/h) +
 *       {@code admin:login:req:email:{lower}} (10/h), 1 h TTL. Redis
 *       unavailable fails closed ({@code INTERNAL}), never unthrottled.</li>
 *   <li>Supabase GoTrue password grant:
 *       {@code POST ${SUPABASE_URL}/auth/v1/token?grant_type=password} with the
 *       {@code apikey} ({@code SUPABASE_ANON_KEY}, never SERVICE_ROLE) header,
 *       5 s connect + 5 s request timeout. Wrong credentials answer
 *       {@code INVALID_CREDENTIALS} (401).</li>
 *   <li>The returned {@code access_token} is validated through
 *       {@link SupabaseJwtValidator} (JWKS signature + claims).</li>
 *   <li>The platform row is loaded via
 *       {@code PlatformUserRepository#findActiveByAuthUserId} (ACTIVE +
 *       not soft-deleted only) and roles via
 *       {@code UserRoleRepository#findRoleCodesByUserId} — never from JWT claims.</li>
 *   <li>The top admin role wins by priority
 *       {@code SUPER_ADMIN > ADMIN > OPS_ADMIN > FINANCE_ADMIN > SUPPORT_ADMIN};
 *       a user with no admin-family role answers {@code INVALID_CREDENTIALS}
 *       (enumeration resistance: existence, password, status and role failures
 *       are indistinguishable).</li>
 *   <li>Scopes are {@code Permission.forRole(top)} filtered to the 15 frontend
 *       {@code SCOPES} (see {@code apps/admin-web/src/auth/AuthContext.tsx}),
 *       in frontend order.</li>
 *   <li>{@code last_login_at} is updated and the attempt is audit-logged.</li>
 * </ol>
 *
 * <p>Privacy: passwords and tokens are never logged (not even masked) and never
 * appear in audit details. Logs carry masked emails only.
 */
@Service
public class AdminAuthService {

  private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

  /**
   * Frontend {@code SCOPES} order from
   * {@code apps/admin-web/src/auth/AuthContext.tsx} (15 scopes).
   */
  static final List<String> FRONTEND_SCOPES = List.of(
      "VENDOR_VIEW", "VENDOR_APPROVE", "VENDOR_SUSPEND",
      "ORDER_VIEW", "ORDER_CANCEL", "ORDER_REFUND",
      "RIDER_VIEW", "RIDER_APPROVE", "RIDER_SUSPEND",
      "FINANCE_VIEW", "PAYOUT_MANAGE",
      "SUPPORT_VIEW", "SUPPORT_ASSIGN",
      "AUDIT_VIEW", "SETTINGS_MANAGE");

  /** Top-role priority: the first admin-family role present wins. */
  private static final List<Role> TOP_ROLE_PRIORITY = List.of(
      Role.SUPER_ADMIN, Role.ADMIN, Role.OPS_ADMIN, Role.FINANCE_ADMIN, Role.SUPPORT_ADMIN);

  private static final int MAX_LOGINS_PER_IP_PER_HOUR = 30;
  private static final int MAX_LOGINS_PER_EMAIL_PER_HOUR = 10;

  private final PlatformUserRepository users;
  private final UserRoleRepository userRoles;
  private final SupabaseJwtValidator jwtValidator;
  private final StringRedisTemplate redis;
  private final AuditService audit;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private final String supabaseUrl;
  private final String supabaseAnonKey;

  public AdminAuthService(
      PlatformUserRepository users,
      UserRoleRepository userRoles,
      SupabaseJwtValidator jwtValidator,
      StringRedisTemplate redis,
      AuditService audit,
      @Value("${supabase.url:${SUPABASE_URL:}}") String supabaseUrl,
      @Value("${supabase.anon-key:${SUPABASE_ANON_KEY:}}") String supabaseAnonKey) {
    this.users = users;
    this.userRoles = userRoles;
    this.jwtValidator = jwtValidator;
    this.redis = redis;
    this.audit = audit;
    this.supabaseUrl = supabaseUrl == null ? "" : supabaseUrl.trim().replaceAll("/+$", "");
    this.supabaseAnonKey = supabaseAnonKey == null ? "" : supabaseAnonKey.trim();
  }

  /**
   * Password login for Admin Web. Returns the Supabase access JWT plus the
   * resolved admin profile for the {@code ApiResponse} envelope.
   */
  public LoginResponse login(String rawEmail, String password, String clientIp) {
    String email = normaliseEmail(rawEmail);
    if (email.isEmpty() || password == null || password.isEmpty()) {
      throw new BusinessException(ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password");
    }
    enforceLoginThrottle(email, clientIp);
    if (supabaseUrl.isEmpty() || supabaseAnonKey.isEmpty()) {
      log.warn("Admin login misconfigured: SUPABASE_URL / SUPABASE_ANON_KEY missing");
      throw new BusinessException(ErrorCodes.AUTH_MISCONFIGURED,
          "Authentication is not configured");
    }
    String accessToken = goTruePasswordGrant(email, password);
    String sub = validateAccessToken(accessToken, email);
    final UUID subUuid;
    try {
      subUuid = UUID.fromString(sub);
    } catch (IllegalArgumentException e) {
      auditFailed(email);
      throw new BusinessException(ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password");
    }
    com.codewild.food.identity.domain.PlatformUser entity =
        users.findActiveByAuthUserId(subUuid).orElse(null);
    if (entity == null || entity.getId() == null) {
      auditFailed(email);
      throw new BusinessException(ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password");
    }
    Role top = topAdminRole(roleCodesFor(entity.getId()));
    if (top == null) {
      auditFailed(email);
      throw new BusinessException(ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password");
    }
    // Record last login. Best-effort: a persistence failure never fails the login.
    try {
      entity.setLastLoginAt(Instant.now());
      users.save(entity);
    } catch (Exception e) {
      log.warn("Admin last_login_at update failed for user {}", entity.getIdAsString());
    }
    AdminUserDto admin = toAdminUser(entity, top, email);
    audit.log(entity.getIdAsString(), "ADMIN_LOGIN", "PlatformUser",
        entity.getIdAsString(), "admin login email=" + maskEmail(email));
    log.info("Admin login success email={}", maskEmail(email));
    return new LoginResponse(accessToken, admin);
  }

  /**
   * Authenticated admin profile for Admin Web rehydration.
   * The admin-family role gate is enforced by {@code @PreAuthorize} on the
   * controller; this method maps the principal the same way {@link #login}
   * does (top role + frontend-filtered scopes, identity from the DB row).
   */
  @Transactional(readOnly = true)
  public AdminUserDto me(PlatformUser principal) {
    if (principal == null) {
      throw new BusinessException(ErrorCodes.UNAUTHENTICATED, "Authentication required");
    }
    Role top = topAdminRole(principal.getRoles().stream().map(Role::name).toList());
    if (top == null) {
      // Fall back to a fresh DB role read; still fail closed when none is admin.
      top = topAdminRole(roleCodesFor(principal.getUserId()));
    }
    if (top == null) {
      throw new BusinessException(ErrorCodes.FORBIDDEN,
          "You do not have access to this resource");
    }
    com.codewild.food.identity.domain.PlatformUser entity =
        users.findById(principal.getUserId()).orElse(null);
    if (entity == null) {
      throw new BusinessException(ErrorCodes.UNKNOWN_USER,
          "Account is not registered on this platform");
    }
    return toAdminUser(entity, top, null);
  }

  // ------------------------------------------------------------------
  // Supabase GoTrue password grant (password truth)
  // ------------------------------------------------------------------

  private String goTruePasswordGrant(String email, String password) {
    final URI uri;
    try {
      uri = URI.create(supabaseUrl + "/auth/v1/token?grant_type=password");
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.AUTH_MISCONFIGURED,
          "Authentication is not configured");
    }
    final String body;
    try {
      body = mapper.writeValueAsString(Map.of("email", email, "password", password));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.INTERNAL, "Service temporarily unavailable");
    }
    final HttpResponse<String> res;
    try {
      HttpRequest req = HttpRequest.newBuilder(uri)
          .timeout(Duration.ofSeconds(5))
          .header("Content-Type", "application/json")
          .header("apikey", supabaseAnonKey)
          .header("Authorization", "Bearer " + supabaseAnonKey)
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      res = http.send(req, HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
      log.warn("Admin GoTrue request failed");
      throw new BusinessException(ErrorCodes.INTERNAL, "Service temporarily unavailable");
    }
    int status = res.statusCode();
    if (status == 400 || status == 401 || status == 422) {
      auditFailed(email);
      throw new BusinessException(ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password");
    }
    if (status == 429) {
      throw new BusinessException(ErrorCodes.RATE_LIMITED,
          "Too many login attempts. Try later.");
    }
    if (status != 200) {
      log.warn("Admin GoTrue unexpected status={}", status);
      throw new BusinessException(ErrorCodes.INTERNAL, "Service temporarily unavailable");
    }
    try {
      JsonNode root = mapper.readTree(res.body());
      String token = root.path("access_token").asText(null);
      if (token == null || token.isBlank()) {
        auditFailed(email);
        throw new BusinessException(ErrorCodes.INVALID_CREDENTIALS,
            "Invalid email or password");
      }
      return token;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.INTERNAL, "Service temporarily unavailable");
    }
  }

  private String validateAccessToken(String accessToken, String email) {
    try {
      return jwtValidator.validate(accessToken).sub();
    } catch (SupabaseJwtValidator.JwtValidationException e) {
      log.debug("Admin access token validation failed code={}", e.getCode());
      auditFailed(email);
      throw new BusinessException(ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password");
    }
  }

  // ------------------------------------------------------------------
  // Throttle (Redis, fail closed)
  // ------------------------------------------------------------------

  private void enforceLoginThrottle(String email, String clientIp) {
    String ipKey = "admin:login:req:ip:" + sanitizeIp(clientIp);
    String emailKey = "admin:login:req:email:" + sanitizeEmail(email);
    try {
      Long ipCount = redis.opsForValue().increment(ipKey);
      if (ipCount != null && ipCount == 1L) {
        redis.expire(ipKey, Duration.ofHours(1));
      }
      if (ipCount != null && ipCount > MAX_LOGINS_PER_IP_PER_HOUR) {
        throw new BusinessException(ErrorCodes.RATE_LIMITED,
            "Too many login attempts. Try later.");
      }
      Long emailCount = redis.opsForValue().increment(emailKey);
      if (emailCount != null && emailCount == 1L) {
        redis.expire(emailKey, Duration.ofHours(1));
      }
      if (emailCount != null && emailCount > MAX_LOGINS_PER_EMAIL_PER_HOUR) {
        throw new BusinessException(ErrorCodes.RATE_LIMITED,
            "Too many login attempts. Try later.");
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      // Redis unavailable — fail closed, never allow unthrottled password attempts.
      log.warn("Admin login throttle Redis unavailable; failing closed");
      throw new BusinessException(ErrorCodes.INTERNAL, "Service temporarily unavailable");
    }
  }

  // ------------------------------------------------------------------
  // Role / scope mapping
  // ------------------------------------------------------------------

  private List<String> roleCodesFor(UUID userId) {
    try {
      List<String> codes = userRoles.findRoleCodesByUserId(userId);
      return codes == null ? List.of() : codes;
    } catch (Exception e) {
      log.warn("Admin role lookup failed; failing closed");
      return List.of();
    }
  }

  static Role topAdminRole(Collection<String> codes) {
    if (codes == null || codes.isEmpty()) {
      return null;
    }
    Set<Role> have = EnumSet.noneOf(Role.class);
    for (String code : codes) {
      Role r = Role.parseOrNull(code);
      if (r != null) {
        have.add(r);
      }
    }
    for (Role want : TOP_ROLE_PRIORITY) {
      if (have.contains(want)) {
        return want;
      }
    }
    return null;
  }

  static List<String> scopesFor(Role top) {
    Set<String> granted = Permission.forRole(top).stream()
        .map(Enum::name)
        .collect(Collectors.toSet());
    return FRONTEND_SCOPES.stream().filter(granted::contains).toList();
  }

  private AdminUserDto toAdminUser(
      com.codewild.food.identity.domain.PlatformUser entity, Role top, String emailFallback) {
    String email = entity.getEmail();
    if ((email == null || email.isBlank()) && emailFallback != null) {
      email = emailFallback;
    }
    String name = entity.getDisplayName();
    if (name != null && name.isBlank()) {
      name = null;
    }
    return new AdminUserDto(entity.getIdAsString(), email, name, top.name(), scopesFor(top));
  }

  // ------------------------------------------------------------------
  // Helpers (never handle passwords/tokens here)
  // ------------------------------------------------------------------

  private void auditFailed(String email) {
    audit.log(null, "ADMIN_LOGIN_FAILED", "PlatformUser", null,
        "admin login failed email=" + maskEmail(email));
  }

  private static String normaliseEmail(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase();
  }

  private static String sanitizeIp(String ip) {
    String v = ip == null || ip.isBlank() ? "unknown" : ip.trim();
    return v.replaceAll("[^0-9a-zA-Z.:]", "_");
  }

  private static String sanitizeEmail(String emailLower) {
    return emailLower.replaceAll("[^0-9a-zA-Z@.+_-]", "_");
  }

  static String maskEmail(String email) {
    if (email == null || email.isBlank()) {
      return "••••";
    }
    int at = email.indexOf('@');
    if (at <= 0) {
      return "••••";
    }
    String local = email.substring(0, at);
    String domain = email.substring(at);
    String shown = local.length() <= 1 ? local + "***" : local.charAt(0) + "***";
    return shown + domain;
  }
}
