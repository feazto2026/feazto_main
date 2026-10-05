package com.codewild.food.finance.infrastructure;

import com.codewild.food.finance.domain.VendorPayout;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPayoutRepository extends JpaRepository<VendorPayout, UUID> {
  Optional<VendorPayout> findByIdempotencyKey(String key);
  List<VendorPayout> findByVendorIdAndStatus(UUID vendorId, VendorPayout.Status status);
  List<VendorPayout> findByVendorId(UUID vendorId);
}
