package com.codewild.food.subscription.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Supabase truth: public.subscription_daily_orders (0005).
 * UNIQUE(subscription_id, service_date, meal_slot_id) + idempotency_key UNIQUE.
 */
@Entity
@Table(name = "subscription_daily_orders",
  uniqueConstraints = {
    @UniqueConstraint(name = "subscription_daily_orders_triplet_key",
      columnNames = {"subscription_id","service_date","meal_slot_id"}),
    @UniqueConstraint(name = "subscription_daily_orders_idem_key", columnNames = "idempotency_key"),
    @UniqueConstraint(name = "subscription_daily_orders_order_key", columnNames = "order_id")
  },
  indexes = {
    @Index(name = "ix_daily_orders_due", columnList = "service_date,status"),
    @Index(name = "ix_daily_orders_subscription", columnList = "subscription_id,service_date")
  })
public class SubscriptionDailyOrder {
  public enum Status { SCHEDULED, GENERATED, SKIPPED, FAILED, CANCELLED }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "subscription_id", nullable = false)
  private UUID subscriptionId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subscription_id", insertable = false, updatable = false)
  private Subscription subscription;
  @Column(name = "order_id", unique = true)
  private UUID orderId;
  @Column(name = "service_date", nullable = false)
  private LocalDate serviceDate;
  @Column(name = "meal_slot_id", nullable = false)
  private UUID mealSlotId;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.SCHEDULED;
  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;
  @Column(name = "failure_reason")
  private String failureReason;
  @Column(name = "generated_at")
  private Instant generatedAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected SubscriptionDailyOrder() {}
  public SubscriptionDailyOrder(UUID subscriptionId, LocalDate serviceDate, UUID mealSlotId, String idemKey) {
    this.subscriptionId = subscriptionId; this.serviceDate = serviceDate;
    this.mealSlotId = mealSlotId; this.idempotencyKey = idemKey;
  }
  public UUID getId() { return id; }
  public UUID getSubscriptionId() { return subscriptionId; }
  public UUID getOrderId() { return orderId; }
  public void setOrderId(UUID v) { this.orderId = v; }
  public LocalDate getServiceDate() { return serviceDate; }
  public UUID getMealSlotId() { return mealSlotId; }
  public Status getStatus() { return status; }
  public void setStatus(Status s) { this.status = s; this.updatedAt = Instant.now(); }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setFailureReason(String v) { this.failureReason = v; }
  public void setGeneratedAt(Instant v) { this.generatedAt = v; }
}
