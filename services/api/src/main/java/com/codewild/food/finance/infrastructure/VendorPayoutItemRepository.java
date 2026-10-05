package com.codewild.food.finance.infrastructure;

import com.codewild.food.finance.domain.VendorPayoutItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPayoutItemRepository extends JpaRepository<VendorPayoutItem, UUID> {
  List<VendorPayoutItem> findByPayoutId(UUID payoutId);
}
