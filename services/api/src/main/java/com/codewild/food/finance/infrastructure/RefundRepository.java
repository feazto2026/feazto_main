package com.codewild.food.finance.infrastructure;

import com.codewild.food.finance.domain.Refund;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
  Optional<Refund> findByIdempotencyKey(String key);
  List<Refund> findByOrderId(UUID orderId);
  List<Refund> findByStatus(Refund.Status status);
}
