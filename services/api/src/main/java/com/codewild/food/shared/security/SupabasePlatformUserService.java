package com.codewild.food.shared.security;

import com.codewild.food.customer.infrastructure.CustomerProfileRepository;
import com.codewild.food.fulfillment.infrastructure.RiderRepository;
import com.codewild.food.identity.infrastructure.PlatformUserRepository;
import com.codewild.food.identity.infrastructure.UserRoleRepository;
import com.codewild.food.marketplace.infrastructure.VendorRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production {@link PlatformUserService} backed by Supabase-provisioned Postgres.
 *
 * <p>Queries (fail closed on every branch):
 * <ol>
 *   <li>{@code platform_users} by {@code auth_user_id} (= Supabase JWT {@code sub},
 *       UUID text) constrained to {@code account_status='ACTIVE'} — see
 *       {@code PlatformUserRepository#findActiveByAuthUserId}. Unknown {@code sub},
 *       malformed UUID, or non-ACTIVE status yields {@code Optional.empty()}
 *       (filter answers 401).</li>
 *   <li>{@code user_roles JOIN roles} for canonical role codes — never the JWT
 *       {@code role} claim (which is informational and untrusted).</li>
 *   <li>{@code customer_profiles / vendors / riders} by {@code user_id}
 *       for ownership + APPROVED-gate fields.</li>
 * </ol>
 *
 * <p>Maps the JPA entity ({@code identity.domain.PlatformUser}, UUID PK) to the
 * request-scoped principal ({@code shared.security.PlatformUser}) on every request
 * so role/status changes take effect on the next call without waiting for token expiry.
 *
 * <p>Never logs full phones, tokens, or OTP values — masked phone only.
 */
@Service
@Primary
public class SupabasePlatformUserService implements PlatformUserService {

  private static final Logger log = LoggerFactory.getLogger(SupabasePlatformUserService.class);

  private final PlatformUserRepository users;
  private final UserRoleRepository userRoles;
  private final CustomerProfileRepository customers;
  private final VendorRepository vendors;
  private final RiderRepository riders;

  public SupabasePlatformUserService(
      PlatformUserRepository users,
      UserRoleRepository userRoles,
      CustomerProfileRepository customers,
      VendorRepository vendors,
      RiderRepository riders) {
    this.users = users;
    this.userRoles = userRoles;
    this.customers = customers;
    this.vendors = vendors;
    this.riders = riders;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PlatformUser> loadBySupabaseSub(String supabaseSub) {
    if (supabaseSub == null || supabaseSub.isBlank()) {
      return Optional.empty();
    }
    String sub = supabaseSub.trim();
    UUID subUuid;
    try {
      subUuid = UUID.fromString(sub);
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
    Optional<com.codewild.food.identity.domain.PlatformUser> row =
        users.findActiveByAuthUserId(subUuid);
    if (row.isEmpty()) {
      // Unknown sub or suspended/deactivated — fail closed, no guest principal.
      log.debug("Unknown or inactive platform user for sub prefix={}",
          sub.length() > 8 ? sub.substring(0, 8) + "…" : "…");
      return Optional.empty();
    }
    com.codewild.food.identity.domain.PlatformUser entity = row.get();
    UUID userId = entity.getId();
    if (userId == null) {
      return Optional.empty();
    }

    // Roles join — server-side only. Unknown codes are ignored (fail closed).
    List<String> codes;
    try {
      codes = userRoles.findRoleCodesByUserId(userId);
    } catch (Exception ex) {
      log.warn("Role lookup failed; failing closed");
      return Optional.empty();
    }
    Set<Role> roles = EnumSet.noneOf(Role.class);
    for (String code : codes) {
      Role r = Role.parseOrNull(code);
      if (r != null) {
        roles.add(r);
      }
    }
    if (roles.isEmpty()) {
      // Every provisioned user must hold at least CUSTOMER (sync trigger grants it).
      log.warn("Platform user has no recognised roles; failing closed");
      return Optional.empty();
    }

    PlatformUser.Builder builder = PlatformUser.builder(userId, sub)
        .phoneE164(entity.getPhone())
        .roles(roles);

    // Customer profile (optional).
    try {
      customers.findByUserId(userId).ifPresent(c -> {
        if (c.getId() != null) {
          builder.customerId(c.getId());
        }
      });
    } catch (Exception ex) {
      log.debug("Customer profile lookup failed (non-fatal)");
    }

    // Vendor profile + status gate fields (vendors table, Status enum as String).
    try {
      vendors.findByUserId(userId).ifPresent(v -> {
        if (v.getId() != null) {
          builder.vendorId(v.getId());
        }
        builder.vendorStatus(parseVendorStatus(v.getStatus()));
      });
    } catch (Exception ex) {
      log.debug("Vendor profile lookup failed (non-fatal)");
    }

    // Rider profile + status gate fields (riders table).
    try {
      riders.findByUserId(userId).ifPresent(r -> {
        if (r.getId() != null) {
          builder.riderId(r.getId());
        }
        builder.riderStatus(parseRiderStatus(r.getStatus()));
      });
    } catch (Exception ex) {
      log.debug("Rider profile lookup failed (non-fatal)");
    }

    return Optional.of(builder.build());
  }

  /**
   * Vendor lifecycle (DRAFT/SUBMITTED/UNDER_REVIEW/ACTION_REQUIRED/APPROVED/
   * SUSPENDED/REJECTED/DEACTIVATED) collapsed to the auth gate:
   * only APPROVED trades; SUSPENDED is distinct; everything else is NOT_APPROVED.
   */
  private static PlatformUser.VendorStatus parseVendorStatus(String raw) {
    if (raw == null) {
      return PlatformUser.VendorStatus.NONE;
    }
    return switch (raw.trim().toUpperCase()) {
      case "APPROVED" -> PlatformUser.VendorStatus.APPROVED;
      case "SUSPENDED" -> PlatformUser.VendorStatus.SUSPENDED;
      case "REJECTED", "DEACTIVATED" -> PlatformUser.VendorStatus.REJECTED;
      case "DRAFT", "SUBMITTED", "UNDER_REVIEW", "ACTION_REQUIRED", "PENDING" ->
          PlatformUser.VendorStatus.PENDING;
      default -> PlatformUser.VendorStatus.NONE;
    };
  }

  /**
   * Rider lifecycle (DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/ACTIVE/INACTIVE/
   * SUSPENDED/DEACTIVATED) collapsed to the delivery gate.
   */
  private static PlatformUser.RiderStatus parseRiderStatus(String raw) {
    if (raw == null) {
      return PlatformUser.RiderStatus.NONE;
    }
    return switch (raw.trim().toUpperCase()) {
      case "APPROVED", "ACTIVE" -> PlatformUser.RiderStatus.APPROVED;
      case "SUSPENDED" -> PlatformUser.RiderStatus.SUSPENDED;
      case "REJECTED", "DEACTIVATED" -> PlatformUser.RiderStatus.REJECTED;
      case "DRAFT", "SUBMITTED", "UNDER_REVIEW", "PENDING", "INACTIVE" ->
          PlatformUser.RiderStatus.PENDING;
      default -> PlatformUser.RiderStatus.NONE;
    };
  }
}
