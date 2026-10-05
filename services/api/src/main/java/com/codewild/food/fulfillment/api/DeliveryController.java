package com.codewild.food.fulfillment.api;

import com.codewild.food.fulfillment.api.FulfillmentDtos.*;
import com.codewild.food.fulfillment.application.DeliveryAssignmentService;
import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {
  private final DeliveryAssignmentService service;
  public DeliveryController(DeliveryAssignmentService service) { this.service = service; }
  private String rid() { return MDC.get(RequestIdFilter.REQUEST_ID_MDC); }

  @PostMapping("/for-order/{orderId}")
  public ApiResponse<DeliveryResponse> create(@PathVariable String orderId) {
    return ApiResponse.ok(service.createForOrder(orderId), "Delivery created", rid());
  }

  @PostMapping("/{id}/assign")
  public ApiResponse<DeliveryResponse> assign(@PathVariable String id,
      @RequestParam(required = false) String zoneId) {
    return ApiResponse.ok(service.assign(id, zoneId), "Rider assigned", rid());
  }

  @PostMapping("/{id}/pickup")
  public ApiResponse<DeliveryResponse> pickup(@PathVariable String id, @RequestBody ConfirmRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ApiResponse.ok(service.confirmPickup(id, req.code(), key), "Picked up", rid());
  }

  @PostMapping("/{id}/complete")
  public ApiResponse<DeliveryResponse> complete(@PathVariable String id, @RequestBody ConfirmRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ApiResponse.ok(service.confirmDelivery(id, req.code(), key), "Delivered", rid());
  }
}
