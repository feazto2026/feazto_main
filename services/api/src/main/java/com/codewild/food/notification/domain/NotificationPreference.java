package com.codewild.food.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {
  @Id
  @Column(name = "user_id")
  private UUID userId;
  @Column(name = "push_enabled", nullable = false)
  private boolean pushEnabled = true;
  @Column(name = "sms_enabled", nullable = false)
  private boolean smsEnabled = true;
  @Column(name = "email_enabled", nullable = false)
  private boolean emailEnabled = true;
  @Column(name = "locale", nullable = false)
  private String locale = "en";
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected NotificationPreference() {}
  public NotificationPreference(UUID userId) { this.userId = userId; }
  public UUID getUserId() { return userId; }
}
