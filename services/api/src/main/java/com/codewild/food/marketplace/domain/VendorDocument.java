package com.codewild.food.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_documents")
public class VendorDocument {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "vendor_id", nullable = false)
  private UUID vendorId;
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
  protected VendorDocument() {}
  public UUID getId() { return id; }
}
