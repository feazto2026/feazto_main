package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.Menu;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, UUID> {
  List<Menu> findByVendorIdAndActiveTrue(UUID vendorId);
}
