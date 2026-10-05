package com.codewild.food.shared.events;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Supabase truth: public.outbox_events (0007). Transactional relay:
 * status/attempts/next_attempt_at/idempotency_key, published_at, error.
 */
@Entity
@Table(name = "outbox_events",
  uniqueConstraints = @UniqueConstraint(name = "outbox_events_idem_key", columnNames = "idempotency_key"),
  indexes = {
    @Index(name = "ix_outbox_pending", columnList = "next_attempt_at"),
    @Index(name = "ix_outbox_aggregate", columnList = "aggregate_type,aggregate_id,created_at")
  })
public class OutboxEvent {
  public enum Status { PENDING, PUBLISHED, FAILED, DEAD_LETTER }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType = "";

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType = "";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payloadJson = "{}";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "headers", nullable = false, columnDefinition = "jsonb")
  private String headersJson = "{}";

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.PENDING;

  @Column(name = "attempts", nullable = false)
  private int attempts = 0;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt = Instant.now();

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "error")
  private String error;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey = "obx:" + UUID.randomUUID();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  // Back-compat alias: legacy column "type" maps to event_type.
  @Transient
  private String typeAlias;

  protected OutboxEvent() {}
  public OutboxEvent(String type, String aggregateType, String aggregateId, String payloadJson) {
    this.eventType = type == null ? "" : type;
    this.typeAlias = this.eventType;
    this.aggregateType = aggregateType == null ? "" : aggregateType;
    try { this.aggregateId = aggregateId == null ? null : UUID.fromString(aggregateId); }
    catch (Exception e) { this.aggregateId = null; }
    this.payloadJson = payloadJson == null ? "{}" : payloadJson;
    this.idempotencyKey = "obx:" + UUID.randomUUID();
  }
  public OutboxEvent(String eventType, String aggregateType, UUID aggregateId, String payloadJson, String idemKey) {
    this.eventType = eventType; this.aggregateType = aggregateType;
    this.aggregateId = aggregateId; this.payloadJson = payloadJson == null ? "{}" : payloadJson;
    this.idempotencyKey = idemKey == null ? "obx:" + UUID.randomUUID() : idemKey;
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public String getType() { return eventType; }
  public String getEventType() { return eventType; }
  public String getAggregateType() { return aggregateType; }
  public String getAggregateId() { return aggregateId == null ? null : aggregateId.toString(); }
  public UUID getAggregateUuid() { return aggregateId; }
  public String getPayloadJson() { return payloadJson; }
  public Status getStatus() { return status; }
  public boolean isPublished() { return status == Status.PUBLISHED; }
  public void markPublished() {
    this.status = Status.PUBLISHED; this.publishedAt = Instant.now(); this.updatedAt = Instant.now();
  }
  public void markFailed(String err) {
    this.attempts++;
    this.error = err;
    this.status = attempts >= 10 ? Status.DEAD_LETTER : Status.FAILED;
    this.nextAttemptAt = Instant.now().plusSeconds((long) Math.min(3600, Math.pow(2, Math.min(attempts, 10)) * 15));
    this.updatedAt = Instant.now();
  }
  public void retryLater(String err) {
    this.attempts++;
    this.error = err;
    this.status = Status.PENDING;
    this.nextAttemptAt = Instant.now().plusSeconds((long) Math.min(3600, Math.pow(2, Math.min(attempts, 10)) * 15));
    this.updatedAt = Instant.now();
  }
  public int getAttempts() { return attempts; }
  public Instant getNextAttemptAt() { return nextAttemptAt; }
  public String getIdempotencyKey() { return idempotencyKey; }
}
