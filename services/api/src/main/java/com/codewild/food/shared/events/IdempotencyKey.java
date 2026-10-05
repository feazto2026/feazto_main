package com.codewild.food.shared.events;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Supabase truth: public.idempotency_keys (0007). Durable ledger UNIQUE(scope,key). */
@Entity
@Table(name = "idempotency_keys",
  uniqueConstraints = @UniqueConstraint(name = "idempotency_keys_scope_key", columnNames = {"scope","key"}))
public class IdempotencyKey {
  public enum Status { IN_PROGRESS, COMPLETED, FAILED }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "scope", nullable = false)
  private String scope = "";
  @Column(name = "key", nullable = false)
  private String key = "";
  @Column(name = "user_id")
  private UUID userId;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.IN_PROGRESS;
  @Column(name = "response_code")
  private Integer responseCode;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_body", nullable = false, columnDefinition = "jsonb")
  private String responseBody = "{}";
  @Column(name = "locked_at", nullable = false)
  private Instant lockedAt = Instant.now();
  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt = Instant.now().plusSeconds(86400);
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected IdempotencyKey() {}
  public IdempotencyKey(String scope, String key, UUID userId) {
    this.scope = scope; this.key = key; this.userId = userId;
  }
  public UUID getId() { return id; }
  public Status getStatus() { return status; }
  public void setStatus(Status s) { this.status = s; this.updatedAt = Instant.now(); }
  public String getResponseBody() { return responseBody; }
  public void setResponseBody(String v) { this.responseBody = v == null ? "{}" : v; }
  public void setResponseCode(Integer v) { this.responseCode = v; }
  public Instant getExpiresAt() { return expiresAt; }
}
