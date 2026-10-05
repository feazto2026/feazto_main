package com.codewild.food.subscription.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_status_history")
public class SubscriptionStatusHistory {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "subscription_id", nullable = false)
  private UUID subscriptionId;
  @Column(name = "from_status") private String fromStatus;
  @Column(name = "to_status", nullable = false)
  private String toStatus;
  @Column(name = "changed_by") private UUID changedBy;
  @Column(name = "actor_role") private String actorRole;
  @Column(name = "change_reason") private String changeReason;
  @Column(name = "idempotency_key", unique = true)
  private String idempotencyKey;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected SubscriptionStatusHistory() {}
  public SubscriptionStatusHistory(UUID subId, String from, String to, UUID by, String role, String reason, String idem) {
    this.subscriptionId = subId; this.fromStatus = from; this.toStatus = to;
    this.changedBy = by; this.actorRole = role; this.changeReason = reason; this.idempotencyKey = idem;
  }
  public UUID getId() { return id; }
}
