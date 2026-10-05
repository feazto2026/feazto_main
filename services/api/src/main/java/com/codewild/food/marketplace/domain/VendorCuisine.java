package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vendor_cuisines")
public class VendorCuisine {
  @EmbeddedId private Pk pk = new Pk();
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected VendorCuisine() {}
  @Embeddable
  public static class Pk implements Serializable {
    @Column(name = "vendor_id") private UUID vendorId;
    @Column(name = "cuisine_id") private UUID cuisineId;
    public Pk() {}
    @Override public boolean equals(Object o) {
      if (!(o instanceof Pk other)) return false;
      return Objects.equals(vendorId, other.vendorId) && Objects.equals(cuisineId, other.cuisineId);
    }
    @Override public int hashCode() { return Objects.hash(vendorId, cuisineId); }
  }
}
