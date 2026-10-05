package com.codewild.food.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class Role {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "code", nullable = false, unique = true)
  private String code;
  @Column(name = "name", nullable = false)
  private String name;
  @Column(name = "description", nullable = false)
  private String description = "";
  @Column(name = "is_system", nullable = false)
  private boolean system = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected Role() {}
  public Role(String code, String name) { this.code = code; this.name = name; }
  public UUID getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
}
