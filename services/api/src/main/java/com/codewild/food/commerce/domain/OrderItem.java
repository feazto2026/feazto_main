package com.codewild.food.commerce.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Supabase truth: public.order_items (0004). Immutable snapshots. */
@Entity
@Table(name = "order_items",
  indexes = @Index(name = "ix_order_items_order", columnList = "order_id"))
public class OrderItem {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "menu_item_id")
  private UUID menuItemId;

  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;

  @Column(name = "item_name_snapshot", nullable = false)
  private String itemNameSnapshot = "";

  @Column(name = "item_description_snapshot", nullable = false)
  private String itemDescriptionSnapshot = "";

  @Column(name = "unit_price_paise_snapshot", nullable = false)
  private long unitPricePaiseSnapshot = 0;

  @Column(name = "mrp_paise_snapshot")
  private Long mrpPaiseSnapshot;

  @Column(name = "quantity", nullable = false)
  private int quantity = 1;

  @Column(name = "discount_paise_snapshot", nullable = false)
  private long discountPaiseSnapshot = 0;

  @Column(name = "tax_paise_snapshot", nullable = false)
  private long taxPaiseSnapshot = 0;

  @Column(name = "line_total_paise", nullable = false)
  private long lineTotalPaise = 0;

  @JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
  @Column(name = "cuisine_tags_snapshot", nullable = false, columnDefinition = "text[]")
  private String[] cuisineTagsSnapshot = new String[0];

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "customization_snapshot", nullable = false, columnDefinition = "jsonb")
  private String customizationSnapshot = "{}";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected OrderItem() {}
  public OrderItem(String menuItemId, String name, java.math.BigDecimal price, int qty) {
    try { this.menuItemId = menuItemId == null ? null : UUID.fromString(menuItemId); } catch (Exception e) { this.menuItemId = null; }
    this.itemNameSnapshot = name == null ? "" : name;
    this.unitPricePaiseSnapshot = price == null ? 0 : price.multiply(java.math.BigDecimal.valueOf(100)).longValue();
    this.quantity = qty;
    this.lineTotalPaise = this.unitPricePaiseSnapshot * qty;
  }
  public OrderItem(UUID menuItemId, UUID vendorId, String name, long unitPaise, int qty) {
    this.menuItemId = menuItemId; this.vendorId = vendorId;
    this.itemNameSnapshot = name == null ? "" : name;
    this.unitPricePaiseSnapshot = unitPaise; this.quantity = qty;
    this.lineTotalPaise = unitPaise * qty;
  }
  public UUID getId() { return id; }
  public UUID getMenuItemId() { return menuItemId; }
  public String getMenuItemIdAsString() { return menuItemId == null ? null : menuItemId.toString(); }
  public String getItemNameSnapshot() { return itemNameSnapshot; }
  public java.math.BigDecimal getUnitPriceSnapshot() { return java.math.BigDecimal.valueOf(unitPricePaiseSnapshot, 2); }
  public long getUnitPricePaiseSnapshot() { return unitPricePaiseSnapshot; }
  public int getQuantity() { return quantity; }
  public long getLineTotalPaise() { return lineTotalPaise; }
  public void setLineTotalPaise(long v) { this.lineTotalPaise = v; }
  public UUID getVendorId() { return vendorId; }
  public void setVendorId(UUID v) { this.vendorId = v; }
  public void setOrderId(UUID v) { this.orderId = v; }
}
