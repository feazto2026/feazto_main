package com.codewild.food.subscription.api;

import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import com.codewild.food.subscription.api.SubscriptionDtos.*;
import com.codewild.food.subscription.application.SubscriptionOrderGenerator;
import com.codewild.food.subscription.application.SubscriptionService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
  private final SubscriptionService service;
  private final SubscriptionOrderGenerator generator;

  public SubscriptionController(SubscriptionService service, SubscriptionOrderGenerator generator) {
    this.service = service; this.generator = generator;
  }

  @PostMapping
  public ApiResponse<SubscriptionResponse> create(@Valid @RequestBody CreateSubscription req,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ApiResponse.ok(service.create(req, key), "Subscription created", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  @PostMapping("/{id}/pause")
  public ApiResponse<SubscriptionResponse> pause(@PathVariable String id) {
    return ApiResponse.ok(service.updateStatus(id, "PAUSED"), "Paused", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  @PostMapping("/{id}/cancel")
  public ApiResponse<SubscriptionResponse> cancel(@PathVariable String id) {
    return ApiResponse.ok(service.updateStatus(id, "CANCELLED"), "Cancelled", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  @PostMapping("/{id}/generate")
  public ApiResponse<String> generate(@PathVariable String id, @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().toString()}") String date) {
    return ApiResponse.ok(generator.generateFor(id, date), "Generation key", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }
}
