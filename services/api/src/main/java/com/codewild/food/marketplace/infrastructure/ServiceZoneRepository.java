package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.ServiceZone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceZoneRepository extends JpaRepository<ServiceZone, UUID> {
  List<ServiceZone> findByCityAndActiveTrue(String city);
  List<ServiceZone> findByActiveTrue();
}
