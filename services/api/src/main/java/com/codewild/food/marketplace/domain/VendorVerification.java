package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "vendor_verifications")
public class VendorVerification {
  public enum Type { KYC, FSSAI, ADDRESS, BANK, DOCUMENT }
  public enum Status { PENDING, APPROVED, REJECTED, CHANGES_REQUESTED, EXPIRED }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;
  @Enumerated(EnumType.STRING)
  @Column(name = "verification_type", nullable = false)
  private Type verificationType = Type.KYC;
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.PENDING;
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "document_urls", nullable = false, columnDefinition = "text[]")
  private String[] documentUrls = new String[0];
  @Column(name = "submitted_by") private UUID submittedBy;
  @Column(name = "reviewed_by") private UUID reviewedBy;
  @Column(name = "reviewed_at") private Instant reviewedAt;
  @Column(name = "rejection_reason") private String rejectionReason;
  @Column(name = "admin_notes") private String adminNotes;
  @Column(name = "idempotency_key", unique = true)
  private String idempotencyKey;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected VendorVerification() {}
  public UUID getId() { return id; }
  public UUID getVendorId() { return vendorId; }
  public Status getStatus() { return status; }
  public void setStatus(Status s) { this.status = s; }
}
