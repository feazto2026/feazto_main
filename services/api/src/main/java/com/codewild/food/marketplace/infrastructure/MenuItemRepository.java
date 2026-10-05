package com.codewild.food.marketplace.infrastructure;

import com.codewild.food.marketplace.domain.MenuItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
  List<MenuItem> findByVendorIdAndActiveTrueAndAvailableTrueAndDeletedAtIsNull(UUID vendorId);
  default List<MenuItem> findByVendorIdAndPublishedTrue(String vendorId) {
    try { return findByVendorIdAndActiveTrueAndAvailableTrueAndDeletedAtIsNull(UUID.fromString(vendorId)); }
    catch (Exception e) { return List.of(); }
  }
  List<MenuItem> findByVendorIdAndAvailableTrue(UUID vendorId);
  Page<MenuItem> findByVendorId(UUID vendorId, Pageable pageable);

  @Query("""
    select i from MenuItem i where i.vendorId = :vendorId
    and i.active = true and i.available = true and i.deletedAt is null
    and (:q is null or lower(i.name) like lower(concat('%', :q, '%')))
    """)
  Page<MenuItem> searchVendorMenu(UUID vendorId, String q, Pageable pageable);

  @Query("""
    select i from MenuItem i where i.active = true and i.available = true and i.deletedAt is null
    and (:q is null or lower(i.name) like lower(concat('%', :q, '%')))
    """)
  Page<MenuItem> searchAll(String q, Pageable pageable);
}
