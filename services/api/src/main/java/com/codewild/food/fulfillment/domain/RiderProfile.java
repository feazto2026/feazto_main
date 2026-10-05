package com.codewild.food.fulfillment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Supabase truth: public.riders (0006). UUID PK/FK, status + kyc_status, online/city. */
@Entity
@Table(name = "riders")
public class RiderProfile {
  public enum Status {
    DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, ACTIVE, INACTIVE, SUSPENDED, DEACTIVATED
  }
  public enum KycStatus { PENDING, APPROVED, REJECTED, CHANGES_REQUESTED, EXPIRED }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "display_name", nullable = false)
  private String displayName = "";
  @Column(name = "phone") private String phone;
  @Column(name = "photo_url") private String photoUrl;
  @Column(name = "vehicle_type", nullable = false)
  private String vehicleType = "BIKE";
  @Column(name = "vehicle_number") private String vehicleNumber;
  @Column(name = "city", nullable = false)
  private String city = "";

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.DRAFT;

  @Enumerated(EnumType.STRING)
  @Column(name = "kyc_status", nullable = false)
  private KycStatus kycStatus = KycStatus.PENDING;

  @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
  private java.math.BigDecimal ratingAvg = java.math.BigDecimal.ZERO;
  @Column(name = "rating_count", nullable = false)
  private int ratingCount = 0;
  @Column(name = "total_deliveries", nullable = false)
  private int totalDeliveries = 0;
  @Column(name = "current_lat") private Double currentLat;
  @Column(name = "current_lng") private Double currentLng;
  @Column(name = "last_location_at") private Instant lastLocationAt;
  @Column(name = "is_online", nullable = false)
  private boolean online = false;
  @Column(name = "max_concurrent_deliveries", nullable = false)
  private int maxConcurrentDeliveries = 2;
  @Column(name = "approved_at") private Instant approvedAt;
  @Column(name = "approved_by") private UUID approvedBy;
  @Column(name = "suspension_reason") private String suspensionReason;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected RiderProfile() {}
  public RiderProfile(String userId) {
    try { this.userId = userId == null ? null : UUID.fromString(userId); } catch (Exception e) { this.userId = null; }
  }
  public RiderProfile(UUID userId) { this.userId = userId; }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public UUID getUserId() { return userId; }
  public boolean isAvailable() { return online && (status == Status.APPROVED || status == Status.ACTIVE); }
  public boolean isOnline() { return online; }
  public void setAvailable(boolean a) { this.online = a; }
  public void setOnline(boolean o) { this.online = o; }
  /** Back-compat zone id: riders carry city; zone filtering happens via delivery city. */
  public String getServiceZoneId() { return city; }
  public void setServiceZoneId(String z) { if (z != null) this.city = z; }
  public String getCity() { return city; }
  public void setCity(String c) { this.city = c; }
  public String getStatus() { return status == null ? null : status.name(); }
  public Status getStatusEnum() { return status; }
  public void setStatus(String s) {
    try { this.status = s == null ? Status.DRAFT : Status.valueOf(s); }
    catch (Exception e) { this.status = Status.DRAFT; }
  }
  public Double getCurrentLat() { return currentLat; }
  public Double getCurrentLng() { return currentLng; }
  public int getMaxConcurrentDeliveries() { return maxConcurrentDeliveries; }
}
