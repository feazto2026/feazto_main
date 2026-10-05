package com.codewild.food.commerce.application;

import com.codewild.food.commerce.api.CommerceDtos.*;
import com.codewild.food.commerce.domain.*;
import com.codewild.food.commerce.infrastructure.OrderRepository;
import com.codewild.food.commerce.infrastructure.PaymentRepository;
import com.codewild.food.marketplace.domain.VendorProfile;
import com.codewild.food.marketplace.domain.VendorSlot;
import com.codewild.food.marketplace.infrastructure.MenuItemRepository;
import com.codewild.food.marketplace.infrastructure.VendorRepository;
import com.codewild.food.marketplace.infrastructure.VendorServiceZoneRepository;
import com.codewild.food.marketplace.infrastructure.VendorSlotRepository;
import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import com.codewild.food.shared.events.DomainEvent;
import com.codewild.food.shared.events.OutboxService;
import com.codewild.food.shared.idempotency.IdempotencyService;
import com.codewild.food.shared.security.PermissionEvaluator;
import com.codewild.food.shared.security.PlatformUser;
import com.codewild.food.shared.security.SecurityUtils;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final OrderRepository orders;
  private final MenuItemRepository menuItems;
  private final VendorRepository vendors;
  private final VendorSlotRepository vendorSlots;
  private final VendorServiceZoneRepository vendorZones;
  private final IdempotencyService idempotency;
  private final OutboxService outbox;

  public OrderService(OrderRepository orders, MenuItemRepository menuItems,
                      VendorRepository vendors, VendorSlotRepository vendorSlots,
                      VendorServiceZoneRepository vendorZones,
                      IdempotencyService idempotency, OutboxService outbox) {
    this.orders = orders; this.menuItems = menuItems;
    this.vendors = vendors; this.vendorSlots = vendorSlots; this.vendorZones = vendorZones;
    this.idempotency = idempotency; this.outbox = outbox;
  }

  @Transactional
  public OrderResponse createOrder(CreateOrder req, String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var existing = orders.findByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) {
        var e = existing.get();
        return new OrderResponse(e.getId().toString(), e.getStatus().name(),
            String.valueOf(e.getTotalAmount()));
      }
      if (idempotency.isDuplicate("order", idempotencyKey))
        throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT, "Duplicate order request in progress");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      idempotencyKey = "ord:" + UUID.randomUUID();
    }

    // ---- 1. Vendor must be APPROVED + active (Supabase vendors.status lifecycle) ----
    UUID vendorUuid = parseUuidOrThrow(req.vendorId(), "Vendor not found");
    VendorProfile vendor = vendors.findById(vendorUuid)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Vendor not found"));
    if (!vendor.isAvailable()) {
      throw new BusinessException(ErrorCodes.VALIDATION,
          "Vendor not available (status=" + vendor.getStatus() + ")");
    }

    // ---- 2. Serviceability: vendor must serve at least one active zone ----
    var zones = vendorZones.findByPkVendorIdAndActiveTrue(vendorUuid);
    if (zones.isEmpty()) {
      throw new BusinessException(ErrorCodes.VALIDATION, "Vendor not serviceable in your zone");
    }

    // ---- 3. Slot capacity (DB authoritative; row-lock when slot known) ----
    // CreateOrder DTO carries no slot yet; guard on the vendor's available slots so
    // capacity exhaustion fails fast instead of violating vendor_slots_capacity_check later.
    var availableSlots = vendorSlots.findByVendorIdAndAvailableTrue(vendorUuid);
    if (!availableSlots.isEmpty()) {
      boolean anyRemaining = availableSlots.stream().anyMatch(vs -> vs.remaining() > 0 && vs.isAvailable());
      if (!anyRemaining) {
        throw new BusinessException(ErrorCodes.VALIDATION, "Slot capacity exhausted for vendor");
      }
    }

    UUID customerUuid = SecurityUtils.currentUserUuid();
    OrderEntity order = new OrderEntity();
    order.setCustomerId(customerUuid);
    order.setVendorId(vendorUuid);
    order.setIdempotencyKey(idempotencyKey);
    if (req.addressId() != null && !req.addressId().isBlank()) {
      order.setAddressId(req.addressId());
    }

    // ---- 4. Price + snapshot freeze (§11): items carry frozen unit prices ----
    long subtotalPaise = 0;
    BigDecimal total = BigDecimal.ZERO;
    for (var line : req.lines()) {
      UUID menuUuid = parseUuidOrNull(line.menuItemId());
      if (menuUuid == null) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, "Menu item not found: " + line.menuItemId());
      }
      var menu = menuItems.findById(menuUuid)
          .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Menu item not found: " + line.menuItemId()));
      if (!vendorUuid.equals(menu.getVendorId())) {
        throw new BusinessException(ErrorCodes.VALIDATION, "Menu item does not belong to vendor");
      }
      if (!menu.isPublished()) {
        throw new BusinessException(ErrorCodes.VALIDATION, "Menu item not available: " + menu.getName());
      }
      OrderItem item = new OrderItem(menu.getIdAsString(), menu.getName(), menu.getPrice(), line.quantity());
      item.setVendorId(vendorUuid);
      order.getItems().add(item);
      subtotalPaise += item.getLineTotalPaise();
      total = total.add(menu.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
    }
    order.setSubtotalPaise(subtotalPaise);
    order.setDiscountPaise(0);
    order.setTaxPaise(0);
    order.setDeliveryFeePaise(0);
    order.setPlatformFeePaise(0);
    order.setPackagingFeePaise(0);
    // Commission snapshot frozen at creation from vendor master.
    int bps = Math.max(0, Math.min(10000, vendor.getCommissionBps()));
    order.setCommissionBpsSnapshot(bps);
    long commission = subtotalPaise * bps / 10000L;
    order.setPlatformCommissionPaiseSnapshot(commission);
    order.setVendorPayoutPaiseSnapshot(Math.max(0, subtotalPaise - commission));
    order.setItemCount(order.getItems().size());
    if (order.getDeliveryAddressSnapshot() == null) {
      order.setDeliveryAddressSnapshot("{}");
    }
    // DB CHECK backstop: total = subtotal - discount + tax + fees.
    order.recomputeTotalOrThrow();
    order.transitionTo(OrderStateMachine.State.PAYMENT_PENDING);
    OrderEntity saved = orders.save(order); // unique constraint on idempotencyKey is DB backstop
    outbox.save(DomainEvent.of("OrderCreated", "Order", saved.getId().toString(), "{}"));
    return new OrderResponse(saved.getId().toString(), saved.getStatus().name(),
        String.valueOf(saved.getTotalAmount()));
  }

  @Transactional
  public OrderResponse transition(String orderId, OrderStateMachine.State to) {
    UUID oid = parseUuidOrThrow(orderId);
    OrderEntity order = orders.findById(oid)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Order not found"));
    // AuthZ: owner (customer) OR own approved vendor OR privileged admin with ORDER_VIEW.
    PlatformUser me = SecurityUtils.currentUser();
    UUID ownerUuid = order.getCustomerId();
    UUID vendorUuid = order.getVendorId();
    boolean ownerOrPrivileged = ownerUuid != null && PermissionEvaluator.canAccessOrder(me, ownerUuid);
    boolean vendorOfOrder = vendorUuid != null && PermissionEvaluator.canVendorAccessOrder(me, vendorUuid);
    if (!ownerOrPrivileged && !vendorOfOrder) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, "You do not have access to this order");
    }
    order.transitionTo(to);
    OrderEntity saved = orders.save(order);
    outbox.save(DomainEvent.of("Order" + to.name(), "Order", saved.getId().toString(), "{}"));
    return new OrderResponse(saved.getId().toString(), saved.getStatus().name(),
        String.valueOf(saved.getTotalAmount()));
  }

  public OrderResponse get(String orderId) {
    UUID oid = parseUuidOrThrow(orderId);
    OrderEntity order = orders.findById(oid)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Order not found"));
    PlatformUser me = SecurityUtils.currentUser();
    UUID ownerUuid = order.getCustomerId();
    UUID vendorUuid = order.getVendorId();
    boolean allowed = (ownerUuid != null && PermissionEvaluator.canAccessOrder(me, ownerUuid))
        || (vendorUuid != null && PermissionEvaluator.canVendorAccessOrder(me, vendorUuid));
    if (!allowed) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, "You do not have access to this order");
    }
    return new OrderResponse(order.getId().toString(), order.getStatus().name(),
        String.valueOf(order.getTotalAmount()));
  }

  private static UUID parseUuidOrNull(String raw) {
    try {
      return raw == null ? null : UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static UUID parseUuidOrThrow(String raw) {
    try {
      return UUID.fromString(raw);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, "Order not found");
    }
  }

  private static UUID parseUuidOrThrow(String raw, String message) {
    try {
      return UUID.fromString(raw);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, message);
    }
  }
}
