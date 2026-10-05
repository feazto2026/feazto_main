package com.codewild.food.customer.api;

import com.codewild.food.customer.api.CustomerDtos.*;
import com.codewild.food.customer.application.CustomerService;
import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
  private final CustomerService service;
  public CustomerController(CustomerService service) { this.service = service; }

  @GetMapping("/me")
  public ApiResponse<ProfileResponse> me() {
    return ApiResponse.ok(service.myProfile(), MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  @PutMapping("/me")
  public ApiResponse<ProfileResponse> upsert(@Valid @RequestBody UpsertProfile req) {
    return ApiResponse.ok(service.upsertMyProfile(req), "Profile saved", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }
}
