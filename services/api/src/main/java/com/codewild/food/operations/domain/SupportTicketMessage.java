package com.codewild.food.operations.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "support_ticket_messages")
public class SupportTicketMessage {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "ticket_id", nullable = false)
  private UUID ticketId;
  @Column(name = "sender_id") private UUID senderId;
  @Column(name = "message", nullable = false)
  private String message = "";
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attachments", nullable = false, columnDefinition = "jsonb")
  private String attachments = "[]";
  @Column(name = "is_internal", nullable = false)
  private boolean internal = false;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  protected SupportTicketMessage() {}
  public SupportTicketMessage(UUID ticketId, UUID senderId, String message) {
    this.ticketId = ticketId; this.senderId = senderId; this.message = message;
  }
  public UUID getId() { return id; }
}
