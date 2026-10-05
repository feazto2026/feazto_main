package com.codewild.food.subscription.infrastructure;

import com.codewild.food.subscription.domain.MealPlan;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {
  List<MealPlan> findByVendorIdAndActiveTrue(UUID vendorId);
}
