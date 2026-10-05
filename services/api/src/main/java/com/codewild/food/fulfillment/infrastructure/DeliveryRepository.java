package com.codewild.food.fulfillment.infrastructure;

import com.codewild.food.fulfillment.domain.Delivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
  Optional<Delivery> findByOrderId(UUID orderId);
  default Optional<Delivery> findByOrderId(String orderId) {
    try { return findByOrderId(UUID.fromString(orderId)); }
    catch (Exception e) { return Optional.empty(); }
  }
  List<Delivery> findByRiderId(UUID riderId);
  default List<Delivery> findByRiderId(String riderId) {
    try { return findByRiderId(UUID.fromString(riderId)); }
    catch (Exception e) { return List.of(); }
  }
  List<Delivery> findByStatus(com.codewild.food.fulfillment.domain.DeliveryStateMachine.State status);
  Optional<Delivery> findByIdempotencyKey(String key);
}
