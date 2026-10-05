package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vendor_regions")
public class VendorRegion {
  @EmbeddedId private Pk pk = new Pk();
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected VendorRegion() {}
  @Embeddable
  public static class Pk implements Serializable {
    @Column(name = "vendor_id") private UUID vendorId;
    @Column(name = "region_id") private UUID regionId;
    @Column(name = "kind") private String kind = "SPECIALTY";
    public Pk() {}
    @Override public boolean equals(Object o) {
      if (!(o instanceof Pk other)) return false;
      return Objects.equals(vendorId, other.vendorId) && Objects.equals(regionId, other.regionId) && Objects.equals(kind, other.kind);
    }
    @Override public int hashCode() { return Objects.hash(vendorId, regionId, kind); }
  }
}
