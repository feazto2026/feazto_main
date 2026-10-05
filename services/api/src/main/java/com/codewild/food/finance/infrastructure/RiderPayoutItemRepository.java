package com.codewild.food.finance.infrastructure;

import com.codewild.food.finance.domain.RiderPayoutItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiderPayoutItemRepository extends JpaRepository<RiderPayoutItem, UUID> {
  List<RiderPayoutItem> findByPayoutId(UUID payoutId);
}
