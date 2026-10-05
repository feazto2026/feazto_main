package com.codewild.food.commerce.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Supabase truth: public.payments (0004). Webhook idempotent on
 * provider_payment_id UNIQUE + idempotency_key NOT NULL UNIQUE.
 */
@Entity
@Table(name = "payments",
  uniqueConstraints = {
    @UniqueConstraint(name = "payments_provider_payment_id_key", columnNames = "provider_payment_id"),
    @UniqueConstraint(name = "payments_idempotency_key_key", columnNames = "idempotency_key")
  },
  indexes = {
    @Index(name = "ix_payments_order", columnList = "order_id"),
    @Index(name = "ix_payments_provider_ref", columnList = "provider,provider_payment_id"),
    @Index(name = "ix_payments_status", columnList = "status")
  })
public class PaymentEntity {
  public enum Provider { RAZORPAY, CASHFREE, STRIPE, UPI, COD, INTERNAL }
  public enum Method { UPI, CARD, NETBANKING, WALLET, COD, BANK_TRANSFER }
  public enum Status {
    CREATED, INITIATED, PENDING, SUCCESS, FAILED,
    CANCELLED, REFUND_PENDING, PARTIALLY_REFUNDED, REFUNDED
  }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id")
  private UUID orderId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", insertable = false, updatable = false)
  private OrderEntity order;

  @Column(name = "subscription_id")
  private UUID subscriptionId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private Provider provider = Provider.INTERNAL;

  @Column(name = "provider_payment_id", unique = true)
  private String providerPaymentId;

  @Column(name = "provider_order_id")
  private String providerOrderId;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "amount_paise", nullable = false)
  private long amountPaise = 0;

  @Column(name = "currency", nullable = false)
  private String currency = "INR";

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false)
  private Method method = Method.UPI;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.CREATED;

  @Column(name = "verified_at")
  private Instant verifiedAt;
  @Column(name = "failure_code")
  private String failureCode;
  @Column(name = "failure_message")
  private String failureMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "webhook_payload", nullable = false, columnDefinition = "jsonb")
  private String webhookPayload = "{}";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private String metadataJson = "{}";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected PaymentEntity() {}
  public PaymentEntity(String orderId, long amountPaise, String idempotencyKey) {
    try { this.orderId = orderId == null ? null : UUID.fromString(orderId); } catch (Exception e) { this.orderId = null; }
    this.amountPaise = amountPaise; this.idempotencyKey = idempotencyKey;
  }
  public PaymentEntity(UUID orderId, UUID customerId, long amountPaise, String idemKey, Provider provider, Method method) {
    this.orderId = orderId; this.customerId = customerId;
    this.amountPaise = amountPaise; this.idempotencyKey = idemKey;
    this.provider = provider; this.method = method;
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public UUID getOrderId() { return orderId; }
  public String getOrderIdAsString() { return orderId == null ? null : orderId.toString(); }
  public void setOrderId(UUID v) { this.orderId = v; }
  public UUID getCustomerId() { return customerId; }
  public void setCustomerId(UUID v) { this.customerId = v; }
  /** Back-compat String status accessors. */
  public String getStatus() { return status == null ? null : status.name(); }
  public Status getStatusEnum() { return status; }
  public void setStatus(String s) {
    try { this.status = s == null ? Status.CREATED : Status.valueOf(s); }
    catch (Exception e) { this.status = Status.CREATED; }
    this.updatedAt = Instant.now();
  }
  public void setStatusEnum(Status s) { this.status = s; this.updatedAt = Instant.now(); }
  /** Back-compat provider reference = provider_payment_id. */
  public String getProviderReference() { return providerPaymentId; }
  public void setProviderReference(String r) { this.providerPaymentId = r; }
  public String getProviderPaymentId() { return providerPaymentId; }
  public void setProviderPaymentId(String v) { this.providerPaymentId = v; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String k) { this.idempotencyKey = k; }
  public long getAmountPaise() { return amountPaise; }
  public void setAmountPaise(long v) { this.amountPaise = v; }
  public Provider getProvider() { return provider; }
  public void setProvider(Provider p) { this.provider = p; }
  public Method getMethod() { return method; }
  public void setMethod(Method m) { this.method = m; }
  public Instant getVerifiedAt() { return verifiedAt; }
  public void setVerifiedAt(Instant v) { this.verifiedAt = v; }
  public void setFailure(String code, String msg) { this.failureCode = code; this.failureMessage = msg; }
  public String getWebhookPayload() { return webhookPayload; }
  public void setWebhookPayload(String v) { this.webhookPayload = v == null ? "{}" : v; }
}
