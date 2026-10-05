package com.codewild.food.finance.infrastructure;

import com.codewild.food.finance.domain.Commission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionRepository extends JpaRepository<Commission, UUID> {
  Optional<Commission> findByOrderId(UUID orderId);
  List<Commission> findByVendorIdAndStatus(UUID vendorId, Commission.Status status);
}
