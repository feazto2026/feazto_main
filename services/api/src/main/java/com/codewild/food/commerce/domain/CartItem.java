package com.codewild.food.commerce.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cart_items",
  uniqueConstraints = @UniqueConstraint(name = "cart_items_cart_item_hash_key",
    columnNames = {"cart_id","menu_item_id","customization_hash"}))
public class CartItem {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "cart_id", nullable = false)
  private UUID cartId;
  @Column(name = "menu_item_id", nullable = false)
  private UUID menuItemId;
  @Column(name = "quantity", nullable = false)
  private int quantity = 1;
  @Column(name = "unit_price_paise_snapshot", nullable = false)
  private long unitPricePaiseSnapshot = 0;
  @Column(name = "customization_hash", nullable = false)
  private String customizationHash = "";
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "customizations", nullable = false, columnDefinition = "jsonb")
  private String customizations = "{}";
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected CartItem() {}
  public UUID getId() { return id; }
}
