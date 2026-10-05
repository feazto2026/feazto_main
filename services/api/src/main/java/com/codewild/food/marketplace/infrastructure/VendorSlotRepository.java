package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.VendorSlot;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface VendorSlotRepository extends JpaRepository<VendorSlot, UUID> {
  List<VendorSlot> findByVendorIdAndAvailableTrue(UUID vendorId);
  Optional<VendorSlot> findByVendorIdAndSlotId(UUID vendorId, UUID slotId);

  /** Slot capacity check: row-locked read for order placement (SELECT FOR UPDATE). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select vs from VendorSlot vs where vs.id = :id")
  Optional<VendorSlot> findByIdForUpdate(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select vs from VendorSlot vs where vs.vendorId = :vendorId and vs.slotId = :slotId")
  Optional<VendorSlot> findByVendorAndSlotForUpdate(UUID vendorId, UUID slotId);
}
