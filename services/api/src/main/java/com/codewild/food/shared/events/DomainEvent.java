package com.codewild.food.shared.events;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent(String eventId, String type, String aggregateType,
                          String aggregateId, Instant occurredAt, String payloadJson) {
  public static DomainEvent of(String type, String aggregateType, String aggregateId, String payloadJson) {
    return new DomainEvent(UUID.randomUUID().toString(), type, aggregateType, aggregateId,
        Instant.now(), payloadJson == null ? "{}" : payloadJson);
  }
}
