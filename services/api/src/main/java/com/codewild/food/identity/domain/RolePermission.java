package com.codewild.food.identity.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "role_permissions")
public class RolePermission {
  @EmbeddedId private Pk pk = new Pk();
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected RolePermission() {}
  @Embeddable
  public static class Pk implements Serializable {
    @Column(name = "role_id") private UUID roleId;
    @Column(name = "permission_id") private UUID permissionId;
    public Pk() {}
    public Pk(UUID r, UUID p) { this.roleId = r; this.permissionId = p; }
    @Override public boolean equals(Object o) {
      if (!(o instanceof Pk other)) return false;
      return Objects.equals(roleId, other.roleId) && Objects.equals(permissionId, other.permissionId);
    }
    @Override public int hashCode() { return Objects.hash(roleId, permissionId); }
  }
}
