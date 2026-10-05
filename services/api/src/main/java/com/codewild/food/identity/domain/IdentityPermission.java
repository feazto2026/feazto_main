package com.codewild.food.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permissions")
public class IdentityPermission {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "code", nullable = false, unique = true)
  private String code;
  @Column(name = "module", nullable = false)
  private String module = "PLATFORM";
  @Column(name = "description", nullable = false)
  private String description = "";
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected IdentityPermission() {}
  public IdentityPermission(String code, String module) { this.code = code; this.module = module; }
  public UUID getId() { return id; }
  public String getCode() { return code; }
}
