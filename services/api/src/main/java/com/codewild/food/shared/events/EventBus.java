package com.codewild.food.shared.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EventBus {
  private static final Logger log = LoggerFactory.getLogger(EventBus.class);
  private final List<Consumer<DomainEvent>> handlers = new CopyOnWriteArrayList<>();

  public void subscribe(Consumer<DomainEvent> handler) { handlers.add(handler); }

  public void publish(DomainEvent event) {
    for (Consumer<DomainEvent> h : handlers) {
      try { h.accept(event); }
      catch (Exception e) { log.warn("Event handler failed for {}: {}", event.type(), e.getMessage()); }
    }
  }
}
