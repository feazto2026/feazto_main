package com.codewild.food.serviceability.application;

import com.codewild.food.customer.domain.CustomerAddress;
import com.codewild.food.customer.infrastructure.CustomerAddressRepository;
import com.codewild.food.marketplace.domain.ServiceZone;
import com.codewild.food.marketplace.domain.VendorProfile;
import com.codewild.food.marketplace.infrastructure.ServiceZoneRepository;
import com.codewild.food.marketplace.infrastructure.VendorRepository;
import com.codewild.food.marketplace.infrastructure.VendorServiceZoneRepository;
import com.codewild.food.marketplace.infrastructure.VendorSlotRepository;
import com.codewild.food.serviceability.api.ServiceabilityDtos.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Domain capability (not a UI filter): vendor availability + delivery-zone
 * coverage + slot capacity, all read from Supabase truth (0002
 * {@code addresses}, 0003 {@code vendors}/{@code service_zones}/
 * {@code vendor_service_zones}/{@code vendor_slots}).
 *
 * <p>Matching order per zone: postal-code &gt; city &gt; geo-radius.
 * Zones are data — no city is hard-coded. Delivery fee prefers the
 * vendor-specific override ({@code vendor_service_zones.custom_delivery_fee_paise})
 * and falls back to the zone default.
 */
@Service
public class ServiceabilityService {
  private final CustomerAddressRepository addresses;
  private final VendorRepository vendors;
  private final VendorServiceZoneRepository vendorZones;
  private final ServiceZoneRepository zones;
  private final VendorSlotRepository vendorSlots;

  public ServiceabilityService(CustomerAddressRepository addresses,
      VendorRepository vendors,
      VendorServiceZoneRepository vendorZones,
      ServiceZoneRepository zones,
      VendorSlotRepository vendorSlots) {
    this.addresses = addresses;
    this.vendors = vendors;
    this.vendorZones = vendorZones;
    this.zones = zones;
    this.vendorSlots = vendorSlots;
  }

  public CheckResponse check(CheckRequest req) {
    if (req.addressId() == null || req.addressId().isBlank()) {
      return new CheckResponse(false, req.vendorId(), req.slot(), null, null, "Address required");
    }
    UUID addressId = parseUuid(req.addressId());
    Optional<CustomerAddress> addrOpt = addressId == null
        ? Optional.empty()
        : addresses.findById(addressId);
    if (addrOpt.isEmpty()) {
      return new CheckResponse(false, req.vendorId(), req.slot(), null, null, "Unknown address");
    }
    CustomerAddress addr = addrOpt.get();
    if (req.vendorId() != null && !req.vendorId().isBlank()) {
      return checkVendor(addr, req.vendorId(), req.slot());
    }
    // Platform-wide probe (no vendor): any active zone covering the address?
    Optional<ServiceZone> match = bestZoneForAddress(null, addr);
    if (match.isEmpty()) {
      return new CheckResponse(false, null, req.slot(), null, null, "Area not serviceable yet");
    }
    return new CheckResponse(true, null, req.slot(), "45-60 min",
        paiseToRupees(match.get().getDeliveryFeePaise()), null);
  }

  private CheckResponse checkVendor(CustomerAddress addr, String vendorIdRaw, String slot) {
    UUID vendorUuid = parseUuid(vendorIdRaw);
    Optional<VendorProfile> vOpt = vendorUuid == null
        ? Optional.empty()
        : vendors.findById(vendorUuid);
    if (vOpt.isEmpty() || !vOpt.get().isAvailable()) {
      return new CheckResponse(false, vendorIdRaw, slot, null, null, "Vendor not available");
    }
    Optional<ServiceZone> match = bestZoneForAddress(vendorUuid, addr);
    if (match.isEmpty()) {
      return new CheckResponse(false, vendorIdRaw, slot, null, null,
          "Vendor does not deliver to this address");
    }
    // Slot capacity (DB authoritative): a named slot must have remaining capacity.
    if (slot != null && !slot.isBlank()) {
      UUID slotUuid = parseUuid(slot);
      if (slotUuid != null) {
        var vs = vendorSlots.findByVendorIdAndSlotId(vendorUuid, slotUuid);
        if (vs.isPresent() && (!vs.get().isAvailable() || vs.get().remaining() <= 0)) {
          return new CheckResponse(false, vendorIdRaw, slot, null, null, "Slot full");
        }
      } else if ("FULL".equalsIgnoreCase(slot.trim())) {
        return new CheckResponse(false, vendorIdRaw, slot, null, null, "Slot full");
      }
    }
    long feePaise = match.get().getDeliveryFeePaise();
    Long override = vendorZoneFeeOverride(vendorUuid, match.get().getId());
    if (override != null) {
      feePaise = override;
    }
    return new CheckResponse(true, vendorIdRaw, slot, "45-60 min", paiseToRupees(feePaise), null);
  }

  /** Vendor-scoped zones when {@code vendorId} is set, else platform-wide active zones. */
  private Optional<ServiceZone> bestZoneForAddress(UUID vendorId, CustomerAddress addr) {
    List<ServiceZone> candidates = new ArrayList<>();
    if (vendorId != null) {
      var links = vendorZones.findByPkVendorIdAndActiveTrue(vendorId);
      if (links.isEmpty()) {
        return Optional.empty();
      }
      List<UUID> zoneIds = links.stream().map(l -> l.getServiceZoneId()).toList();
      candidates.addAll(zones.findAllById(zoneIds).stream()
          .filter(ServiceZone::isActive).toList());
    } else {
      candidates.addAll(zones.findByCityAndActiveTrue(addr.getCity()));
      if (candidates.isEmpty()) {
        candidates.addAll(zones.findByActiveTrue());
      }
    }
    return candidates.stream().filter(z -> covers(z, addr)).findFirst();
  }

  /** Postal-code &gt; city &gt; geo-radius. A zone with no matching signal does not cover. */
  private boolean covers(ServiceZone z, CustomerAddress addr) {
    String[] pins = z.getPostalCodes();
    if (pins != null && pins.length > 0 && addr.getPostalCode() != null
        && !addr.getPostalCode().isBlank()) {
      for (String p : pins) {
        if (p != null && addr.getPostalCode().equals(p.trim())) {
          return true;
        }
      }
    }
    if (z.getCity() != null && !z.getCity().isBlank()
        && addr.getCity() != null && z.getCity().equalsIgnoreCase(addr.getCity().trim())) {
      return true;
    }
    if (z.getCenterLat() != null && z.getCenterLng() != null && z.getRadiusKm() != null
        && addr.getLatitude() != null && addr.getLongitude() != null) {
      double d = haversineKm(z.getCenterLat(), z.getCenterLng(),
          addr.getLatitude(), addr.getLongitude());
      if (d <= z.getRadiusKm().doubleValue()) {
        return true;
      }
    }
    return false;
  }

  private Long vendorZoneFeeOverride(UUID vendorId, UUID zoneId) {
    try {
      var links = vendorZones.findByPkVendorIdAndActiveTrue(vendorId);
      for (var l : links) {
        if (zoneId.equals(l.getServiceZoneId())) {
          return l.getCustomDeliveryFeePaise();
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 6371.0 * 2 * Math.asin(Math.min(1.0, Math.sqrt(a)));
  }

  private static String paiseToRupees(long paise) {
    return String.format("%d.%02d", paise / 100, Math.abs(paise % 100));
  }

  private static UUID parseUuid(String raw) {
    try {
      return raw == null ? null : UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
