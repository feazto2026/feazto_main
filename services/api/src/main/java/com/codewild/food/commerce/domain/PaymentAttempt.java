package com.codewild.food.commerce.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_attempts",
  uniqueConstraints = {
    @UniqueConstraint(name = "payment_attempts_idempotency_key_key", columnNames = "idempotency_key"),
    @UniqueConstraint(name = "payment_attempts_payment_no_key", columnNames = {"payment_id","attempt_no"})
  })
public class PaymentAttempt {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "payment_id", nullable = false)
  private UUID paymentId;
  @Column(name = "attempt_no", nullable = false)
  private int attemptNo = 1;
  @Column(name = "provider_reference")
  private String providerReference;
  @Column(name = "status", nullable = false)
  private String status = "INITIATED";
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
  private String requestPayload = "{}";
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_payload", nullable = false, columnDefinition = "jsonb")
  private String responsePayload = "{}";
  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;
  @Column(name = "error_code")
  private String errorCode;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected PaymentAttempt() {}
  public PaymentAttempt(UUID paymentId, int attemptNo, String idemKey) {
    this.paymentId = paymentId; this.attemptNo = attemptNo; this.idempotencyKey = idemKey;
  }
  public UUID getId() { return id; }
  public String getStatus() { return status; }
  public void setStatus(String s) { this.status = s; }
}
