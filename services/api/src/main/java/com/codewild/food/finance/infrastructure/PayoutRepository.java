package com.codewild.food.finance.infrastructure;

import com.codewild.food.finance.domain.VendorPayout;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<VendorPayout, UUID> {
  Optional<VendorPayout> findByIdempotencyKey(String key);
}
