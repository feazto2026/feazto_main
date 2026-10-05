package com.codewild.food.marketplace.api;

import com.codewild.food.marketplace.api.MarketplaceDtos.*;
import com.codewild.food.marketplace.application.MarketplaceService;
import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class MarketplaceController {
  private final MarketplaceService service;
  public MarketplaceController(MarketplaceService service) { this.service = service; }

  @PostMapping("/vendors")
  public ApiResponse<VendorResponse> register(@Valid @RequestBody RegisterVendor req) {
    return ApiResponse.ok(service.registerVendor(req), "Vendor submitted", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  @GetMapping("/vendors")
  public ApiResponse<List<VendorResponse>> discover() {
    return ApiResponse.ok(service.discoverVendors(), MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  @GetMapping("/vendors/{id}/menu")
  public ApiResponse<List<MenuItemResponse>> menu(@PathVariable String id) {
    return ApiResponse.ok(service.vendorMenu(id), MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  @PostMapping("/vendors/{id}/menu")
  public ApiResponse<MenuItemResponse> addItem(@PathVariable String id, @Valid @RequestBody CreateMenuItem req) {
    return ApiResponse.ok(service.addMenuItem(id, req), "Item added", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }
}
