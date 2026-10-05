package com.codewild.food.commerce.infrastructure;

import com.codewild.food.commerce.domain.PaymentAttempt;
import com.codewild.food.commerce.domain.PaymentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
  Optional<PaymentEntity> findByIdempotencyKey(String key);
  /** Webhook idempotency: provider_payment_id UNIQUE. */
  Optional<PaymentEntity> findByProviderPaymentId(String providerPaymentId);
  default Optional<PaymentEntity> findByProviderReference(String ref) {
    return findByProviderPaymentId(ref);
  }
  List<PaymentEntity> findByOrderId(UUID orderId);
  List<PaymentEntity> findByCustomerId(UUID customerId);
  List<PaymentEntity> findByStatus(PaymentEntity.Status status);
}
