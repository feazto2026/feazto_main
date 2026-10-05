package com.codewild.food.fulfillment.infrastructure;

import com.codewild.food.fulfillment.domain.RiderProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RiderRepository extends JpaRepository<RiderProfile, UUID> {
  Optional<RiderProfile> findByUserId(UUID userId);
  default Optional<RiderProfile> findByUserId(String userId) {
    try { return findByUserId(UUID.fromString(userId)); }
    catch (Exception e) { return Optional.empty(); }
  }
  List<RiderProfile> findByOnlineTrueAndStatus(RiderProfile.Status status);

  @Query("SELECT r FROM RiderProfile r WHERE r.online = true "
      + "AND (r.status = com.codewild.food.fulfillment.domain.RiderProfile$Status.APPROVED "
      + "OR r.status = com.codewild.food.fulfillment.domain.RiderProfile$Status.ACTIVE)")
  List<RiderProfile> findOnlineApproved();

  /** Dispatch candidate scan: zone (city) + online + approved/active. */
  @Query("select r from RiderProfile r where r.online = true and r.city = :city "
    + "and (r.status = com.codewild.food.fulfillment.domain.RiderProfile.Status.APPROVED "
    + "or r.status = com.codewild.food.fulfillment.domain.RiderProfile.Status.ACTIVE) "
    + "order by r.totalDeliveries asc")
  List<RiderProfile> findDispatchCandidates(String city);

  default List<RiderProfile> findByAvailableTrueAndStatus(String status) {
    try {
      var s = RiderProfile.Status.valueOf(status);
      if (s == RiderProfile.Status.APPROVED)
        return findDispatchCandidates(null).isEmpty()
          ? findByOnlineTrueAndStatus(s) : findByOnlineTrueAndStatus(s);
      return findByOnlineTrueAndStatus(s);
    } catch (Exception e) { return List.of(); }
  }
}
