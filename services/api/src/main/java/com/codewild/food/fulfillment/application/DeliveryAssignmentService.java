package com.codewild.food.fulfillment.application;

import com.codewild.food.fulfillment.api.FulfillmentDtos.*;
import com.codewild.food.fulfillment.domain.Delivery;
import com.codewild.food.fulfillment.domain.DeliveryAssignment;
import com.codewild.food.fulfillment.domain.DeliveryStateMachine;
import com.codewild.food.fulfillment.domain.RiderProfile;
import com.codewild.food.fulfillment.infrastructure.DeliveryAssignmentRepository;
import com.codewild.food.fulfillment.infrastructure.DeliveryRepository;
import com.codewild.food.fulfillment.infrastructure.RiderAvailabilityRepository;
import com.codewild.food.fulfillment.infrastructure.RiderRepository;
import com.codewild.food.marketplace.infrastructure.ServiceZoneRepository;
import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import com.codewild.food.shared.events.DomainEvent;
import com.codewild.food.shared.events.OutboxService;
import com.codewild.food.shared.idempotency.IdempotencyService;
import com.codewild.food.shared.security.PermissionEvaluator;
import com.codewild.food.shared.security.PlatformUser;
import com.codewild.food.shared.security.SecurityUtils;
import com.codewild.food.shared.security.VendorRiderApprovalGate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryAssignmentService {
  private final DeliveryRepository deliveries;
  private final DeliveryAssignmentRepository assignments;
  private final RiderRepository riders;
  private final RiderAvailabilityRepository availability;
  private final ServiceZoneRepository zones;
  private final QrValidationService qr;
  private final IdempotencyService idempotency;
  private final OutboxService outbox;

  public DeliveryAssignmentService(DeliveryRepository deliveries,
      DeliveryAssignmentRepository assignments,
      RiderRepository riders,
      RiderAvailabilityRepository availability,
      ServiceZoneRepository zones,
      QrValidationService qr, IdempotencyService idempotency, OutboxService outbox) {
    this.deliveries = deliveries; this.assignments = assignments;
    this.riders = riders; this.availability = availability; this.zones = zones;
    this.qr = qr; this.idempotency = idempotency; this.outbox = outbox;
  }

  private static String str(UUID v) {
    return v == null ? null : v.toString();
  }

  private static DeliveryResponse toResponse(Delivery d) {
    return new DeliveryResponse(str(d.getId()), str(d.getOrderId()), str(d.getRiderId()),
        d.getStatus() == null ? null : d.getStatus().name());
  }

  @Transactional
  public DeliveryResponse createForOrder(String orderId) {
    UUID orderUuid = parseUuidOrThrow(orderId, "Delivery not found");
    return deliveries.findByOrderId(orderUuid)
        .map(DeliveryAssignmentService::toResponse)
        .orElseGet(() -> {
          Delivery d = new Delivery(orderUuid);
          d.setPickupCode(qr.newCode());
          d.setDeliveryCode(qr.newCode());
          Delivery saved = deliveries.save(d);
          outbox.save(DomainEvent.of("DeliveryCreated", "Delivery", str(saved.getId()), "{}"));
          return toResponse(saved);
        });
  }

  /**
   * Dispatch strategy (0006 §16): zone + availability + workload inputs are stored
   * on the assignment row (assignment_reason); strategy evolves without rewriting Order.
   * Only online APPROVED/ACTIVE riders with availability today are eligible.
   */
  @Transactional
  public DeliveryResponse assign(String deliveryId, String zoneId) {
    Delivery d = deliveries.findById(parseUuidOrThrow(deliveryId, "Delivery not found"))
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Delivery not found"));
    String city = resolveCity(zoneId);
    List<RiderProfile> candidates = (city != null && !city.isBlank())
        ? riders.findDispatchCandidates(city)
        : riders.findOnlineApproved();
    if (candidates.isEmpty()) {
      candidates = riders.findOnlineApproved();
    }
    if (candidates.isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, "No eligible riders");
    }
    // Availability filter: rider must have an available window today, or no schedule
    // rows at all (fail-open for riders without a posted schedule).
    int weekday = LocalDate.now().getDayOfWeek().getValue() % 7; // 0=Sunday..6=Saturday
    List<RiderProfile> filtered = new ArrayList<>();
    for (RiderProfile r : candidates) {
      var todayRows = availability.findByRiderIdAndWeekdayAndAvailableTrue(r.getId(), weekday);
      if (!todayRows.isEmpty()) {
        filtered.add(r);
      } else if (availability.findByRiderId(r.getId()).isEmpty()) {
        filtered.add(r);
      }
    }
    if (!filtered.isEmpty()) {
      candidates = filtered;
    }
    // Workload ordering: findDispatchCandidates already orders by total_deliveries asc;
    // keep first eligible (lowest workload). Future: distance scoring.
    var chosen = candidates.get(0);
    d.setRiderId(chosen.getId());
    // AVAILABLE -> ASSIGNED is the canonical dispatch edge; tolerate already-assigned.
    if (d.getStatus() != DeliveryStateMachine.State.ASSIGNED) {
      d.transitionTo(DeliveryStateMachine.State.ASSIGNED);
    }
    Delivery saved = deliveries.save(d);
    // Persist the dispatch offer (0006 delivery_assignments; UNIQUE delivery+rider+attempt).
    var prior = assignments.findByDeliveryIdOrderByAttemptNoDesc(saved.getId());
    int attemptNo = prior.isEmpty() ? 1 : prior.get(0).getAttemptNo() + 1;
    String reason = "{\"zone\":\"" + (zoneId == null ? "" : zoneId)
        + "\",\"city\":\"" + (city == null ? "" : city)
        + "\",\"weekday\":" + weekday + "}";
    try {
      assignments.save(new DeliveryAssignment(saved.getId(), chosen.getId(), attemptNo,
          "asg:" + saved.getId() + ":" + attemptNo + ":" + chosen.getId(), reason));
    } catch (Exception dup) {
      // Idempotent retry on UNIQUE(delivery, rider, attempt) — offer already recorded.
    }
    outbox.save(DomainEvent.of("RiderAssigned", "Delivery", str(saved.getId()),
        "{\"rider\":\"" + str(chosen.getId()) + "\"}"));
    return toResponse(saved);
  }

  private String resolveCity(String zoneId) {
    if (zoneId == null || zoneId.isBlank()) {
      return null;
    }
    try {
      UUID zid = UUID.fromString(zoneId);
      return zones.findById(zid).map(z -> z.getCity()).orElse(null);
    } catch (IllegalArgumentException notUuid) {
      // Caller passed a city/zone code directly; treat as city for candidate scan.
      return zoneId;
    }
  }

  @Transactional
  public DeliveryResponse confirmPickup(String deliveryId, String code, String idempotencyKey) {
    if (idempotencyKey != null && idempotency.isDuplicate("pickup", idempotencyKey))
      throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT, "Duplicate pickup confirmation");
    Delivery d = deliveries.findById(parseUuidOrThrow(deliveryId, "Delivery not found"))
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Delivery not found"));
    // AuthZ: assigned APPROVED rider only (or privileged admin). Fail closed.
    PlatformUser me = SecurityUtils.currentUser();
    try {
      if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasRole("SUPER_ADMIN")) {
        VendorRiderApprovalGate.requireApprovedRider(me);
      }
    } catch (VendorRiderApprovalGate.GateException e) {
      throw new BusinessException(e.getCode(), e.getMessage());
    }
    UUID assignedRider = d.getRiderId();
    boolean assigned = assignedRider != null
        && PermissionEvaluator.canRiderUpdateDelivery(me, assignedRider);
    boolean privileged = SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasRole("SUPER_ADMIN");
    if (!assigned && !privileged) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, "Only assigned rider can confirm pickup");
    }
    if (!isPickupValid(d, code)) {
      throw new BusinessException(ErrorCodes.VALIDATION, "Invalid pickup code");
    }
    if (d.getStatus() == DeliveryStateMachine.State.ASSIGNED) {
      d.transitionTo(DeliveryStateMachine.State.ACCEPTED);
    }
    if (d.getStatus() != DeliveryStateMachine.State.PICKED_UP) {
      d.transitionTo(DeliveryStateMachine.State.PICKED_UP);
    }
    Delivery saved = deliveries.save(d);
    outbox.save(DomainEvent.of("OrderPickedUp", "Delivery", str(saved.getId()), "{}"));
    return toResponse(saved);
  }

  @Transactional
  public DeliveryResponse confirmDelivery(String deliveryId, String code, String idempotencyKey) {
    if (idempotencyKey != null && idempotency.isDuplicate("delivery", idempotencyKey))
      throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT, "Duplicate delivery confirmation");
    Delivery d = deliveries.findById(parseUuidOrThrow(deliveryId, "Delivery not found"))
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Delivery not found"));
    PlatformUser me = SecurityUtils.currentUser();
    try {
      if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasRole("SUPER_ADMIN")) {
        VendorRiderApprovalGate.requireApprovedRider(me);
      }
    } catch (VendorRiderApprovalGate.GateException e) {
      throw new BusinessException(e.getCode(), e.getMessage());
    }
    UUID assignedRider = d.getRiderId();
    boolean assigned = assignedRider != null
        && PermissionEvaluator.canRiderUpdateDelivery(me, assignedRider);
    boolean privileged = SecurityUtils.hasRole("ADMIN") || SecurityUtils.hasRole("SUPER_ADMIN");
    if (!assigned && !privileged) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, "Only assigned rider can confirm delivery");
    }
    if (!isDeliveryValid(d, code)) {
      throw new BusinessException(ErrorCodes.VALIDATION, "Invalid delivery code");
    }
    if (d.getStatus() == DeliveryStateMachine.State.PICKED_UP) {
      d.transitionTo(DeliveryStateMachine.State.EN_ROUTE);
    }
    if (d.getStatus() != DeliveryStateMachine.State.DELIVERED) {
      d.transitionTo(DeliveryStateMachine.State.DELIVERED);
    }
    Delivery saved = deliveries.save(d);
    outbox.save(DomainEvent.of("OrderDelivered", "Delivery", str(saved.getId()), "{}"));
    return toResponse(saved);
  }

  /**
   * Hash-based pickup check (0006 §12: no plaintext OTP stored) with expiry.
   * Accepts pickup_code_hash OR pickup_qr_token_hash; expired codes always fail.
   */
  private boolean isPickupValid(Delivery d, String code) {
    if (code == null || code.isBlank()) {
      return false;
    }
    String candidate = Delivery.hash(code);
    String hash = d.getPickupCodeHash();
    if (hash != null && candidate != null && candidate.equalsIgnoreCase(hash)) {
      Instant exp = d.getPickupCodeExpiresAt();
      if (exp == null || Instant.now().isBefore(exp)) {
        return true;
      }
      return false; // hash matched but expired → fail closed (no legacy fallback)
    }
    String qrHash = d.getPickupQrTokenHash();
    if (qrHash != null && candidate != null && candidate.equalsIgnoreCase(qrHash)) {
      Instant exp = d.getPickupQrExpiresAt();
      if (exp == null || Instant.now().isBefore(exp)) {
        return true;
      }
      return false;
    }
    if (hash != null || qrHash != null) {
      return false; // hashed rows never fall back to plaintext compare
    }
    // Legacy/plaintext bridge (pre-hash rows): delegate to the stub validator.
    return qr.validatePickup(d.getPickupCode(), code);
  }

  private boolean isDeliveryValid(Delivery d, String code) {
    if (code == null || code.isBlank()) {
      return false;
    }
    String hash = d.getDeliveryCodeHash();
    if (hash != null) {
      Instant exp = d.getDeliveryCodeExpiresAt();
      if (exp != null && Instant.now().isAfter(exp)) {
        return false;
      }
      String candidate = Delivery.hash(code);
      if (candidate != null && candidate.equalsIgnoreCase(hash)) {
        return true;
      }
      return false; // hashed rows never fall back to plaintext compare
    }
    return qr.validateDelivery(d.getDeliveryCode(), code);
  }

  private static UUID parseUuidOrThrow(String raw, String notFoundMessage) {
    try {
      return UUID.fromString(raw);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, notFoundMessage);
    }
  }
}
