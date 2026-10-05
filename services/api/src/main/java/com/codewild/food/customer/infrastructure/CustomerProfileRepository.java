package com.codewild.food.customer.infrastructure;

import com.codewild.food.customer.domain.CustomerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
  Optional<CustomerProfile> findByUserId(UUID userId);
  default Optional<CustomerProfile> findByUserId(String userId) {
    try { return findByUserId(UUID.fromString(userId)); }
    catch (Exception e) { return Optional.empty(); }
  }
}
