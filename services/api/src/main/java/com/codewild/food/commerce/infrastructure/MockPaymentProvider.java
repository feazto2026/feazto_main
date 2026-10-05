package com.codewild.food.commerce.infrastructure;

import com.codewild.food.commerce.domain.PaymentProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stub provider; swap with Razorpay/Stripe adapter without touching domain. */
@Component
public class MockPaymentProvider implements PaymentProvider {
  @Override
  public PaymentIntent createPayment(CreatePaymentCommand cmd) {
    return new PaymentIntent("pay_" + UUID.randomUUID().toString().substring(0, 8), "CREATED", "{}");
  }
  @Override
  public PaymentVerification verifyPayment(String ref) {
    return new PaymentVerification(ref, true, "SUCCESS");
  }
  @Override
  public RefundResult refund(RefundCommand cmd) {
    return new RefundResult("rfnd_" + UUID.randomUUID().toString().substring(0, 8), "SUCCESS");
  }
}
