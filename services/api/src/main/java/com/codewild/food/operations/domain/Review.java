package com.codewild.food.operations.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reviews",
  uniqueConstraints = @UniqueConstraint(name = "reviews_order_key", columnNames = "order_id"))
public class Review {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;
  @Column(name = "customer_id", nullable = false)
  private UUID customerId;
  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;
  @Column(name = "rider_id") private UUID riderId;
  @Column(name = "delivery_id") private UUID deliveryId;
  @Column(name = "vendor_rating") private Short vendorRating;
  @Column(name = "food_rating") private Short foodRating;
  @Column(name = "delivery_rating") private Short deliveryRating;
  @Column(name = "comment") private String comment;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "images", nullable = false, columnDefinition = "jsonb")
  private String images = "[]";
  @Column(name = "status", nullable = false)
  private String status = "PUBLISHED";
  @Column(name = "moderated_by") private UUID moderatedBy;
  @Column(name = "moderated_at") private Instant moderatedAt;
  @Column(name = "moderation_reason") private String moderationReason;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected Review() {}
  public UUID getId() { return id; }
}
