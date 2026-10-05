package com.codewild.food.serviceability.api;

import com.codewild.food.serviceability.api.ServiceabilityDtos.*;
import com.codewild.food.serviceability.application.ServiceabilityService;
import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/serviceability")
public class ServiceabilityController {
  private final ServiceabilityService service;
  public ServiceabilityController(ServiceabilityService service) { this.service = service; }

  @PostMapping("/check")
  public ApiResponse<CheckResponse> check(@Valid @RequestBody CheckRequest req) {
    return ApiResponse.ok(service.check(req), MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }
}
