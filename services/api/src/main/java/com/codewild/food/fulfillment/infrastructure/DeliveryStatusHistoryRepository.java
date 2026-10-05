package com.codewild.food.fulfillment.infrastructure;

import com.codewild.food.fulfillment.domain.DeliveryStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryStatusHistoryRepository extends JpaRepository<DeliveryStatusHistory, UUID> {
  List<DeliveryStatusHistory> findByDeliveryIdOrderByCreatedAtAsc(UUID deliveryId);
}
