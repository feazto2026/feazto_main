package com.codewild.food.subscription.infrastructure;

import com.codewild.food.subscription.domain.SubscriptionDailyOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionDailyOrderRepository extends JpaRepository<SubscriptionDailyOrder, UUID> {
  Optional<SubscriptionDailyOrder> findByIdempotencyKey(String key);
  Optional<SubscriptionDailyOrder> findBySubscriptionIdAndServiceDateAndMealSlotId(
    UUID subscriptionId, LocalDate serviceDate, UUID mealSlotId);
  List<SubscriptionDailyOrder> findByServiceDateAndStatus(LocalDate serviceDate, SubscriptionDailyOrder.Status status);
  List<SubscriptionDailyOrder> findBySubscriptionIdAndServiceDate(UUID subscriptionId, LocalDate serviceDate);
  List<SubscriptionDailyOrder> findBySubscriptionId(UUID subscriptionId);
}
