package com.codewild.food.subscription.infrastructure;

import com.codewild.food.subscription.domain.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
  Optional<Subscription> findByIdempotencyKey(String key);
  Optional<Subscription> findBySubscriptionNumber(String num);
  List<Subscription> findByStatus(Subscription.Status status);
  default List<Subscription> findByStatus(String status) {
    try { return findByStatus(Subscription.Status.valueOf(status)); }
    catch (Exception e) { return List.of(); }
  }
  List<Subscription> findByCustomerId(UUID customerId);
  List<Subscription> findByVendorId(UUID vendorId);
  default List<Subscription> findByCustomerId(String customerId) {
    try { return findByCustomerId(UUID.fromString(customerId)); }
    catch (Exception e) { return List.of(); }
  }
  List<Subscription> findByVendorIdAndStatus(UUID vendorId, Subscription.Status status);
  List<Subscription> findByCustomerIdAndStatus(UUID customerId, Subscription.Status status);
}
