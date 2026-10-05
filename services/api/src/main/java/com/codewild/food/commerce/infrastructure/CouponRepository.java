package com.codewild.food.commerce.infrastructure;

import com.codewild.food.commerce.domain.Coupon;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {
  Optional<Coupon> findByCode(String code);
}
