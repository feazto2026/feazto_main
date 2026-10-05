package com.codewild.food.finance.infrastructure;

import com.codewild.food.finance.domain.RiderPayout;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiderPayoutRepository extends JpaRepository<RiderPayout, UUID> {
  Optional<RiderPayout> findByIdempotencyKey(String key);
  List<RiderPayout> findByRiderIdAndStatus(UUID riderId, RiderPayout.Status status);
}
