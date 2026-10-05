package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.Region;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, UUID> {
  Optional<Region> findByCode(String code);
}
