package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.VendorCuisine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorCuisineRepository extends JpaRepository<VendorCuisine, VendorCuisine.Pk> {
}
