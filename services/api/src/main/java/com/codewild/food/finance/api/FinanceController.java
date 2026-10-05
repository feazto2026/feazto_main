package com.codewild.food.finance.api;

import com.codewild.food.finance.api.FinanceDtos.*;
import com.codewild.food.finance.application.FinanceService;
import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {
  private final FinanceService service;
  public FinanceController(FinanceService service) { this.service = service; }

  @PostMapping("/payouts")
  public ApiResponse<PayoutResponse> payout(@RequestBody CreatePayout req) {
    return ApiResponse.ok(service.createPayout(req), "Payout created", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }
}
