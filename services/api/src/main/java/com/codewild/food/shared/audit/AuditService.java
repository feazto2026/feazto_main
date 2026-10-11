package com.codewild.food.shared.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
  private static final Logger log = LoggerFactory.getLogger(AuditService.class);
  private final AuditLogRepository repo;
  public AuditService(AuditLogRepository repo) { this.repo = repo; }

  /**
   * Best-effort audit write: isolated REQUIRES_NEW TX + flush inside try/catch so an
   * audit failure (e.g. inet type mismatch, null ip) never rolls back the business TX.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void log(String actor, String action, String resourceType, String resourceId, String detail) {
    try { repo.saveAndFlush(new AuditLog(actor, action, resourceType, resourceId, detail)); }
    catch (Exception ex) { log.warn("Audit log failed action={}: {}", action, ex.getMessage()); }
  }
}
