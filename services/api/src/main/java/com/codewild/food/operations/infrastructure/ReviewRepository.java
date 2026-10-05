package com.codewild.food.operations.infrastructure;

import com.codewild.food.operations.domain.Review;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
  Optional<Review> findByOrderId(UUID orderId);
  List<Review> findByVendorIdAndStatusOrderByCreatedAtDesc(UUID vendorId, String status);
  List<Review> findByRiderId(UUID riderId);
}
