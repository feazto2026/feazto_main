package com.codewild.food.customer.infrastructure;

import com.codewild.food.customer.domain.CustomerAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
  List<CustomerAddress> findByCustomerIdAndActiveTrue(UUID customerId);
  List<CustomerAddress> findByCustomerId(UUID customerId);
  Optional<CustomerAddress> findByIdAndCustomerId(UUID id, UUID customerId);
  @Query("select a from CustomerAddress a where a.customerId = :customerId and a.active = true and a.isDefault = true")
  Optional<CustomerAddress> findDefault(@Param("customerId") UUID customerId);
}
