package com.codewild.food.subscription.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "meal_plans")
public class MealPlan {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;
  @Column(name = "name", nullable = false)
  private String name = "";
  @Column(name = "description", nullable = false)
  private String description = "";
  @Column(name = "image_url") private String imageUrl;
  @Column(name = "price_per_meal_paise", nullable = false)
  private long pricePerMealPaise = 0;
  @Column(name = "price_monthly_paise") private Long priceMonthlyPaise;
  @Column(name = "currency", nullable = false)
  private String currency = "INR";
  @Column(name = "meals_per_day", nullable = false)
  private int mealsPerDay = 1;
  @Column(name = "trial_available", nullable = false)
  private boolean trialAvailable = false;
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "applicable_days", nullable = false, columnDefinition = "integer[]")
  private Integer[] applicableDays = new Integer[]{0,1,2,3,4,5,6};
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected MealPlan() {}
  public MealPlan(UUID vendorId, String name, long pricePerMealPaise) {
    this.vendorId = vendorId; this.name = name; this.pricePerMealPaise = pricePerMealPaise;
  }
  public UUID getId() { return id; }
  public UUID getVendorId() { return vendorId; }
  public String getName() { return name; }
  public long getPricePerMealPaise() { return pricePerMealPaise; }
  public boolean isActive() { return active; }
}
