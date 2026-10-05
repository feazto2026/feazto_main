package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "regions")
public class Region {
  public enum Kind { STATE, DISTRICT, CITY, CULTURAL_REGION }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "code", nullable = false, unique = true)
  private String code;
  @Column(name = "name", nullable = false)
  private String name;
  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false)
  private Kind kind = Kind.CITY;
  @Column(name = "state") private String state;
  @Column(name = "district") private String district;
  @Column(name = "city") private String city;
  @Column(name = "parent_id") private UUID parentId;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected Region() {}
  public Region(String code, String name, Kind kind) { this.code = code; this.name = name; this.kind = kind; }
  public UUID getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public Kind getKind() { return kind; }
}
