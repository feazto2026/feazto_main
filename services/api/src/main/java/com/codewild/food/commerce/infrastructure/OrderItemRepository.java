package com.codewild.food.commerce.infrastructure;

import com.codewild.food.commerce.domain.OrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
  List<OrderItem> findByOrderId(UUID orderId);
}
