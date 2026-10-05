package com.codewild.food.finance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Supabase truth: public.refunds (0004) lives under commerce/finance. */
@Entity
@Table(name = "refunds",
  uniqueConstraints = {
    @UniqueConstraint(name = "refunds_idempotency_key_key", columnNames = "idempotency_key"),
    @UniqueConstraint(name = "refunds_provider_refund_id_key", columnNames = "provider_refund_id")
  })
public class Refund {
  public enum Status { REQUESTED, APPROVED, PROCESSING, COMPLETED, FAILED, REJECTED, CANCELLED }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "order_id", nullable = false)
  private UUID orderId;
  @Column(name = "payment_id", nullable = false)
  private UUID paymentId;
  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;
  @Column(name = "amount_paise", nullable = false)
  private long amountPaise = 0;
  @Column(name = "currency", nullable = false)
  private String currency = "INR";
  @Column(name = "reason", nullable = false)
  private String reason = "";
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.REQUESTED;
  @Column(name = "provider_refund_id", unique = true)
  private String providerRefundId;
  @Column(name = "requested_by") private UUID requestedBy;
  @Column(name = "approved_by") private UUID approvedBy;
  @Column(name = "processed_at") private Instant processedAt;
  @Column(name = "failure_reason") private String failureReason;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Refund() {}
  /** Back-compat rupees ctor. */
  public Refund(String paymentId, java.math.BigDecimal amount, String key) {
    try { this.paymentId = paymentId == null ? null : UUID.fromString(paymentId); } catch (Exception e) { this.paymentId = null; }
    this.amountPaise = amount == null ? 0 : amount.multiply(java.math.BigDecimal.valueOf(100)).longValue();
    this.idempotencyKey = key;
  }
  public Refund(UUID orderId, UUID paymentId, long amountPaise, String key, String reason) {
    this.orderId = orderId; this.paymentId = paymentId;
    this.amountPaise = amountPaise; this.idempotencyKey = key; this.reason = reason == null ? "" : reason;
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public Status getStatusEnum() { return status; }
  public String getStatus() { return status == null ? null : status.name(); }
  public void setStatus(String s) {
    try { this.status = s == null ? Status.REQUESTED : Status.valueOf(s); }
    catch (Exception e) { this.status = Status.REQUESTED; }
    this.updatedAt = Instant.now();
  }
  public void setStatusEnum(Status s) { this.status = s; this.updatedAt = Instant.now(); }
  public long getAmountPaise() { return amountPaise; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public UUID getOrderId() { return orderId; }
  public UUID getPaymentId() { return paymentId; }
  public String getProviderRefundId() { return providerRefundId; }
  public void setProviderRefundId(String v) { this.providerRefundId = v; }
  public void setApprovedBy(UUID v) { this.approvedBy = v; }
  public void setProcessedAt(Instant v) { this.processedAt = v; }
  public void setFailureReason(String v) { this.failureReason = v; }
}
