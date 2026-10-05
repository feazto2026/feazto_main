package com.codewild.food.finance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rider_payouts",
  uniqueConstraints = {
    @UniqueConstraint(name = "rider_payouts_number_key", columnNames = "payout_number"),
    @UniqueConstraint(name = "rider_payouts_idem_key", columnNames = "idempotency_key"),
    @UniqueConstraint(name = "rider_payouts_provider_ref_key", columnNames = "provider_reference")
  })
public class RiderPayout {
  public enum Status { DRAFT, SCHEDULED, PROCESSING, COMPLETED, FAILED, CANCELLED }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "payout_number", nullable = false, unique = true)
  private String payoutNumber = "RPO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  @Column(name = "rider_id", nullable = false)
  private UUID riderId;
  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;
  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;
  @Column(name = "gross_amount_paise", nullable = false)
  private long grossAmountPaise = 0;
  @Column(name = "adjustments_paise", nullable = false)
  private long adjustmentsPaise = 0;
  @Column(name = "net_amount_paise", nullable = false)
  private long netAmountPaise = 0;
  @Column(name = "currency", nullable = false)
  private String currency = "INR";
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.DRAFT;
  @Column(name = "provider_reference", unique = true)
  private String providerReference;
  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey = "rpo:" + UUID.randomUUID();
  @Column(name = "initiated_by") private UUID initiatedBy;
  @Column(name = "approved_by") private UUID approvedBy;
  @Column(name = "failure_reason") private String failureReason;
  @Column(name = "processed_at") private Instant processedAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected RiderPayout() {}
  public RiderPayout(UUID riderId, LocalDate start, LocalDate end, long grossPaise, String idemKey) {
    this.riderId = riderId; this.periodStart = start; this.periodEnd = end;
    this.grossAmountPaise = grossPaise; this.idempotencyKey = idemKey;
    this.netAmountPaise = grossPaise;
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public Status getStatusEnum() { return status; }
  public String getStatus() { return status == null ? null : status.name(); }
  public void setStatusEnum(Status s) { this.status = s; this.updatedAt = Instant.now(); }
  public UUID getRiderId() { return riderId; }
  public long getNetAmountPaise() { return netAmountPaise; }
  public void setApprovedBy(UUID v) { this.approvedBy = v; }
  public void setProcessedAt(Instant v) { this.processedAt = v; }
  public void setInitiatedBy(UUID v) { this.initiatedBy = v; }
}
