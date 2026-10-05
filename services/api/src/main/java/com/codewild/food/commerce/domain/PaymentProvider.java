package com.codewild.food.commerce.domain;

public interface PaymentProvider {
  PaymentIntent createPayment(CreatePaymentCommand command);
  PaymentVerification verifyPayment(String paymentReference);
  RefundResult refund(RefundCommand command);

  record CreatePaymentCommand(String orderId, long amountPaise, String currency, String idempotencyKey) {}
  record PaymentIntent(String reference, String status, String providerData) {}
  record PaymentVerification(String reference, boolean success, String status) {}
  record RefundCommand(String paymentReference, long amountPaise, String idempotencyKey) {}
  record RefundResult(String refundReference, String status) {}
}
