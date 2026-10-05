package com.codewild.food.identity.infrastructure;

import com.codewild.food.identity.domain.PlatformUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {
  Optional<PlatformUser> findByPhone(String phone);
  Optional<PlatformUser> findByEmail(String email);
  Optional<PlatformUser> findByAuthUserId(UUID authUserId);

  /** String-sub overload: parses the JWT sub to UUID, empty on malformed (fail closed). */
  default Optional<PlatformUser> findByAuthUserIdString(String sub) {
    try {
      return sub == null ? Optional.empty() : findByAuthUserId(UUID.fromString(sub.trim()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Fail-closed active lookup: returns the row only when
   * {@code account_status = 'ACTIVE'} and not soft-deleted.
   * Used on EVERY authenticated request by {@code SupabasePlatformUserService}.
   */
  @Query("SELECT u FROM PlatformUser u WHERE u.authUserId = :sub "
      + "AND u.accountStatus = com.codewild.food.identity.domain.PlatformUser$AccountStatus.ACTIVE "
      + "AND u.deletedAt IS NULL")
  Optional<PlatformUser> findActiveByAuthUserId(@Param("sub") UUID supabaseSub);

  default Optional<PlatformUser> findActiveByAuthUserIdString(String sub) {
    try {
      return sub == null ? Optional.empty() : findActiveByAuthUserId(UUID.fromString(sub.trim()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Server-side roles join via {@code user_roles -> roles(code)}.
   * Never from JWT claims. Used by {@code SupabasePlatformUserService} + {@code AuthService}.
   */
  @Query(value = "SELECT r.code FROM user_roles ur "
      + "JOIN roles r ON r.id = ur.role_id "
      + "WHERE ur.user_id = :userId", nativeQuery = true)
  List<String> findRoleCodesByUserId(@Param("userId") UUID userId);

  /** Active-status check without loading the full row (for audit paths). */
  @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM PlatformUser u "
      + "WHERE u.authUserId = :sub "
      + "AND u.accountStatus = com.codewild.food.identity.domain.PlatformUser$AccountStatus.ACTIVE "
      + "AND u.deletedAt IS NULL")
  boolean isActiveByAuthUserId(@Param("sub") UUID supabaseSub);
}
