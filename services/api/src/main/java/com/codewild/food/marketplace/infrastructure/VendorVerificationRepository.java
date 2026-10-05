package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.VendorVerification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorVerificationRepository extends JpaRepository<VendorVerification, UUID> {
  List<VendorVerification> findByVendorIdAndStatus(UUID vendorId, VendorVerification.Status status);
  Optional<VendorVerification> findByIdempotencyKey(String key);
}
