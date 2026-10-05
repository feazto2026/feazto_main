package com.codewild.food.operations.api;

import com.codewild.food.operations.api.OperationsDtos.*;
import com.codewild.food.operations.application.AdminService;
import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final AdminService service;
  public AdminController(AdminService service) { this.service = service; }

  @PostMapping("/vendors/decide")
  public ApiResponse<MessageResponse> decide(@RequestBody VendorDecision req) {
    String status = service.decideVendor(req.vendorId(), req.approve(), req.reason());
    return ApiResponse.ok(new MessageResponse("Vendor " + status),
        MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }
}
