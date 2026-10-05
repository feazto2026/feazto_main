package com.codewild.food.commerce.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Supabase truth: public.order_status_history (0004). Append-only timeline. */
@Entity
@Table(name = "order_status_history",
  indexes = @Index(name = "ix_order_history_order_created", columnList = "order_id,created_at"))
public class OrderStatusHistory {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "order_id", nullable = false)
  private UUID orderId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", insertable = false, updatable = false)
  private OrderEntity order;
  @Column(name = "from_status")
  private String fromStatus;
  @Column(name = "to_status", nullable = false)
  private String toStatus;
  @Column(name = "changed_by")
  private UUID changedBy;
  @Column(name = "actor_role")
  private String actorRole;
  @Column(name = "change_reason")
  private String changeReason;
  @Column(name = "idempotency_key", unique = true)
  private String idempotencyKey;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected OrderStatusHistory() {}
  public OrderStatusHistory(UUID orderId, String from, String to, UUID changedBy, String role, String reason, String idemKey) {
    this.orderId = orderId; this.fromStatus = from; this.toStatus = to;
    this.changedBy = changedBy; this.actorRole = role; this.changeReason = reason; this.idempotencyKey = idemKey;
  }
  public UUID getId() { return id; }
  public UUID getOrderId() { return orderId; }
  public String getToStatus() { return toStatus; }
  public Instant getCreatedAt() { return createdAt; }
}
