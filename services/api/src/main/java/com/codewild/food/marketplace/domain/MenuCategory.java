package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "menu_categories",
  uniqueConstraints = @UniqueConstraint(name = "menu_categories_menu_name_key", columnNames = {"menu_id","name"}))
public class MenuCategory {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "menu_id", nullable = false)
  private UUID menuId;
  @Column(name = "name", nullable = false)
  private String name = "";
  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected MenuCategory() {}
  public MenuCategory(UUID menuId, String name) { this.menuId = menuId; this.name = name; }
  public UUID getId() { return id; }
  public UUID getMenuId() { return menuId; }
  public String getName() { return name; }
}
