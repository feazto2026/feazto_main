package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cuisines")
public class Cuisine {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "code", nullable = false, unique = true)
  private String code;
  @Column(name = "name", nullable = false)
  private String name;
  @Column(name = "category", nullable = false)
  private String category = "REGIONAL";
  @Column(name = "description", nullable = false)
  private String description = "";
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected Cuisine() {}
  public Cuisine(String code, String name) { this.code = code; this.name = name; }
  public UUID getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
}
