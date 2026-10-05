package com.codewild.food.subscription.infrastructure;

import com.codewild.food.subscription.domain.SubscriptionStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionStatusHistoryRepository extends JpaRepository<SubscriptionStatusHistory, UUID> {
  List<SubscriptionStatusHistory> findBySubscriptionIdOrderByCreatedAtAsc(UUID subscriptionId);
}
