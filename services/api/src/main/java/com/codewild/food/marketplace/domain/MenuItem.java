package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Supabase truth: public.menu_items (0003). Money in paise (bigint). */
@Entity
@Table(name = "menu_items",
  indexes = { @Index(name = "ix_menu_items_vendor_avail", columnList = "vendor_id,is_available") })
public class MenuItem {
  public enum FoodType { VEG, NON_VEG, EGG, VEGAN, JAIN }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "menu_id", nullable = false)
  private UUID menuId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "menu_id", insertable = false, updatable = false)
  private Menu menu;

  @Column(name = "category_id")
  private UUID categoryId;

  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vendor_id", insertable = false, updatable = false)
  private VendorProfile vendor;

  @Column(name = "name", nullable = false)
  private String name = "";

  @Column(name = "description", nullable = false)
  private String description = "";

  @Column(name = "image_url")
  private String imageUrl;

  @Column(name = "price_paise", nullable = false)
  private long pricePaise = 0;

  @Column(name = "mrp_paise")
  private Long mrpPaise;

  @Column(name = "currency", nullable = false)
  private String currency = "INR";

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "cuisine_tags", nullable = false, columnDefinition = "text[]")
  private String[] cuisineTags = new String[0];

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "region_tags", nullable = false, columnDefinition = "text[]")
  private String[] regionTags = new String[0];

  @Enumerated(EnumType.STRING)
  @Column(name = "food_type", nullable = false)
  private FoodType foodType = FoodType.VEG;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "meal_types", nullable = false, columnDefinition = "text[]")
  private String[] mealTypes = new String[0];

  @Column(name = "preparation_time_minutes")
  private Integer preparationTimeMinutes;

  @Column(name = "is_available", nullable = false)
  private boolean available = true;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected MenuItem() {}
  public MenuItem(String vendorId, String name, java.math.BigDecimal price) {
    try { this.vendorId = vendorId == null ? null : UUID.fromString(vendorId); } catch (Exception e) { this.vendorId = null; }
    this.name = name;
    this.pricePaise = price == null ? 0 : price.multiply(java.math.BigDecimal.valueOf(100)).longValue();
  }
  public MenuItem(UUID vendorId, UUID menuId, String name, long pricePaise) {
    this.vendorId = vendorId; this.menuId = menuId; this.name = name; this.pricePaise = pricePaise;
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public UUID getVendorId() { return vendorId; }
  public String getVendorIdAsString() { return vendorId == null ? null : vendorId.toString(); }
  public UUID getMenuId() { return menuId; }
  public void setMenuId(UUID m) { this.menuId = m; }
  public String getName() { return name; }
  public void setName(String n) { this.name = n; }
  /** Back-compat rupees view of price_paise snapshot. */
  public java.math.BigDecimal getPrice() {
    return java.math.BigDecimal.valueOf(pricePaise, 2);
  }
  public void setPrice(java.math.BigDecimal rupees) {
    this.pricePaise = rupees == null ? 0 : rupees.multiply(java.math.BigDecimal.valueOf(100)).longValue();
  }
  public long getPricePaise() { return pricePaise; }
  public void setPricePaise(long p) { this.pricePaise = p; }
  public boolean isPublished() { return active && available && deletedAt == null; }
  public boolean isAvailable() { return available; }
  public void setAvailable(boolean a) { this.available = a; }
  public boolean isActive() { return active; }
  public void setActive(boolean a) { this.active = a; }
  public int getDailyCapacity() { return 50; }
  public String getDescription() { return description; }
  public FoodType getFoodType() { return foodType; }
}
