package com.codewild.food.shared.events;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
  List<OutboxEvent> findByStatus(OutboxEvent.Status status);
  List<OutboxEvent> findTop100ByStatusOrderByNextAttemptAtAsc(OutboxEvent.Status status);

  default List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc() {
    return findTop100ByStatusOrderByNextAttemptAtAsc(OutboxEvent.Status.PENDING);
  }

  /** Publisher scan: PENDING + due, oldest first, row-locked SKIP LOCKED. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OutboxEvent o where o.status = com.codewild.food.shared.events.OutboxEvent.Status.PENDING "
    + "and o.nextAttemptAt <= :now order by o.nextAttemptAt asc")
  List<OutboxEvent> claimDue(Instant now, Pageable pageable);

  @Query(value = "select * from outbox_events where status = 'PENDING' "
    + "and next_attempt_at <= now() order by next_attempt_at asc limit 100 "
    + "for update skip locked", nativeQuery = true)
  List<OutboxEvent> claimDueSkipLocked();

  Optional<OutboxEvent> findByIdempotencyKey(String key);
}
