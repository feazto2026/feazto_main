package com.codewild.food.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Supabase truth: public.platform_users (0001). UUID PK, auth_user_id/phone/email
 * unique-nullable, account_status enum, is_phone_verified, metadata jsonb.
 */
@Entity
@Table(name = "platform_users",
  uniqueConstraints = {
    @UniqueConstraint(name = "platform_users_auth_user_id_key", columnNames = "auth_user_id"),
    @UniqueConstraint(name = "platform_users_phone_key", columnNames = "phone"),
    @UniqueConstraint(name = "platform_users_email_key", columnNames = "email")
  })
public class PlatformUser {
  public enum AccountStatus { PENDING_VERIFICATION, ACTIVE, SUSPENDED, DEACTIVATED }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "auth_user_id")
  private UUID authUserId;

  @Column(name = "phone")
  private String phone;

  @Column(name = "email", columnDefinition = "citext")
  private String email;

  @Column(name = "display_name", nullable = false)
  private String displayName = "";

  @Column(name = "primary_role_id")
  private UUID primaryRoleId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "primary_role_id", insertable = false, updatable = false)
  private Role primaryRole;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_status", nullable = false)
  private AccountStatus accountStatus = AccountStatus.ACTIVE;

  @Column(name = "is_phone_verified", nullable = false)
  private boolean phoneVerified = false;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private String metadataJson = "{}";

  // legacy local-auth support (not in Supabase; nullable, unused when Supabase Auth is source)
  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected PlatformUser() {}
  public PlatformUser(String phone, String email, String passwordHash) {
    this.phone = phone; this.email = email; this.passwordHash = passwordHash;
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public UUID getAuthUserId() { return authUserId; }
  public void setAuthUserId(UUID v) { this.authUserId = v; }
  public String getPhone() { return phone; }
  public void setPhone(String p) { this.phone = p; }
  public String getEmail() { return email; }
  public void setEmail(String e) { this.email = e; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String h) { this.passwordHash = h; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String d) { this.displayName = d; }
  public AccountStatus getAccountStatus() { return accountStatus; }
  public void setAccountStatus(AccountStatus s) { this.accountStatus = s; }
  public boolean isActive() { return deletedAt == null && accountStatus == AccountStatus.ACTIVE; }
  public void setActive(boolean a) {
    if (!a && accountStatus == AccountStatus.ACTIVE) accountStatus = AccountStatus.SUSPENDED;
    if (a && accountStatus == AccountStatus.SUSPENDED) accountStatus = AccountStatus.ACTIVE;
  }
  /** Back-compat for services storing roles JSON: now derived from primary_role/user_roles. */
  public String getRolesJson() { return "[\"CUSTOMER\"]"; }
  public void setRolesJson(String r) { /* no-op: roles are relational (user_roles) */ }
}
