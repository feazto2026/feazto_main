package com.codewild.food.subscription.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_schedules",
  uniqueConstraints = @UniqueConstraint(name = "subscription_schedules_sub_day_slot_key",
    columnNames = {"subscription_id","weekday","meal_slot_id"}))
public class SubscriptionSchedule {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "subscription_id", nullable = false)
  private UUID subscriptionId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subscription_id", insertable = false, updatable = false)
  private Subscription subscription;
  @Column(name = "weekday", nullable = false)
  private int weekday = 0;
  @Column(name = "meal_slot_id", nullable = false)
  private UUID mealSlotId;
  @Column(name = "quantity", nullable = false)
  private int quantity = 1;
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected SubscriptionSchedule() {}
  public SubscriptionSchedule(UUID subscriptionId, int weekday, UUID mealSlotId, int quantity) {
    this.subscriptionId = subscriptionId; this.weekday = weekday;
    this.mealSlotId = mealSlotId; this.quantity = quantity;
  }
  public UUID getId() { return id; }
  public UUID getSubscriptionId() { return subscriptionId; }
  public int getWeekday() { return weekday; }
  public UUID getMealSlotId() { return mealSlotId; }
  public int getQuantity() { return quantity; }
  public boolean isActive() { return active; }
  public void setActive(boolean a) { this.active = a; }
}
