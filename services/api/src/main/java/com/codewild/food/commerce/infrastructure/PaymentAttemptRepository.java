package com.codewild.food.commerce.infrastructure;

import com.codewild.food.commerce.domain.PaymentAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
  Optional<PaymentAttempt> findByIdempotencyKey(String key);
  List<PaymentAttempt> findByPaymentIdOrderByAttemptNoAsc(UUID paymentId);
}
