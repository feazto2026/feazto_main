package com.codewild.food.commerce.infrastructure;

import com.codewild.food.commerce.domain.OrderEntity;
import com.codewild.food.commerce.domain.OrderStateMachine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
  Optional<OrderEntity> findByIdempotencyKey(String key);
  Optional<OrderEntity> findByOrderNumber(String orderNumber);
  List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
  List<OrderEntity> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);
  List<OrderEntity> findByVendorIdAndStatus(UUID vendorId, OrderStateMachine.State status);
  Page<OrderEntity> findByCustomerId(UUID customerId, Pageable pageable);
  Page<OrderEntity> findByVendorId(UUID vendorId, Pageable pageable);
  List<OrderEntity> findBySubscriptionId(UUID subscriptionId);

  default Optional<OrderEntity> findById(String id) {
    try { return findById(UUID.fromString(id)); }
    catch (Exception e) { return Optional.empty(); }
  }
  default List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId) {
    try { return findByCustomerIdOrderByCreatedAtDesc(UUID.fromString(customerId)); }
    catch (Exception e) { return List.of(); }
  }
  default List<OrderEntity> findByVendorIdOrderByCreatedAtDesc(String vendorId) {
    try { return findByVendorIdOrderByCreatedAtDesc(UUID.fromString(vendorId)); }
    catch (Exception e) { return List.of(); }
  }
}
