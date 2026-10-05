package com.codewild.food.shared.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Supabase truth: public.audit_logs (0007). Append-only. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_role")
  private String actorRole;

  @Column(name = "action", nullable = false)
  private String action = "";

  @Column(name = "entity_type", nullable = false)
  private String entityType = "";

  @Column(name = "entity_id")
  private UUID entityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "old_value", nullable = false, columnDefinition = "jsonb")
  private String oldValue = "{}";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "new_value", nullable = false, columnDefinition = "jsonb")
  private String newValue = "{}";

  @Column(name = "reason")
  private String reason;
  @Column(name = "ip_address", columnDefinition = "inet")
  private String ipAddress;
  @Column(name = "user_agent")
  private String userAgent;
  @Column(name = "request_id")
  private String requestId;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected AuditLog() {}
  public AuditLog(String actorUserId, String action, String resourceType, String resourceId, String detail) {
    try { this.actorId = actorUserId == null ? null : UUID.fromString(actorUserId); } catch (Exception e) { this.actorId = null; }
    this.action = action == null ? "" : action;
    this.entityType = resourceType == null ? "" : resourceType;
    try { this.entityId = resourceId == null ? null : UUID.fromString(resourceId); } catch (Exception e) { this.entityId = null; }
    this.reason = detail;
    this.newValue = detail == null ? "{}" : "{\"detail\":" + JSONObject.quote(detail) + "}";
  }
  public AuditLog(UUID actorId, String actorRole, String action, String entityType, UUID entityId,
                  String oldJson, String newJson, String reason) {
    this.actorId = actorId; this.actorRole = actorRole; this.action = action;
    this.entityType = entityType; this.entityId = entityId;
    this.oldValue = oldJson == null ? "{}" : oldJson;
    this.newValue = newJson == null ? "{}" : newJson;
    this.reason = reason;
  }
  public UUID getId() { return id; }

  private static final class JSONObject {
    static String quote(String s) {
      return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
  }
}
