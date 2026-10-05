package com.codewild.food.shared.security;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Request-scoped auth helpers over the {@link PlatformUser} principal.
 *
 * <p>Principal contract (set by {@link JwtAuthenticationFilter}):
 * {@code Authentication.getPrincipal()} is ALWAYS a {@link PlatformUser}
 * (never a raw {@code String} or JWT claims map). Roles/authorities are derived
 * server-side from {@code user_roles}; the JWT {@code role} claim is ignored.
 *
 * <p>Ownership + state checks belong in {@link PermissionEvaluator} +
 * {@link VendorRiderApprovalGate}; this class only exposes the current principal
 * and thin role/owner conveniences for services.
 */
@Component
public class SecurityUtils {

  private SecurityUtils() {}

  /** Current authenticated principal; throws 403 when unauthenticated. */
  public static PlatformUser currentUser() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || a.getPrincipal() instanceof String
        && "anonymousUser".equals(a.getPrincipal())) {
      throw new AccessDeniedException("Unauthenticated");
    }
    Object principal = a == null ? null : a.getPrincipal();
    if (principal instanceof PlatformUser u) {
      return u;
    }
    // Fail closed: HS256 String principals no longer exist (single Supabase chain).
    // Any unexpected principal type is unauthenticated.
    throw new AccessDeniedException("Unauthenticated");
  }

  /** Platform user id (UUID text) for ownership columns ({@code user_id}). */
  public static String currentUserId() {
    return currentUser().getUserId().toString();
  }

  public static UUID currentUserUuid() {
    return currentUser().getUserId();
  }

  public static boolean hasRole(String role) {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null) {
      return false;
    }
    // Prefer the principal's server-side roles when available.
    Object principal = a.getPrincipal();
    if (principal instanceof PlatformUser u) {
      Role want = Role.parseOrNull(role == null ? null
          : role.startsWith("ROLE_") ? role.substring(5) : role);
      if (want != null && u.hasRole(want)) {
        return true;
      }
    }
    String want1 = role;
    String want2 = role != null && role.startsWith("ROLE_") ? role.substring(5) : "ROLE_" + role;
    return a.getAuthorities().stream()
        .anyMatch(g -> g.getAuthority().equals(want1) || g.getAuthority().equals(want2));
  }

  public static boolean hasPermission(Permission permission) {
    try {
      return currentUser().hasPermission(permission);
    } catch (AccessDeniedException e) {
      return false;
    }
  }

  public static void requireOwnerOrAdmin(String ownerUserId) {
    if (hasRole("ADMIN") || hasRole("SUPER_ADMIN")) {
      return;
    }
    String me;
    try {
      me = currentUserId();
    } catch (AccessDeniedException e) {
      throw new AccessDeniedException("Not owner");
    }
    if (ownerUserId != null && ownerUserId.equals(me)) {
      return;
    }
    throw new AccessDeniedException("Not owner");
  }

  /** Ownership via UUID profile owner (null-safe, fail closed). */
  public static void requireOwnerOrAdmin(UUID ownerUserId) {
    requireOwnerOrAdmin(ownerUserId == null ? null : ownerUserId.toString());
  }

  /**
   * Auth details set by the filter ({@code supabaseSub}, {@code requestId}).
   * Legacy jjwt {@code Claims} details no longer exist.
   */
  public static String detailAsString(String key) {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a != null && a.getDetails() instanceof Map<?, ?> m) {
      Object v = m.get(key);
      return v == null ? null : String.valueOf(v);
    }
    return null;
  }

  /** Backwards-compatible alias for {@link #detailAsString}. */
  public static String claimAsString(String key) {
    return detailAsString(key);
  }
}
