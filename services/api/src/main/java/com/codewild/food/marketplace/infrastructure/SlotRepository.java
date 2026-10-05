package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.Slot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotRepository extends JpaRepository<Slot, UUID> {
  Optional<Slot> findByCode(String code);
  List<Slot> findByActiveTrue();
}
