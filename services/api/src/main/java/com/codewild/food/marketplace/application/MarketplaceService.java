package com.codewild.food.marketplace.application;

import com.codewild.food.marketplace.api.MarketplaceDtos.*;
import com.codewild.food.marketplace.domain.MenuItem;
import com.codewild.food.marketplace.domain.VendorProfile;
import com.codewild.food.marketplace.infrastructure.MenuItemRepository;
import com.codewild.food.marketplace.infrastructure.VendorRepository;
import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import com.codewild.food.shared.events.DomainEvent;
import com.codewild.food.shared.events.OutboxService;
import com.codewild.food.shared.security.PermissionEvaluator;
import com.codewild.food.shared.security.PlatformUser;
import com.codewild.food.shared.security.SecurityUtils;
import com.codewild.food.shared.security.VendorRiderApprovalGate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketplaceService {
  private final VendorRepository vendors;
  private final MenuItemRepository items;
  private final OutboxService outbox;

  public MarketplaceService(VendorRepository vendors, MenuItemRepository items, OutboxService outbox) {
    this.vendors = vendors; this.items = items; this.outbox = outbox;
  }

  @Transactional
  public VendorResponse registerVendor(RegisterVendor req) {
    UUID userId = SecurityUtils.currentUserUuid();
    vendors.findByUserId(userId).ifPresent(v -> {
      throw new BusinessException(ErrorCodes.CONFLICT, "Vendor profile already exists");
    });
    VendorProfile saved = vendors.save(new VendorProfile(userId, req.kitchenName(), req.city()));
    outbox.save(DomainEvent.of("VendorSubmitted", "VendorProfile", saved.getIdAsString(), "{}"));
    return new VendorResponse(saved.getIdAsString(), saved.getKitchenName(), saved.getCity(), saved.getStatus());
  }

  public List<VendorResponse> discoverVendors() {
    return vendors.findApprovedActive().stream()
        .map(v -> new VendorResponse(v.getIdAsString(), v.getKitchenName(), v.getCity(), v.getStatus()))
        .toList();
  }

  public List<MenuItemResponse> vendorMenu(String vendorId) {
    if (vendorId == null || vendorId.isBlank()) {
      return List.of();
    }
    return items.findByVendorIdAndPublishedTrue(vendorId).stream()
        .map(i -> new MenuItemResponse(i.getIdAsString(), i.getVendorIdAsString(), i.getName(), i.getPrice()))
        .toList();
  }

  @Transactional
  public MenuItemResponse addMenuItem(String vendorId, CreateMenuItem req) {
    PlatformUser me = SecurityUtils.currentUser();
    // State gate first: only APPROVED vendors may trade (403 VENDOR_* on failure).
    try {
      VendorRiderApprovalGate.requireApprovedVendor(me);
    } catch (VendorRiderApprovalGate.GateException e) {
      throw new BusinessException(e.getCode(), e.getMessage());
    }
    // Ownership + permission gate: own vendorId + MENU_MANAGE (fail closed).
    UUID vendorUuid = parseUuidOrNull(vendorId);
    if (vendorUuid == null || !PermissionEvaluator.canManageVendorMenu(me, vendorUuid)) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, "Only the approved vendor may manage this menu");
    }
    VendorProfile v = vendors.findById(vendorUuid)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Vendor not found"));
    MenuItem saved = items.save(new MenuItem(vendorId, req.name(), req.price()));
    outbox.save(DomainEvent.of("MenuPublished", "MenuItem", saved.getIdAsString(), "{}"));
    return new MenuItemResponse(saved.getIdAsString(), saved.getVendorIdAsString(),
        saved.getName(), saved.getPrice());
  }

  private static UUID parseUuidOrNull(String raw) {
    try {
      return raw == null ? null : UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
