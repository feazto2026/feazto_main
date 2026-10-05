package com.codewild.food.fulfillment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rider_documents")
public class RiderDocument {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "rider_id", nullable = false)
  private UUID riderId;
  @Column(name = "document_type", nullable = false)
  private String documentType = "";
  @Column(name = "file_url", nullable = false)
  private String fileUrl = "";
  @Column(name = "status", nullable = false)
  private String status = "PENDING";
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
  protected RiderDocument() {}
  public UUID getId() { return id; }
}
