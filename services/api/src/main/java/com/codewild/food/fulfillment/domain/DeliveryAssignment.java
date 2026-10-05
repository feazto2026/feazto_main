package com.codewild.food.fulfillment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "delivery_assignments",
  uniqueConstraints = {
    @UniqueConstraint(name = "delivery_assignments_idem_key", columnNames = "idempotency_key"),
    @UniqueConstraint(name = "delivery_assignments_delivery_rider_attempt_key",
      columnNames = {"delivery_id","rider_id","attempt_no"})
  })
public class DeliveryAssignment {
  public enum Status { OFFERED, ACCEPTED, REJECTED, EXPIRED, TIMEOUT, CANCELLED, REASSIGNED }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "delivery_id", nullable = false)
  private UUID deliveryId;
  @Column(name = "rider_id", nullable = false)
  private UUID riderId;
  @Column(name = "attempt_no", nullable = false)
  private int attemptNo = 1;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.OFFERED;
  @Column(name = "offered_at", nullable = false)
  private Instant offeredAt = Instant.now();
  @Column(name = "responded_at") private Instant respondedAt;
  @Column(name = "expires_at") private Instant expiresAt;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "assignment_reason", nullable = false, columnDefinition = "jsonb")
  private String assignmentReason = "{}";
  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey = "asg:" + UUID.randomUUID();
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected DeliveryAssignment() {}
  public DeliveryAssignment(UUID deliveryId, UUID riderId, int attemptNo, String idemKey, String reasonJson) {
    this.deliveryId = deliveryId; this.riderId = riderId;
    this.attemptNo = attemptNo; this.idempotencyKey = idemKey;
    this.assignmentReason = reasonJson == null ? "{}" : reasonJson;
    this.expiresAt = Instant.now().plusSeconds(120);
  }
  public UUID getId() { return id; }
  public UUID getDeliveryId() { return deliveryId; }
  public UUID getRiderId() { return riderId; }
  public Status getStatus() { return status; }
  public void setStatus(Status s) { this.status = s; this.updatedAt = Instant.now(); if (s != Status.OFFERED) this.respondedAt = Instant.now(); }
  public int getAttemptNo() { return attemptNo; }
  public String getIdempotencyKey() { return idempotencyKey; }
}
