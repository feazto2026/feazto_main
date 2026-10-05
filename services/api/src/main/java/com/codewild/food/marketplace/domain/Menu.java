package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "menus")
public class Menu {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vendor_id", insertable = false, updatable = false)
  private VendorProfile vendor;
  @Column(name = "name", nullable = false)
  private String name = "";
  @Column(name = "description", nullable = false)
  private String description = "";
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
  @Column(name = "published_at")
  private Instant publishedAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected Menu() {}
  public Menu(UUID vendorId, String name) { this.vendorId = vendorId; this.name = name; }
  public UUID getId() { return id; }
  public UUID getVendorId() { return vendorId; }
  public String getName() { return name; }
  public boolean isActive() { return active; }
  public void setActive(boolean a) { this.active = a; }
  public Instant getPublishedAt() { return publishedAt; }
  public void setPublishedAt(Instant v) { this.publishedAt = v; }
}
