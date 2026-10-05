package com.codewild.food.fulfillment.infrastructure;

import com.codewild.food.fulfillment.domain.DeliveryAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {
  List<DeliveryAssignment> findByDeliveryIdAndStatus(UUID deliveryId, DeliveryAssignment.Status status);
  List<DeliveryAssignment> findByDeliveryIdOrderByAttemptNoDesc(UUID deliveryId);
  List<DeliveryAssignment> findByRiderIdAndStatus(UUID riderId, DeliveryAssignment.Status status);
  Optional<DeliveryAssignment> findByIdempotencyKey(String key);
}
