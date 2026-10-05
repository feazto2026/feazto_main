package com.codewild.food.operations.application;

import com.codewild.food.marketplace.infrastructure.VendorRepository;
import com.codewild.food.shared.audit.AuditService;
import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import com.codewild.food.shared.events.DomainEvent;
import com.codewild.food.shared.events.OutboxService;
import com.codewild.food.shared.security.Permission;
import com.codewild.food.shared.security.PermissionEvaluator;
import com.codewild.food.shared.security.SecurityUtils;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
  private final VendorRepository vendors;
  private final AuditService audit;
  private final OutboxService outbox;

  public AdminService(VendorRepository vendors, AuditService audit, OutboxService outbox) {
    this.vendors = vendors; this.audit = audit; this.outbox = outbox;
  }

  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPS_ADMIN')")
  @Transactional
  public String decideVendor(String vendorId, boolean approve, String reason) {
    // Permission-level gate (role alone is insufficient): VENDOR_APPROVE.
    if (!PermissionEvaluator.hasPermission(SecurityUtils.currentUser(), Permission.VENDOR_APPROVE)
        && !SecurityUtils.hasRole("SUPER_ADMIN") && !SecurityUtils.hasRole("ADMIN")) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, "Missing VENDOR_APPROVE permission");
    }
    UUID vid;
    try {
      vid = UUID.fromString(vendorId);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, "Vendor not found");
    }
    var v = vendors.findById(vid)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Vendor not found"));
    v.setStatus(approve ? "APPROVED" : "REJECTED");
    vendors.save(v);
    audit.log(SecurityUtils.currentUserId(), approve ? "VENDOR_APPROVE" : "VENDOR_REJECT",
        "VendorProfile", vendorId, reason);
    outbox.save(DomainEvent.of(approve ? "VendorApproved" : "VendorRejected",
        "VendorProfile", vendorId, "{\"reason\":\"" + reason + "\"}"));
    return v.getStatus();
  }
}
