package com.codewild.food.commerce.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "coupons",
  uniqueConstraints = @UniqueConstraint(name = "coupons_code_key", columnNames = "code"))
public class Coupon {
  public enum DiscountType { PERCENT, FLAT, FREE_DELIVERY }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "code", nullable = false, unique = true, columnDefinition = "citext")
  private String code;
  @Column(name = "title", nullable = false)
  private String title = "";
  @Column(name = "description", nullable = false)
  private String description = "";
  @Enumerated(EnumType.STRING)
  @Column(name = "discount_type", nullable = false)
  private DiscountType discountType = DiscountType.FLAT;
  @Column(name = "discount_bps") private Integer discountBps;
  @Column(name = "discount_paise") private Long discountPaise;
  @Column(name = "max_discount_paise") private Long maxDiscountPaise;
  @Column(name = "min_order_paise", nullable = false)
  private long minOrderPaise = 0;
  @Column(name = "usage_limit_total") private Integer usageLimitTotal;
  @Column(name = "usage_limit_per_user", nullable = false)
  private int usageLimitPerUser = 1;
  @Column(name = "used_count", nullable = false)
  private int usedCount = 0;
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "applicable_vendor_ids", nullable = false, columnDefinition = "uuid[]")
  private UUID[] applicableVendorIds = new UUID[0];
  @Column(name = "valid_from") private Instant validFrom;
  @Column(name = "valid_to") private Instant validTo;
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected Coupon() {}
  public Coupon(String code, String title, DiscountType type) { this.code = code; this.title = title; this.discountType = type; }
  public UUID getId() { return id; }
  public String getCode() { return code; }
  public DiscountType getDiscountType() { return discountType; }
  public Integer getDiscountBps() { return discountBps; }
  public void setDiscountBps(Integer v) { this.discountBps = v; }
  public Long getDiscountPaise() { return discountPaise; }
  public void setDiscountPaise(Long v) { this.discountPaise = v; }
  public Long getMaxDiscountPaise() { return maxDiscountPaise; }
  public void setMaxDiscountPaise(Long v) { this.maxDiscountPaise = v; }
  public long getMinOrderPaise() { return minOrderPaise; }
  public boolean isActive() { return active; }
  public Instant getValidFrom() { return validFrom; }
  public Instant getValidTo() { return validTo; }
  /** Server-side discount for a subtotal (caps + floor at subtotal). */
  public long discountFor(long subtotalPaise) {
    if (!active) return 0;
    Instant now = Instant.now();
    if (validFrom != null && now.isBefore(validFrom)) return 0;
    if (validTo != null && now.isAfter(validTo)) return 0;
    if (subtotalPaise < minOrderPaise) return 0;
    long d = switch (discountType) {
      case PERCENT -> discountBps == null ? 0 : (subtotalPaise * discountBps) / 10000L;
      case FLAT -> discountPaise == null ? 0 : discountPaise;
      case FREE_DELIVERY -> 0;
    };
    if (maxDiscountPaise != null) d = Math.min(d, maxDiscountPaise);
    return Math.min(d, subtotalPaise);
  }
}
