package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.MenuCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {
  List<MenuCategory> findByMenuIdOrderBySortOrderAsc(UUID menuId);
}
