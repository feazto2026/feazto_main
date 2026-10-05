package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.MenuItemAvailability;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemAvailabilityRepository extends JpaRepository<MenuItemAvailability, UUID> {
  List<MenuItemAvailability> findByMenuItemIdAndServiceDate(UUID menuItemId, LocalDate serviceDate);
  List<MenuItemAvailability> findByMenuItemIdAndServiceDateIsNull(UUID menuItemId);
}
