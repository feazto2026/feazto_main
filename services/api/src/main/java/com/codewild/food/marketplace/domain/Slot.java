package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "slots",
  uniqueConstraints = @UniqueConstraint(name = "slots_code_key", columnNames = "code"))
public class Slot {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "code", nullable = false, unique = true)
  private String code;
  @Column(name = "name", nullable = false)
  private String name = "";
  @Column(name = "starts_at", nullable = false)
  private LocalTime startsAt;
  @Column(name = "ends_at", nullable = false)
  private LocalTime endsAt;
  @Column(name = "cutoff_minutes_before", nullable = false)
  private int cutoffMinutesBefore = 60;
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected Slot() {}
  public Slot(String code, String name, LocalTime startsAt, LocalTime endsAt) {
    this.code = code; this.name = name; this.startsAt = startsAt; this.endsAt = endsAt;
  }
  public UUID getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public LocalTime getStartsAt() { return startsAt; }
  public LocalTime getEndsAt() { return endsAt; }
  public boolean isActive() { return active; }
}
