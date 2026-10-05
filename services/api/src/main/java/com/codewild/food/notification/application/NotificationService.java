package com.codewild.food.notification.application;

import com.codewild.food.notification.domain.NotificationEntity;
import com.codewild.food.notification.infrastructure.NotificationRepository;
import com.codewild.food.shared.events.DomainEvent;
import com.codewild.food.shared.security.SecurityUtils;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.codewild.food.shared.events.EventBus;

@Service
public class NotificationService {
  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
  private final NotificationRepository repo;
  private final EventBus bus;

  public NotificationService(NotificationRepository repo, EventBus bus) {
    this.repo = repo; this.bus = bus;
  }

  /** Semantic-event driven: business modules publish events, this maps to user messages. */
  @PostConstruct
  public void subscribe() {
    bus.subscribe(this::onEvent);
  }

  private void onEvent(DomainEvent e) {
    String msg = switch (e.type()) {
      case "OrderReady", "OrderREADY_FOR_PICKUP" -> "Your order is ready for pickup";
      case "OrderDelivered" -> "Your order was delivered. Enjoy!";
      case "RiderAssigned" -> "A rider was assigned to your order";
      case "PaymentSucceeded" -> "Payment successful";
      default -> null;
    };
    if (msg != null) {
      log.info("Notify {}: {}", e.type(), msg);
      // Persist in-app notification best-effort; business state never depends on this.
      try { repo.save(new NotificationEntity(e.aggregateId(), "IN_APP", e.type(), msg)); }
      catch (Exception ex) { log.warn("Notification persist failed: {}", ex.getMessage()); }
    }
  }

  public List<NotificationEntity> myNotifications() {
    return repo.findByUserIdOrderByCreatedAtDesc(SecurityUtils.currentUserId());
  }
}
