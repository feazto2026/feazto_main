package com.codewild.food.operations.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Supabase truth: public.support_tickets (0007). */
@Entity
@Table(name = "support_tickets",
  uniqueConstraints = @UniqueConstraint(name = "support_tickets_number_key", columnNames = "ticket_number"))
public class SupportTicket {
  public enum Category { ORDER, PAYMENT, DELIVERY, SUBSCRIPTION, VENDOR, RIDER, ACCOUNT, OTHER }
  public enum Priority { LOW, MEDIUM, HIGH, URGENT }
  public enum Status { OPEN, ASSIGNED, IN_PROGRESS, WAITING_ON_CUSTOMER, RESOLVED, CLOSED, REOPENED }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "ticket_number", nullable = false, unique = true)
  private String ticketNumber = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

  @Column(name = "requester_id", nullable = false)
  private UUID requesterId;

  @Column(name = "requester_role", nullable = false)
  private String requesterRole = "CUSTOMER";

  @Column(name = "order_id") private UUID orderId;
  @Column(name = "delivery_id") private UUID deliveryId;
  @Column(name = "subject", nullable = false)
  private String subject = "";
  @Column(name = "description", nullable = false)
  private String description = "";

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private Category category = Category.OTHER;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false)
  private Priority priority = Priority.MEDIUM;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.OPEN;

  @Column(name = "assigned_to") private UUID assignedTo;
  @Column(name = "resolution_note") private String resolutionNote;
  @Column(name = "resolved_at") private Instant resolvedAt;
  @Column(name = "sla_due_at") private Instant slaDueAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected SupportTicket() {}
  public SupportTicket(String requester, String subject, String body) {
    try { this.requesterId = requester == null ? null : UUID.fromString(requester); } catch (Exception e) { this.requesterId = null; }
    this.subject = subject == null ? "" : subject;
    this.description = body == null ? "" : body;
  }
  public UUID getId() { return id; }
  public String getIdAsString() { return id == null ? null : id.toString(); }
  public Status getStatusEnum() { return status; }
  public String getStatus() { return status == null ? null : status.name(); }
  public void setStatus(String s) {
    try { this.status = s == null ? Status.OPEN : Status.valueOf(s); }
    catch (Exception e) { this.status = Status.OPEN; }
    this.updatedAt = Instant.now();
  }
  public String getAssignee() { return assignedTo == null ? null : assignedTo.toString(); }
  public void setAssignee(String a) { try { this.assignedTo = a == null ? null : UUID.fromString(a); } catch (Exception e) { this.assignedTo = null; } }
  public UUID getAssigneeUuid() { return assignedTo; }
  public void setAssigneeUuid(UUID v) { this.assignedTo = v; }
  public String getSubject() { return subject; }
  public UUID getRequesterId() { return requesterId; }
  public Priority getPriority() { return priority; }
}
