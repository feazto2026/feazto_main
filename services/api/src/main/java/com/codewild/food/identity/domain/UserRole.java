package com.codewild.food.identity.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
public class UserRole {
  @EmbeddedId private Pk pk = new Pk();
  @Column(name = "granted_by") private UUID grantedBy;
  @Column(name = "granted_at", nullable = false)
  private Instant grantedAt = Instant.now();
  protected UserRole() {}
  public UserRole(UUID userId, UUID roleId) {
    this.pk = new Pk(userId, roleId);
  }
  public UUID getUserId() { return pk.userId; }
  public UUID getRoleId() { return pk.roleId; }
  public Pk getPk() { return pk; }
  public UUID getGrantedBy() { return grantedBy; }
  public void setGrantedBy(UUID v) { this.grantedBy = v; }
  public Instant getGrantedAt() { return grantedAt; }
  @Embeddable
  public static class Pk implements Serializable {
    @Column(name = "user_id") private UUID userId;
    @Column(name = "role_id") private UUID roleId;
    public Pk() {}
    public Pk(UUID u, UUID r) { this.userId = u; this.roleId = r; }
    @Override public boolean equals(Object o) {
      if (!(o instanceof Pk other)) return false;
      return Objects.equals(userId, other.userId) && Objects.equals(roleId, other.roleId);
    }
    @Override public int hashCode() { return Objects.hash(userId, roleId); }
  }
}
