package com.codewild.food.customer.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Supabase truth: public.customer_profiles (0002). UUID PK/FK, arrays, language. */
@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private com.codewild.food.identity.domain.PlatformUser platformUser;

  @Column(name = "full_name", nullable = false)
  private String fullName = "";

  @Column(name = "avatar_url")
  private String avatarUrl;

  @Column(name = "hometown_region_id")
  private UUID hometownRegionId;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "preferred_region_ids", nullable = false, columnDefinition = "uuid[]")
  private UUID[] preferredRegionIds = new UUID[0];

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "preferred_cuisines", nullable = false, columnDefinition = "text[]")
  private String[] preferredCuisines = new String[0];

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "dietary_preferences", nullable = false, columnDefinition = "text[]")
  private String[] dietaryPreferences = new String[0];

  @Column(name = "preferred_language", nullable = false)
  private String preferredLanguage = "en";

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected CustomerProfile() {}
  public CustomerProfile(String userId, String displayName) {
    this.userId = parseUuid(userId);
    this.fullName = displayName == null ? "" : displayName;
  }
  public CustomerProfile(UUID userId, String fullName) {
    this.userId = userId; this.fullName = fullName == null ? "" : fullName;
  }
  private static UUID parseUuid(String s) {
    try { return s == null ? null : UUID.fromString(s); } catch (Exception e) { return null; }
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public UUID getUserId() { return userId; }
  public String getUserIdAsString() { return userId == null ? null : userId.toString(); }
  public String getDisplayName() { return fullName; }
  public void setDisplayName(String n) { this.fullName = n == null ? "" : n; }
  public String getFullName() { return fullName; }
  public void setFullName(String n) { this.fullName = n == null ? "" : n; }
  /** Back-compat: hometown free text maps to hometown_region_id when it parses as UUID. */
  public String getHometown() { return hometownRegionId == null ? null : hometownRegionId.toString(); }
  public void setHometown(String h) {
    try { this.hometownRegionId = (h == null || h.isBlank()) ? null : UUID.fromString(h); }
    catch (Exception e) { this.hometownRegionId = null; }
  }
  public UUID getHometownRegionId() { return hometownRegionId; }
  public void setHometownRegionId(UUID v) { this.hometownRegionId = v; }
  public String[] getPreferredCuisines() { return preferredCuisines; }
}
