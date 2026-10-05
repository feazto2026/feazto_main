package com.codewild.food.fulfillment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "delivery_status_history",
  indexes = @Index(name = "ix_delivery_history_delivery_created", columnList = "delivery_id,created_at"))
public class DeliveryStatusHistory {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "delivery_id", nullable = false)
  private UUID deliveryId;
  @Column(name = "from_status") private String fromStatus;
  @Column(name = "to_status", nullable = false)
  private String toStatus;
  @Column(name = "changed_by") private UUID changedBy;
  @Column(name = "actor_role") private String actorRole;
  @Column(name = "change_reason") private String changeReason;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "location_snapshot", nullable = false, columnDefinition = "jsonb")
  private String locationSnapshot = "{}";
  @Column(name = "idempotency_key", unique = true)
  private String idempotencyKey;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected DeliveryStatusHistory() {}
  public DeliveryStatusHistory(UUID deliveryId, String from, String to, UUID by, String role, String reason, String idem) {
    this.deliveryId = deliveryId; this.fromStatus = from; this.toStatus = to;
    this.changedBy = by; this.actorRole = role; this.changeReason = reason; this.idempotencyKey = idem;
  }
  public UUID getId() { return id; }
}
