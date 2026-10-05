package com.codewild.food.shared.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final AuditLogRepository repo;
  public AuditService(AuditLogRepository repo) { this.repo = repo; }
  public void log(String actor, String action, String resourceType, String resourceId, String detail) {
    try { repo.save(new AuditLog(actor, action, resourceType, resourceId, detail)); }
    catch (Exception ignored) {}
  }
}
