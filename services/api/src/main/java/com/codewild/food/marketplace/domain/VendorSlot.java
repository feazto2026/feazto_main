package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Supabase truth: public.vendor_slots. DB is authoritative for capacity;
 * Redis holds reservation locks only. Row-lock via SELECT FOR UPDATE in repo.
 */
@Entity
@Table(name = "vendor_slots",
  uniqueConstraints = @UniqueConstraint(name = "vendor_slots_vendor_slot_key", columnNames = {"vendor_id","slot_id"}))
public class VendorSlot {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vendor_id", insertable = false, updatable = false)
  private VendorProfile vendor;
  @Column(name = "slot_id", nullable = false)
  private UUID slotId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "slot_id", insertable = false, updatable = false)
  private Slot slot;
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "service_days", nullable = false, columnDefinition = "integer[]")
  private Integer[] serviceDays = new Integer[]{0,1,2,3,4,5,6};
  @Column(name = "capacity_total", nullable = false)
  private int capacityTotal = 20;
  @Column(name = "capacity_reserved", nullable = false)
  private int capacityReserved = 0;
  @Column(name = "is_available", nullable = false)
  private boolean available = true;
  @Column(name = "effective_from")
  private LocalDate effectiveFrom;
  @Column(name = "effective_to")
  private LocalDate effectiveTo;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected VendorSlot() {}
  public VendorSlot(UUID vendorId, UUID slotId, int capacityTotal) {
    this.vendorId = vendorId; this.slotId = slotId; this.capacityTotal = capacityTotal;
  }
  public UUID getId() { return id; }
  public UUID getVendorId() { return vendorId; }
  public UUID getSlotId() { return slotId; }
  public int getCapacityTotal() { return capacityTotal; }
  public void setCapacityTotal(int v) { this.capacityTotal = v; }
  public int getCapacityReserved() { return capacityReserved; }
  public void setCapacityReserved(int v) { this.capacityReserved = v; }
  public int remaining() { return capacityTotal - capacityReserved; }
  public boolean isAvailable() { return available; }
  public void setAvailable(boolean a) { this.available = a; }
}
