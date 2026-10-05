package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vendor_service_zones")
public class VendorServiceZone {
  @EmbeddedId private Pk pk = new Pk();
  @Column(name = "custom_delivery_fee_paise")
  private Long customDeliveryFeePaise;
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected VendorServiceZone() {}
  public VendorServiceZone(UUID vendorId, UUID zoneId) { this.pk = new Pk(vendorId, zoneId); }
  public UUID getVendorId() { return pk.vendorId; }
  public UUID getServiceZoneId() { return pk.serviceZoneId; }
  public Long getCustomDeliveryFeePaise() { return customDeliveryFeePaise; }
  public void setCustomDeliveryFeePaise(Long v) { this.customDeliveryFeePaise = v; }
  public boolean isActive() { return active; }
  @Embeddable
  public static class Pk implements Serializable {
    @Column(name = "vendor_id") private UUID vendorId;
    @Column(name = "service_zone_id") private UUID serviceZoneId;
    public Pk() {}
    public Pk(UUID v, UUID z) { this.vendorId = v; this.serviceZoneId = z; }
    @Override public boolean equals(Object o) {
      if (!(o instanceof Pk other)) return false;
      return Objects.equals(vendorId, other.vendorId) && Objects.equals(serviceZoneId, other.serviceZoneId);
    }
    @Override public int hashCode() { return Objects.hash(vendorId, serviceZoneId); }
  }
}
