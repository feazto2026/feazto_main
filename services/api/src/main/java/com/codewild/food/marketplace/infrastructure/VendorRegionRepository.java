package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.VendorRegion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRegionRepository extends JpaRepository<VendorRegion, VendorRegion.Pk> {
}
