package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Supabase truth: public.vendors (0003). UUID PK/FK, status lifecycle, paise-free money here (commission bps). */
@Entity
@Table(name = "vendors")
public class VendorProfile {
  public enum Status {
    DRAFT, SUBMITTED, UNDER_REVIEW, ACTION_REQUIRED,
    APPROVED, SUSPENDED, REJECTED, DEACTIVATED
  }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "kitchen_name", nullable = false)
  private String kitchenName = "";

  @Column(name = "description", nullable = false)
  private String description = "";

  @Column(name = "phone")
  private String phone;
  @Column(name = "email", columnDefinition = "citext")
  private String email;
  @Column(name = "profile_image_url")
  private String profileImageUrl;
  @Column(name = "cover_image_url")
  private String coverImageUrl;

  @Column(name = "address_line", nullable = false)
  private String addressLine = "";
  @Column(name = "area", nullable = false)
  private String area = "";
  @Column(name = "city", nullable = false)
  private String city = "";
  @Column(name = "state", nullable = false)
  private String state = "";
  @Column(name = "postal_code", nullable = false)
  private String postalCode = "";
  @Column(name = "latitude")
  private Double latitude;
  @Column(name = "longitude")
  private Double longitude;

  @Column(name = "native_region_id")
  private UUID nativeRegionId;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "cuisine_tags", nullable = false, columnDefinition = "text[]")
  private String[] cuisineTags = new String[0];

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.DRAFT;

  @Column(name = "commission_bps", nullable = false)
  private int commissionBps = 1500;

  @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
  private BigDecimal ratingAvg = BigDecimal.ZERO;

  @Column(name = "rating_count", nullable = false)
  private int ratingCount = 0;

  @Column(name = "is_active", nullable = false)
  private boolean active = false;

  @Column(name = "service_radius_km", nullable = false, precision = 5, scale = 2)
  private BigDecimal serviceRadiusKm = new BigDecimal("5.00");

  @Column(name = "preparation_capacity_per_slot", nullable = false)
  private int preparationCapacityPerSlot = 20;

  @Column(name = "slot_buffer_minutes", nullable = false)
  private int slotBufferMinutes = 30;

  @Column(name = "fssai_license_no")
  private String fssaiLicenseNo;
  @Column(name = "payout_account_ref")
  private String payoutAccountRef;
  @Column(name = "terms_accepted_at")
  private Instant termsAcceptedAt;
  @Column(name = "approved_at")
  private Instant approvedAt;
  @Column(name = "approved_by")
  private UUID approvedBy;
  @Column(name = "suspension_reason")
  private String suspensionReason;
  @Column(name = "deleted_at")
  private Instant deletedAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected VendorProfile() {}
  public VendorProfile(String userId, String kitchenName, String city) {
    try { this.userId = userId == null ? null : UUID.fromString(userId); } catch (Exception e) { this.userId = null; }
    this.kitchenName = kitchenName == null ? "" : kitchenName;
    this.city = city == null ? "" : city;
  }
  public VendorProfile(UUID userId, String kitchenName, String city) {
    this.userId = userId; this.kitchenName = kitchenName == null ? "" : kitchenName;
    this.city = city == null ? "" : city;
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public UUID getUserId() { return userId; }
  public String getUserIdAsString() { return userId == null ? null : userId.toString(); }
  public String getKitchenName() { return kitchenName; }
  public void setKitchenName(String k) { this.kitchenName = k; }
  public String getCity() { return city; }
  public void setCity(String c) { this.city = c; }
  public String getStatus() { return status == null ? null : status.name(); }
  public Status getStatusEnum() { return status; }
  public void setStatus(String s) {
    try { this.status = s == null ? Status.DRAFT : Status.valueOf(s); }
    catch (Exception e) { this.status = Status.DRAFT; }
  }
  public void setStatusEnum(Status s) { this.status = s; }
  public boolean isAvailable() { return active && status == Status.APPROVED && deletedAt == null; }
  public boolean isActive() { return active; }
  public void setAvailable(boolean a) { this.active = a; }
  public void setActive(boolean a) { this.active = a; }
  public int getCommissionBps() { return commissionBps; }
  public String[] getCuisineTags() { return cuisineTags; }
}
