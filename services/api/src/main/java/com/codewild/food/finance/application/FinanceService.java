package com.codewild.food.finance.application;

import com.codewild.food.finance.api.FinanceDtos.*;
import com.codewild.food.finance.domain.VendorPayout;
import com.codewild.food.finance.infrastructure.PayoutRepository;
import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import com.codewild.food.shared.events.DomainEvent;
import com.codewild.food.shared.events.OutboxService;
import com.codewild.food.shared.security.Permission;
import com.codewild.food.shared.security.PermissionEvaluator;
import com.codewild.food.shared.security.SecurityUtils;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {
  private final PayoutRepository payouts;
  private final OutboxService outbox;

  public FinanceService(PayoutRepository payouts, OutboxService outbox) {
    this.payouts = payouts; this.outbox = outbox;
  }

  @PreAuthorize("hasAnyRole('ADMIN','FINANCE_ADMIN','SUPER_ADMIN')")
  @Transactional
  public PayoutResponse createPayout(CreatePayout req) {
    // Permission-level gate: PAYOUT_MANAGE (SUPER_ADMIN holds all permissions).
    if (!PermissionEvaluator.hasPermission(SecurityUtils.currentUser(), Permission.PAYOUT_MANAGE)) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, "Missing PAYOUT_MANAGE permission");
    }
    // Vendor payouts are the Phase-1 path; rider payouts follow the same gate via
    // RiderPayout once the finance track wires beneficiary routing.
    UUID vendorId;
    try {
      vendorId = UUID.fromString(req.beneficiaryId());
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.VALIDATION, "Invalid beneficiary");
    }
    long grossPaise = req.amount() == null ? 0
        : req.amount().multiply(java.math.BigDecimal.valueOf(100)).longValue();
    LocalDate today = LocalDate.now();
    VendorPayout saved = payouts.save(new VendorPayout(vendorId, today, today, grossPaise,
        "vpo:" + UUID.randomUUID()));
    saved.setInitiatedBy(SecurityUtils.currentUserUuid());
    payouts.save(saved);
    outbox.save(DomainEvent.of("VendorPayoutCreated", "VendorPayout", saved.getIdAsString(), "{}"));
    return new PayoutResponse(saved.getIdAsString(), saved.getStatus());
  }
}
