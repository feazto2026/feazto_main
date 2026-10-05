package com.codewild.food.commerce.application;

import com.codewild.food.commerce.api.CommerceDtos.*;
import com.codewild.food.commerce.domain.*;
import com.codewild.food.commerce.infrastructure.OrderRepository;
import com.codewild.food.commerce.infrastructure.PaymentRepository;
import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import com.codewild.food.shared.events.DomainEvent;
import com.codewild.food.shared.events.OutboxService;
import com.codewild.food.shared.idempotency.IdempotencyService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private final PaymentRepository payments;
  private final OrderRepository orders;
  private final PaymentProvider provider;
  private final IdempotencyService idempotency;
  private final OutboxService outbox;
  private final WebhookSignatureVerifier webhookVerifier;

  public PaymentService(PaymentRepository payments, OrderRepository orders, PaymentProvider provider,
                        IdempotencyService idempotency, OutboxService outbox,
                        WebhookSignatureVerifier webhookVerifier) {
    this.payments = payments; this.orders = orders; this.provider = provider;
    this.idempotency = idempotency; this.outbox = outbox;
    this.webhookVerifier = webhookVerifier;
  }

  private static String str(UUID v) {
    return v == null ? null : v.toString();
  }

  private static UUID uuidOrThrow(String raw, String message) {
    try {
      return UUID.fromString(raw);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, message);
    }
  }

  @Transactional
  public PaymentResponse createPayment(String orderId, String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var existing = payments.findByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) {
        var e = existing.get();
        return new PaymentResponse(str(e.getId()), e.getStatus(), e.getProviderReference());
      }
      if (idempotency.isDuplicate("payment", idempotencyKey))
        throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT, "Duplicate payment request");
    }
    OrderEntity order = orders.findById(uuidOrThrow(orderId, "Order not found"))
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Order not found"));
    long paise = order.getTotalAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue();
    PaymentEntity entity = new PaymentEntity(orderId, paise, idempotencyKey);
    // Link customer for ownership checks (UUID truth).
    entity.setCustomerId(order.getCustomerId());
    var intent = provider.createPayment(
        new PaymentProvider.CreatePaymentCommand(str(order.getId()), paise, "INR", idempotencyKey));
    entity.setProviderReference(intent.reference());
    entity.setStatus(intent.status());
    PaymentEntity saved = payments.save(entity);
    return new PaymentResponse(str(saved.getId()), saved.getStatus(), saved.getProviderReference());
  }

  /**
   * Webhook entry point with provider authentication. The HMAC signature over
   * the raw body is verified BEFORE any state is read, so forged callbacks
   * can never flip payment/order state. Replay safety comes from the terminal-
   * state short-circuit below + {@code payments.provider_payment_id} UNIQUE
   * (Supabase 0004): replays return the same result with no duplicate outbox.
   */
  @Transactional
  public PaymentResponse handleWebhookVerified(String providerReference, boolean success,
      String signatureHeader, String rawBody) {
    if (webhookVerifier != null && !webhookVerifier.verify(rawBody, signatureHeader)) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, "Invalid webhook signature");
    }
    return handleWebhook(providerReference, success);
  }

  @Transactional
  public PaymentResponse handleWebhook(String providerReference, boolean success) {
    // Webhook idempotency: provider_payment_id is UNIQUE (0004); replays return same result.
    PaymentEntity p = payments.findByProviderReference(providerReference)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Payment not found"));
    // Idempotent replay: terminal states return without side effects (no duplicate outbox).
    String current = p.getStatus();
    if ("SUCCESS".equals(current)) {
      return new PaymentResponse(str(p.getId()), p.getStatus(), p.getProviderReference());
    }
    if ("FAILED".equals(current) && !success) {
      return new PaymentResponse(str(p.getId()), p.getStatus(), p.getProviderReference());
    }
    var verification = provider.verifyPayment(providerReference);
    p.setStatus(verification.success() && success ? "SUCCESS" : "FAILED");
    payments.save(p);
    if ("SUCCESS".equals(p.getStatus())) {
      OrderEntity order = orders.findById(p.getOrderId())
          .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Order not found"));
      order.transitionTo(OrderStateMachine.State.PLACED);
      orders.save(order);
      outbox.save(DomainEvent.of("PaymentSucceeded", "Payment", str(p.getId()), "{}"));
    } else {
      outbox.save(DomainEvent.of("PaymentFailed", "Payment", str(p.getId()), "{}"));
    }
    return new PaymentResponse(str(p.getId()), p.getStatus(), p.getProviderReference());
  }

  @Transactional
  public String refund(String paymentId, String idempotencyKey) {
    if (idempotencyKey != null && idempotency.isDuplicate("refund", idempotencyKey))
      throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT, "Duplicate refund request");
    PaymentEntity p = payments.findById(uuidOrThrow(paymentId, "Payment not found"))
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Payment not found"));
    var result = provider.refund(new PaymentProvider.RefundCommand(
        p.getProviderReference(), p.getAmountPaise(), idempotencyKey));
    outbox.save(DomainEvent.of("RefundCreated", "Payment", str(p.getId()), "{\"refund\":\"" + result.refundReference() + "\"}"));
    return result.refundReference();
  }
}
