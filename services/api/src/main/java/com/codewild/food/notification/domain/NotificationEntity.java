package com.codewild.food.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Supabase truth: public.notifications (0007) + notification_preferences. */
@Entity
@Table(name = "notifications")
public class NotificationEntity {
  public enum Channel { PUSH, SMS, EMAIL, IN_APP }
  public enum Status { PENDING, SENT, DELIVERED, READ, FAILED, CANCELLED }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "recipient_user_id", nullable = false)
  private UUID recipientUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false)
  private Channel channel = Channel.IN_APP;

  @Column(name = "template_code", nullable = false)
  private String templateCode = "GENERIC";
  @Column(name = "title", nullable = false)
  private String title = "";
  @Column(name = "body", nullable = false)
  private String body = "";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "data", nullable = false, columnDefinition = "jsonb")
  private String dataJson = "{}";

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.PENDING;

  @Column(name = "dedupe_key", unique = true)
  private String dedupeKey;
  @Column(name = "sent_at") private Instant sentAt;
  @Column(name = "read_at") private Instant readAt;
  @Column(name = "failure_reason") private String failureReason;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected NotificationEntity() {}
  public NotificationEntity(String userId, String channel, String title, String body) {
    try { this.recipientUserId = userId == null ? null : UUID.fromString(userId); } catch (Exception e) { this.recipientUserId = null; }
    try { this.channel = channel == null ? Channel.IN_APP : Channel.valueOf(channel); }
    catch (Exception e) { this.channel = Channel.IN_APP; }
    this.title = title == null ? "" : title;
    this.body = body == null ? "" : body;
  }
  public NotificationEntity(UUID recipient, Channel channel, String title, String body, String dedupeKey) {
    this.recipientUserId = recipient; this.channel = channel;
    this.title = title == null ? "" : title; this.body = body == null ? "" : body;
    this.dedupeKey = dedupeKey;
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public UUID getRecipientUserId() { return recipientUserId; }
  /** Back-compat user id. */
  public String getUserId() { return recipientUserId == null ? null : recipientUserId.toString(); }
  public String getTitle() { return title; }
  public String getBody() { return body; }
  public Status getStatus() { return status; }
  public void setStatus(Status s) { this.status = s; this.updatedAt = Instant.now(); }
  public Instant getCreatedAt() { return createdAt; }
}
