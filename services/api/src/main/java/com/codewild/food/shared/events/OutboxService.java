package com.codewild.food.shared.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
  private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
  private final OutboxRepository repo;
  private final EventBus bus;

  public OutboxService(OutboxRepository repo, EventBus bus) { this.repo = repo; this.bus = bus; }

  @Transactional
  public void save(DomainEvent event) {
    repo.save(new OutboxEvent(event.type(), event.aggregateType(), event.aggregateId(), event.payloadJson()));
    bus.publish(event);
  }

  @Scheduled(fixedDelay = 15000)
  @Transactional
  public void publishPending() {
    var pending = repo.findTop100ByPublishedFalseOrderByCreatedAtAsc();
    for (var e : pending) {
      try {
        bus.publish(DomainEvent.of(e.getType(), e.getAggregateType(), e.getAggregateId(), e.getPayloadJson()));
        e.markPublished();
        repo.save(e);
      } catch (Exception ex) {
        log.warn("Outbox publish failed {}: {}", e.getId(), ex.getMessage());
      }
    }
  }
}
