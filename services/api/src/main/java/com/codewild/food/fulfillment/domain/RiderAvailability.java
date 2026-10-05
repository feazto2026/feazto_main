package com.codewild.food.fulfillment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "rider_availability",
  uniqueConstraints = @UniqueConstraint(name = "rider_availability_rider_day_time_key",
    columnNames = {"rider_id","weekday","start_time","end_time"}))
public class RiderAvailability {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "rider_id", nullable = false)
  private UUID riderId;
  @Column(name = "weekday", nullable = false)
  private int weekday = 0;
  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;
  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;
  @Column(name = "is_available", nullable = false)
  private boolean available = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected RiderAvailability() {}
  public UUID getId() { return id; }
  public UUID getRiderId() { return riderId; }
  public int getWeekday() { return weekday; }
  public boolean isAvailable() { return available; }
}
