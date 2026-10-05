package com.codewild.food.commerce.infrastructure;

import com.codewild.food.commerce.domain.CartItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
  List<CartItem> findByCartId(UUID cartId);
}
