package com.codewild.food.fulfillment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Supabase truth: public.deliveries (0006). 1:1 order link, rider FK,
 * verification artefacts are HASHES + expiry verified server-side.
 */
@Entity
@Table(name = "deliveries",
  uniqueConstraints = {
    @UniqueConstraint(name = "deliveries_delivery_number_key", columnNames = "delivery_number"),
    @UniqueConstraint(name = "deliveries_order_id_key", columnNames = "order_id"),
    @UniqueConstraint(name = "deliveries_idempotency_key_key", columnNames = "idempotency_key")
  },
  indexes = {
    @Index(name = "ix_deliveries_rider_status", columnList = "rider_id,status"),
    @Index(name = "ix_deliveries_status", columnList = "status")
  })
public class Delivery {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "delivery_number", nullable = false, unique = true)
  private String deliveryNumber = "DLV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;

  @Column(name = "rider_id")
  private UUID riderId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "rider_id", insertable = false, updatable = false)
  private RiderProfile rider;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey = "dlv:" + UUID.randomUUID();

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private DeliveryStateMachine.State status = DeliveryStateMachine.State.AVAILABLE;

  @Column(name = "pickup_code_hash")
  private String pickupCodeHash;
  @Column(name = "pickup_code_expires_at")
  private Instant pickupCodeExpiresAt;
  @Column(name = "delivery_code_hash")
  private String deliveryCodeHash;
  @Column(name = "delivery_code_expires_at")
  private Instant deliveryCodeExpiresAt;
  @Column(name = "pickup_qr_token_hash")
  private String pickupQrTokenHash;
  @Column(name = "pickup_qr_expires_at")
  private Instant pickupQrExpiresAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "pickup_address_snapshot", nullable = false, columnDefinition = "jsonb")
  private String pickupAddressSnapshot = "{}";
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dropoff_address_snapshot", nullable = false, columnDefinition = "jsonb")
  private String dropoffAddressSnapshot = "{}";

  @Column(name = "distance_km", precision = 7, scale = 2)
  private BigDecimal distanceKm;
  @Column(name = "delivery_fee_paise_snapshot", nullable = false)
  private long deliveryFeePaiseSnapshot = 0;
  @Column(name = "rider_payout_paise_snapshot")
  private Long riderPayoutPaiseSnapshot;
  @Column(name = "tip_paise", nullable = false)
  private long tipPaise = 0;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "proof_of_delivery", nullable = false, columnDefinition = "jsonb")
  private String proofOfDelivery = "{}";

  @Column(name = "failure_reason")
  private String failureReason;
  @Column(name = "estimated_pickup_at")
  private Instant estimatedPickupAt;
  @Column(name = "estimated_delivery_at")
  private Instant estimatedDeliveryAt;
  @Column(name = "picked_up_at")
  private Instant pickedUpAt;
  @Column(name = "delivered_at")
  private Instant deliveredAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Delivery() {}
  public Delivery(String orderId) {
    try { this.orderId = orderId == null ? null : UUID.fromString(orderId); } catch (Exception e) { this.orderId = null; }
  }
  public Delivery(UUID orderId) { this.orderId = orderId; }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public UUID getOrderId() { return orderId; }
  public String getOrderIdAsString() { return orderId == null ? null : orderId.toString(); }
  public UUID getRiderId() { return riderId; }
  public String getRiderIdAsString() { return riderId == null ? null : riderId.toString(); }
  public void setRiderId(UUID r) { this.riderId = r; }
  public void setRiderId(String r) { try { this.riderId = r == null ? null : UUID.fromString(r); } catch (Exception e) { this.riderId = null; } }
  public DeliveryStateMachine.State getStatus() { return status; }
  public void setStatus(DeliveryStateMachine.State s) { this.status = s; }
  public void transitionTo(DeliveryStateMachine.State next) {
    DeliveryStateMachine.validate(this.status, next);
    var from = this.status;
    this.status = next; this.updatedAt = Instant.now();
    if (next == DeliveryStateMachine.State.PICKED_UP) this.pickedUpAt = Instant.now();
    if (next == DeliveryStateMachine.State.DELIVERED) this.deliveredAt = Instant.now();
  }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String k) { this.idempotencyKey = k; }
  // hash+expiry accessors (no plaintext getters)
  public String getPickupCodeHash() { return pickupCodeHash; }
  public void setPickupCodeHash(String h) { this.pickupCodeHash = h; }
  public Instant getPickupCodeExpiresAt() { return pickupCodeExpiresAt; }
  public void setPickupCodeExpiresAt(Instant v) { this.pickupCodeExpiresAt = v; }
  public String getDeliveryCodeHash() { return deliveryCodeHash; }
  public void setDeliveryCodeHash(String h) { this.deliveryCodeHash = h; }
  public Instant getDeliveryCodeExpiresAt() { return deliveryCodeExpiresAt; }
  public void setDeliveryCodeExpiresAt(Instant v) { this.deliveryCodeExpiresAt = v; }
  public String getPickupQrTokenHash() { return pickupQrTokenHash; }
  public void setPickupQrTokenHash(String h) { this.pickupQrTokenHash = h; }
  public Instant getPickupQrExpiresAt() { return pickupQrExpiresAt; }
  public void setPickupQrExpiresAt(Instant v) { this.pickupQrExpiresAt = v; }
  /** Back-compat plaintext bridge: hashes the code on write; never readable. */
  public String getPickupCode() { return null; }
  public void setPickupCode(String code) {
    if (code != null) {
      this.pickupCodeHash = hash(code);
      this.pickupCodeExpiresAt = Instant.now().plusSeconds(6 * 3600);
    }
  }
  public String getDeliveryCode() { return null; }
  public void setDeliveryCode(String code) {
    if (code != null) {
      this.deliveryCodeHash = hash(code);
      this.deliveryCodeExpiresAt = Instant.now().plusSeconds(6 * 3600);
    }
  }
  public static String hash(String code) {
    try {
      var md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(code.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      var sb = new StringBuilder();
      for (byte b : d) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) { return code; }
  }
}
