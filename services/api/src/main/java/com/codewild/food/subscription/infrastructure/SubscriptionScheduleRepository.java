package com.codewild.food.subscription.infrastructure;

import com.codewild.food.subscription.domain.SubscriptionSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionScheduleRepository extends JpaRepository<SubscriptionSchedule, UUID> {
  List<SubscriptionSchedule> findBySubscriptionIdAndActiveTrue(UUID subscriptionId);
}
