package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.VendorServiceZone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorServiceZoneRepository extends JpaRepository<VendorServiceZone, VendorServiceZone.Pk> {
  List<VendorServiceZone> findByPkVendorIdAndActiveTrue(UUID vendorId);
  List<VendorServiceZone> findByPkServiceZoneIdAndActiveTrue(UUID zoneId);
}
