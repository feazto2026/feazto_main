package com.codewild.food.subscription.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Supabase truth: public.subscriptions (0005). Contract with frozen pricing. */
@Entity
@Table(name = "subscriptions",
  uniqueConstraints = {
    @UniqueConstraint(name = "subscriptions_number_key", columnNames = "subscription_number"),
    @UniqueConstraint(name = "subscriptions_idempotency_key_key", columnNames = "idempotency_key")
  })
public class Subscription {
  public enum Status {
    DRAFT, PAYMENT_PENDING, ACTIVE, PAUSED,
    SKIPPED, EXPIRED, CANCELLED, PAYMENT_FAILED
  }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "subscription_number", nullable = false, unique = true)
  private String subscriptionNumber = "SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", insertable = false, updatable = false)
  private com.codewild.food.customer.domain.CustomerProfile customer;
  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vendor_id", insertable = false, updatable = false)
  private com.codewild.food.marketplace.domain.VendorProfile vendor;
  @Column(name = "meal_plan_id", nullable = false)
  private UUID mealPlanId;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "meal_plan_id", insertable = false, updatable = false)
  private MealPlan mealPlan;
  @Column(name = "address_id")
  private UUID addressId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.DRAFT;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate = LocalDate.now();
  @Column(name = "end_date", nullable = false)
  private LocalDate endDate = LocalDate.now().plusDays(29);
  @Column(name = "total_meals", nullable = false)
  private int totalMeals = 30;
  @Column(name = "meals_consumed", nullable = false)
  private int mealsConsumed = 0;
  @Column(name = "meals_skipped", nullable = false)
  private int mealsSkipped = 0;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "price_snapshot", nullable = false, columnDefinition = "jsonb")
  private String priceSnapshot = "{}";

  @Column(name = "total_amount_paise", nullable = false)
  private long totalAmountPaise = 0;
  @Column(name = "amount_paid_paise", nullable = false)
  private long amountPaidPaise = 0;
  @Column(name = "currency", nullable = false)
  private String currency = "INR";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "delivery_preferences", nullable = false, columnDefinition = "jsonb")
  private String deliveryPreferences = "{}";

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "skip_dates", nullable = false, columnDefinition = "date[]")
  private LocalDate[] skipDates = new LocalDate[0];

  @Column(name = "paused_from") private LocalDate pausedFrom;
  @Column(name = "paused_to") private LocalDate pausedTo;
  @Column(name = "cancelled_at") private Instant cancelledAt;
  @Column(name = "cancellation_reason") private String cancellationReason;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Subscription() {}
  public Subscription(String customerId, String vendorId, String mealPlanId) {
    try { this.customerId = customerId == null ? null : UUID.fromString(customerId); } catch (Exception e) { this.customerId = null; }
    try { this.vendorId = vendorId == null ? null : UUID.fromString(vendorId); } catch (Exception e) { this.vendorId = null; }
    try { this.mealPlanId = mealPlanId == null ? null : UUID.fromString(mealPlanId); } catch (Exception e) { this.mealPlanId = null; }
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public UUID getCustomerId() { return customerId; }
  public String getCustomerIdAsString() { return customerId == null ? null : customerId.toString(); }
  public void setCustomerId(UUID c) { this.customerId = c; }
  public void setCustomerId(String c) { try { this.customerId = c == null ? null : UUID.fromString(c); } catch (Exception e) { this.customerId = null; } }
  public UUID getVendorId() { return vendorId; }
  public void setVendorId(UUID v) { this.vendorId = v; }
  public void setVendorId(String v) { try { this.vendorId = v == null ? null : UUID.fromString(v); } catch (Exception e) { this.vendorId = null; } }
  public UUID getMealPlanId() { return mealPlanId; }
  public void setMealPlanId(UUID m) { this.mealPlanId = m; }
  public void setMealPlanId(String m) { try { this.mealPlanId = m == null ? null : UUID.fromString(m); } catch (Exception e) { this.mealPlanId = null; } }
  /** Back-compat String status. */
  public String getStatus() { return status == null ? null : status.name(); }
  public Status getStatusEnum() { return status; }
  public void setStatus(String s) {
    try { this.status = s == null ? Status.DRAFT : Status.valueOf(s); }
    catch (Exception e) { this.status = Status.DRAFT; }
    this.updatedAt = Instant.now();
  }
  public void setStatusEnum(Status s) { this.status = s; this.updatedAt = Instant.now(); }
  public LocalDate getStartDate() { return startDate; }
  public void setStartDate(LocalDate v) { this.startDate = v; }
  public LocalDate getEndDate() { return endDate; }
  public void setEndDate(LocalDate v) { this.endDate = v; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String k) { this.idempotencyKey = k; }
  public String getSubscriptionNumber() { return subscriptionNumber; }
  public LocalDate[] getSkipDates() { return skipDates; }
  public long getTotalAmountPaise() { return totalAmountPaise; }
  public void setTotalAmountPaise(long v) { this.totalAmountPaise = v; }
  public int getTotalMeals() { return totalMeals; }
  public void setTotalMeals(int v) { this.totalMeals = v; }
}
