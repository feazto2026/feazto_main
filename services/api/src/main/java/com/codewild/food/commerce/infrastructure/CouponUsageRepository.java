package com.codewild.food.commerce.infrastructure;

import com.codewild.food.commerce.domain.CouponUsage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, UUID> {
  List<CouponUsage> findByCustomerIdAndCouponId(UUID customerId, UUID couponId);
}
