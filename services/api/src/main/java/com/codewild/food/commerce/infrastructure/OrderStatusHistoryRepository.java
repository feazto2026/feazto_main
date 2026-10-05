package com.codewild.food.commerce.infrastructure;

import com.codewild.food.commerce.domain.OrderStatusHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
  /** Timeline: history ordered (append-only). */
  List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
  Optional<OrderStatusHistory> findByIdempotencyKey(String key);
}
