package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Supabase truth: public.service_zones (0003). Zones are data — no city hard-coded. */
@Entity
@Table(name = "service_zones",
  uniqueConstraints = @UniqueConstraint(name = "service_zones_code_key", columnNames = "code"))
public class ServiceZone {
  public enum Kind { CITY, AREA, PIN, RADIUS, POLYGON }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "code", nullable = false, unique = true)
  private String code;
  @Column(name = "name", nullable = false)
  private String name = "";
  @Column(name = "city", nullable = false)
  private String city = "";
  @Column(name = "state", nullable = false)
  private String state = "";
  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false)
  private Kind kind = Kind.AREA;
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "postal_codes", nullable = false, columnDefinition = "text[]")
  private String[] postalCodes = new String[0];
  @Column(name = "center_lat") private Double centerLat;
  @Column(name = "center_lng") private Double centerLng;
  @Column(name = "radius_km", precision = 6, scale = 2)
  private BigDecimal radiusKm;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "geojson", nullable = false, columnDefinition = "jsonb")
  private String geojson = "{}";
  @Column(name = "delivery_fee_paise", nullable = false)
  private long deliveryFeePaise = 0;
  @Column(name = "min_order_paise", nullable = false)
  private long minOrderPaise = 0;
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected ServiceZone() {}
  public ServiceZone(String code, String name, Kind kind) { this.code = code; this.name = name; this.kind = kind; }
  public UUID getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public String getCity() { return city; }
  public void setCity(String c) { this.city = c; }
  public Kind getKind() { return kind; }
  public String[] getPostalCodes() { return postalCodes; }
  public void setPostalCodes(String[] v) { this.postalCodes = v; }
  public Double getCenterLat() { return centerLat; }
  public void setCenterLat(Double v) { this.centerLat = v; }
  public Double getCenterLng() { return centerLng; }
  public void setCenterLng(Double v) { this.centerLng = v; }
  public BigDecimal getRadiusKm() { return radiusKm; }
  public void setRadiusKm(BigDecimal v) { this.radiusKm = v; }
  public long getDeliveryFeePaise() { return deliveryFeePaise; }
  public void setDeliveryFeePaise(long v) { this.deliveryFeePaise = v; }
  public long getMinOrderPaise() { return minOrderPaise; }
  public boolean isActive() { return active; }
}
