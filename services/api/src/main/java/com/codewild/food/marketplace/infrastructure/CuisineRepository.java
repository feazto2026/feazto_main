package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.Cuisine;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuisineRepository extends JpaRepository<Cuisine, UUID> {
  Optional<Cuisine> findByCode(String code);
}
