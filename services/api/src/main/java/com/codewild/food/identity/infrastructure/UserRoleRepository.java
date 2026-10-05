package com.codewild.food.identity.infrastructure;

import com.codewild.food.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRole.Pk> {
  List<UserRole> findByPkUserId(UUID userId);

  /** Alias for services using findByUserId naming. */
  default List<UserRole> findByUserId(UUID userId) {
    return findByPkUserId(userId);
  }

  /** Canonical role codes for a platform user (server-side join, never from JWT). */
  @Query(value = "SELECT r.code FROM user_roles ur "
      + "JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = :userId",
      nativeQuery = true)
  List<String> findRoleCodesByUserId(@Param("userId") UUID userId);
}
