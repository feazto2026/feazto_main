package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "menu_item_availability",
  uniqueConstraints = @UniqueConstraint(name = "menu_item_availability_item_slot_date_key",
    columnNames = {"menu_item_id","vendor_slot_id","service_date"}))
public class MenuItemAvailability {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "menu_item_id", nullable = false)
  private UUID menuItemId;
  @Column(name = "vendor_slot_id")
  private UUID vendorSlotId;
  @Column(name = "service_date")
  private LocalDate serviceDate;
  @Column(name = "quantity_available", nullable = false)
  private int quantityAvailable = 0;
  @Column(name = "quantity_reserved", nullable = false)
  private int quantityReserved = 0;
  @Column(name = "is_available", nullable = false)
  private boolean available = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected MenuItemAvailability() {}
  public UUID getId() { return id; }
  public UUID getMenuItemId() { return menuItemId; }
  public int getQuantityAvailable() { return quantityAvailable; }
  public int getQuantityReserved() { return quantityReserved; }
  public void setQuantityReserved(int v) { this.quantityReserved = v; }
  public int remaining() { return quantityAvailable - quantityReserved; }
  public boolean isAvailable() { return available; }
}
