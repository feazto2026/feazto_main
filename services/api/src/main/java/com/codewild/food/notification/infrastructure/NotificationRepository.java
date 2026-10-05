package com.codewild.food.notification.infrastructure;

import com.codewild.food.notification.domain.NotificationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
  List<NotificationEntity> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipient);
  List<NotificationEntity> findByRecipientUserIdAndStatusOrderByCreatedAtDesc(
    UUID recipient, NotificationEntity.Status status);
  Optional<NotificationEntity> findByDedupeKey(String dedupeKey);

  default List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(String userId) {
    try { return findByRecipientUserIdOrderByCreatedAtDesc(UUID.fromString(userId)); }
    catch (Exception e) { return List.of(); }
  }
}
