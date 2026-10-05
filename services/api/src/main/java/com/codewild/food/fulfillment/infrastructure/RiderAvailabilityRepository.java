package com.codewild.food.fulfillment.infrastructure;

import com.codewild.food.fulfillment.domain.RiderAvailability;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiderAvailabilityRepository extends JpaRepository<RiderAvailability, UUID> {
  List<RiderAvailability> findByRiderIdAndWeekdayAndAvailableTrue(UUID riderId, int weekday);
  List<RiderAvailability> findByRiderId(UUID riderId);
}
