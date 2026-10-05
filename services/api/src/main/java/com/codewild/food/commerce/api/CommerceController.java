package com.codewild.food.commerce.api;

import com.codewild.food.commerce.api.CommerceDtos.*;
import com.codewild.food.commerce.application.OrderService;
import com.codewild.food.commerce.application.PaymentService;
import com.codewild.food.commerce.domain.OrderStateMachine;
import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CommerceController {
  private final OrderService orders;
  private final PaymentService payments;

  public CommerceController(OrderService orders, PaymentService payments) {
    this.orders = orders; this.payments = payments;
  }

  private String rid() { return MDC.get(RequestIdFilter.REQUEST_ID_MDC); }

  @PostMapping("/orders")
  public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrder req,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ApiResponse.ok(orders.createOrder(req, key), "Order created", rid());
  }

  @GetMapping("/orders/{id}")
  public ApiResponse<OrderResponse> getOrder(@PathVariable String id) {
    return ApiResponse.ok(orders.get(id), rid());
  }

  @PostMapping("/orders/{id}/accept")
  public ApiResponse<OrderResponse> accept(@PathVariable String id) {
    return ApiResponse.ok(orders.transition(id, OrderStateMachine.State.VENDOR_ACCEPTED), "Accepted", rid());
  }

  @PostMapping("/orders/{id}/prepare")
  public ApiResponse<OrderResponse> prepare(@PathVariable String id) {
    return ApiResponse.ok(orders.transition(id, OrderStateMachine.State.PREPARING), "Preparing", rid());
  }

  @PostMapping("/orders/{id}/ready")
  public ApiResponse<OrderResponse> ready(@PathVariable String id) {
    return ApiResponse.ok(orders.transition(id, OrderStateMachine.State.READY_FOR_PICKUP), "Ready", rid());
  }

  @PostMapping("/orders/{id}/cancel")
  public ApiResponse<OrderResponse> cancel(@PathVariable String id) {
    return ApiResponse.ok(orders.transition(id, OrderStateMachine.State.CANCELLED), "Cancelled", rid());
  }

  @PostMapping("/orders/{id}/complete")
  public ApiResponse<OrderResponse> complete(@PathVariable String id) {
    return ApiResponse.ok(orders.transition(id, OrderStateMachine.State.COMPLETED), "Completed", rid());
  }

  @PostMapping("/payments")
  public ApiResponse<PaymentResponse> createPayment(@Valid @RequestBody CreatePayment req,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ApiResponse.ok(payments.createPayment(req.orderId(), key), "Payment created", rid());
  }

  @PostMapping("/payments/webhook")
  public ApiResponse<PaymentResponse> webhook(@RequestParam String reference,
      @RequestParam boolean success,
      @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
      @RequestBody(required = false) String rawBody) {
    return ApiResponse.ok(payments.handleWebhookVerified(reference, success, signature, rawBody),
        "Webhook processed", rid());
  }

  @PostMapping("/refunds")
  public ApiResponse<String> refund(@Valid @RequestBody RefundRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ApiResponse.ok(payments.refund(req.paymentId(), key), "Refund initiated", rid());
  }
}
