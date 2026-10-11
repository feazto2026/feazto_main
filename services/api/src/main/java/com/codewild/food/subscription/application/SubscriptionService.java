package com.codewild.food.subscription.application;

import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import com.codewild.food.shared.events.DomainEvent;
import com.codewild.food.shared.events.OutboxService;
import com.codewild.food.shared.idempotency.IdempotencyService;
import com.codewild.food.customer.domain.CustomerProfile;
import com.codewild.food.customer.infrastructure.CustomerProfileRepository;
import com.codewild.food.shared.security.PlatformUser;
import com.codewild.food.shared.security.SecurityUtils;
import com.codewild.food.subscription.api.SubscriptionDtos.*;
import com.codewild.food.subscription.domain.Subscription;
import com.codewild.food.subscription.infrastructure.SubscriptionRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {
  private final SubscriptionRepository subs;
  private final IdempotencyService idempotency;
  private final OutboxService outbox;
  private final CustomerProfileRepository profiles;

  public SubscriptionService(SubscriptionRepository subs, IdempotencyService idempotency, OutboxService outbox,
                             CustomerProfileRepository profiles) {
    this.subs = subs; this.idempotency = idempotency; this.outbox = outbox; this.profiles = profiles;
  }

  /** subscriptions.customer_id REFERENCES customer_profiles(id) — never platform_users.id. */
  private UUID resolveCustomerProfileId() {
    PlatformUser me = SecurityUtils.currentUser();
    if (me.getCustomerId() != null) {
      return me.getCustomerId();
    }
    UUID userUuid = me.getUserId();
    return profiles.findByUserId(userUuid)
        .map(CustomerProfile::getId)
        .orElseGet(() -> profiles.save(new CustomerProfile(userUuid, "")).getId());
  }

  private static void requireSubscriptionOwnerOrAdmin(PlatformUser me, UUID subscriptionCustomerId) {
    if (SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasRole("SUPER_ADMIN")) {
      return;
    }
    // New rows: customer_profiles.id; legacy rows: platform_users.id.
    if (subscriptionCustomerId != null && me != null
        && (Objects.equals(subscriptionCustomerId, me.getCustomerId())
            || Objects.equals(subscriptionCustomerId, me.getUserId()))) {
      return;
    }
    throw new AccessDeniedException("Not owner");
  }

  private static String str(UUID v) {
    return v == null ? null : v.toString();
  }

  @Transactional
  public SubscriptionResponse create(CreateSubscription req, String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var existing = subs.findByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) {
        var e = existing.get();
        return new SubscriptionResponse(str(e.getId()), e.getStatus(), str(e.getCustomerId()));
      }
      if (idempotency.isDuplicate("subscription", idempotencyKey))
        throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT, "Duplicate subscription request");
    }
    Subscription s = new Subscription();
    s.setCustomerId(resolveCustomerProfileId());
    s.setVendorId(req.vendorId());
    s.setMealPlanId(req.mealPlanId());
    s.setIdempotencyKey(idempotencyKey);
    Subscription saved = subs.save(s);
    outbox.save(DomainEvent.of("SubscriptionActivated", "Subscription", str(saved.getId()), "{}"));
    return new SubscriptionResponse(str(saved.getId()), saved.getStatus(), str(saved.getCustomerId()));
  }

  @Transactional
  public SubscriptionResponse updateStatus(String id, String status) {
    UUID sid;
    try {
      sid = UUID.fromString(id);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, "Subscription not found");
    }
    Subscription s = subs.findById(sid)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Subscription not found"));
    requireSubscriptionOwnerOrAdmin(SecurityUtils.currentUser(), s.getCustomerId());
    s.setStatus(status);
    Subscription saved = subs.save(s);
    outbox.save(DomainEvent.of("Subscription" + status, "Subscription", str(saved.getId()), "{}"));
    return new SubscriptionResponse(str(saved.getId()), saved.getStatus(), str(saved.getCustomerId()));
  }
}
