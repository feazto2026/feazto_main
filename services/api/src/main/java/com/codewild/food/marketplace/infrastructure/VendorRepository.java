package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.VendorProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VendorRepository extends JpaRepository<VendorProfile, UUID> {
  Optional<VendorProfile> findByUserId(UUID userId);
  default Optional<VendorProfile> findByUserId(String userId) {
    try { return findByUserId(UUID.fromString(userId)); }
    catch (Exception e) { return Optional.empty(); }
  }
  List<VendorProfile> findByStatusAndActiveTrue(VendorProfile.Status status);
  default List<VendorProfile> findByStatusAndAvailableTrue(String status) {
    try { return findByStatusAndActiveTrue(VendorProfile.Status.valueOf(status)); }
    catch (Exception e) { return List.of(); }
  }
  List<VendorProfile> findByCityAndStatus(String city, VendorProfile.Status status);

  // Discovery search: vendor name / cuisine / region / item with pagination.
  @Query("""
    select distinct v from VendorProfile v
    left join MenuItem i on i.vendorId = v.id
    where v.status = com.codewild.food.marketplace.domain.VendorProfile.Status.APPROVED
      and v.active = true and v.deletedAt is null
      and (:q is null or lower(v.kitchenName) like lower(concat('%', :q, '%'))
        or lower(i.name) like lower(concat('%', :q, '%')))
      and (:city is null or v.city = :city)
    """)
  Page<VendorProfile> search(String q, String city, Pageable pageable);

  @Query("select v from VendorProfile v where v.status = com.codewild.food.marketplace.domain.VendorProfile.Status.APPROVED and v.active = true and v.deletedAt is null")
  List<VendorProfile> findApprovedActive();
}
