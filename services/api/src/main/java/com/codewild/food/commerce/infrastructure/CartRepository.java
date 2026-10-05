package com.codewild.food.commerce.infrastructure;

import com.codewild.food.commerce.domain.Cart;
import com.codewild.food.commerce.domain.CartItem;
import com.codewild.food.commerce.domain.Coupon;
import com.codewild.food.commerce.domain.CouponUsage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, UUID> {
  List<Cart> findByCustomerIdAndStatus(UUID customerId, Cart.Status status);
  Optional<Cart> findByCustomerIdAndVendorIdAndStatus(UUID customerId, UUID vendorId, Cart.Status status);
}
