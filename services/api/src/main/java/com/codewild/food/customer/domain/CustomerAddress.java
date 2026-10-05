package com.codewild.food.customer.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Supabase truth: public.addresses (0002). UUID PK/FK, geo pair + range checks. */
@Entity
@Table(name = "addresses",
  indexes = { @Index(name = "ix_addresses_customer", columnList = "customer_id") })
public class CustomerAddress {
  public enum AddressType { HOME, WORK, OTHER }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", insertable = false, updatable = false)
  private CustomerProfile customer;

  @Column(name = "label", nullable = false)
  private String label = "Home";

  @Enumerated(EnumType.STRING)
  @Column(name = "address_type", nullable = false)
  private AddressType addressType = AddressType.HOME;

  @Column(name = "house_flat", nullable = false)
  private String houseFlat = "";
  @Column(name = "street", nullable = false)
  private String street = "";
  @Column(name = "landmark")
  private String landmark;
  @Column(name = "area", nullable = false)
  private String area = "";
  @Column(name = "city", nullable = false)
  private String city = "";
  @Column(name = "state", nullable = false)
  private String state = "";
  @Column(name = "postal_code", nullable = false)
  private String postalCode = "";
  @Column(name = "latitude")
  private Double latitude;
  @Column(name = "longitude")
  private Double longitude;
  @Column(name = "delivery_instructions")
  private String deliveryInstructions;
  @Column(name = "is_default", nullable = false)
  private boolean isDefault = false;
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected CustomerAddress() {}
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public UUID getCustomerId() { return customerId; }
  public void setCustomerId(UUID c) { this.customerId = c; }
  public void setCustomerId(String c) { try { this.customerId = c == null ? null : UUID.fromString(c); } catch (Exception e) { this.customerId = null; } }
  public String getLabel() { return label; }
  public void setLabel(String l) { this.label = l; }
  /** Back-compat single-line field: maps to street. */
  public String getLine1() { return street; }
  public void setLine1(String l) { this.street = l == null ? "" : l; }
  public String getCity() { return city; }
  public void setCity(String c) { this.city = c == null ? "" : c; }
  public String getPostalCode() { return postalCode; }
  public void setPostalCode(String p) { this.postalCode = p == null ? "" : p; }
  public Double getLat() { return latitude; }
  public void setLat(Double lat) { this.latitude = lat; }
  public Double getLng() { return longitude; }
  public void setLng(Double lng) { this.longitude = lng; }
  public Double getLatitude() { return latitude; }
  public Double getLongitude() { return longitude; }
  public boolean getIsDefault() { return isDefault; }
  public void setDefault(boolean d) { this.isDefault = d; }
  public String getArea() { return area; }
  public String getState() { return state; }
}
