package com.codewild.food.subscription.application;

import com.codewild.food.commerce.domain.OrderEntity;
import com.codewild.food.commerce.domain.OrderStateMachine;
import com.codewild.food.commerce.infrastructure.OrderRepository;
import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import com.codewild.food.shared.events.DomainEvent;
import com.codewild.food.shared.events.OutboxService;
import com.codewild.food.subscription.domain.Subscription;
import com.codewild.food.subscription.domain.SubscriptionDailyOrder;
import com.codewild.food.subscription.infrastructure.SubscriptionDailyOrderRepository;
import com.codewild.food.subscription.infrastructure.SubscriptionRepository;
import com.codewild.food.subscription.infrastructure.SubscriptionScheduleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent daily order generator (0005): UNIQUE(subscription_id, service_date,
 * meal_slot_id) guarantees exactly-once. Inserts subscription_daily_orders first,
 * then the operational order, in the same transaction; double-run returns the
 * existing idempotency key.
 */
@Service
public class SubscriptionOrderGenerator {
  private static final Logger log = LoggerFactory.getLogger(SubscriptionOrderGenerator.class);
  private final SubscriptionRepository subs;
  private final SubscriptionScheduleRepository schedules;
  private final SubscriptionDailyOrderRepository dailyOrders;
  private final OrderRepository orders;
  private final OutboxService outbox;

  public SubscriptionOrderGenerator(SubscriptionRepository subs,
      SubscriptionScheduleRepository schedules,
      SubscriptionDailyOrderRepository dailyOrders,
      OrderRepository orders,
      OutboxService outbox) {
    this.subs = subs; this.schedules = schedules;
    this.dailyOrders = dailyOrders; this.orders = orders; this.outbox = outbox;
  }

  @Scheduled(cron = "0 30 5 * * *")
  public void generateDailyOrders() {
    LocalDate today = LocalDate.now();
    List<Subscription> active = subs.findByStatus(Subscription.Status.ACTIVE);
    for (Subscription s : active) {
      try { generateFor(s.getIdAsString(), today.toString()); }
      catch (Exception e) { log.warn("Subscription generation failed {}: {}", s.getIdAsString(), e.getMessage()); }
    }
  }

  @Transactional
  public String generateFor(String subscriptionId, String date) {
    UUID subId;
    LocalDate serviceDate;
    try {
      subId = UUID.fromString(subscriptionId);
      serviceDate = LocalDate.parse(date);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.VALIDATION, "Invalid subscription/date");
    }
    Subscription sub = subs.findById(subId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Subscription not found"));
    if (sub.getStatusEnum() != Subscription.Status.ACTIVE) {
      throw new BusinessException(ErrorCodes.VALIDATION, "Subscription not ACTIVE");
    }
    // Paused/skip windows never mutate already-generated orders; skip generation only.
    if (sub.getSkipDates() != null && Arrays.asList(sub.getSkipDates()).contains(serviceDate)) {
      log.info("Subscription {} skipped for {}", subscriptionId, date);
      return "sub:" + subscriptionId + ":" + date + ":SKIPPED";
    }
    // Weekday mapping: Supabase 0=Sunday..6=Saturday; Java MONDAY=1..SUNDAY=7.
    int weekday = serviceDate.getDayOfWeek().getValue() % 7;
    var scheds = schedules.findBySubscriptionIdAndActiveTrue(subId);
    if (scheds.isEmpty()) {
      throw new BusinessException(ErrorCodes.VALIDATION, "No active schedule for subscription");
    }
    var sched = scheds.stream().filter(s -> s.getWeekday() == weekday).findFirst()
        .orElse(scheds.get(0));
    UUID slotId = sched.getMealSlotId();

    // Daily unique check: (subscription_id, service_date, meal_slot_id).
    var existing = dailyOrders.findBySubscriptionIdAndServiceDateAndMealSlotId(subId, serviceDate, slotId);
    if (existing.isPresent()) {
      return existing.get().getIdempotencyKey();
    }
    String deterministicKey = "sub:" + subscriptionId + ":" + date + ":" + slotId;
    var byKey = dailyOrders.findByIdempotencyKey(deterministicKey);
    if (byKey.isPresent()) {
      return byKey.get().getIdempotencyKey();
    }

    // 1. Insert daily_orders log first (SCHEDULED) — UNIQUE triplet is the backstop.
    SubscriptionDailyOrder dayLog = new SubscriptionDailyOrder(subId, serviceDate, slotId, deterministicKey);
    try {
      dayLog = dailyOrders.saveAndFlush(dayLog);
    } catch (DataIntegrityViolationException dup) {
      // Concurrent double-run collapsed on UNIQUE(subscription, date, slot).
      return dailyOrders.findByIdempotencyKey(deterministicKey)
          .map(SubscriptionDailyOrder::getIdempotencyKey).orElse(deterministicKey);
    }

    // 2. Then the operational order in the SAME transaction.
    OrderEntity order = new OrderEntity();
    order.setCustomerId(sub.getCustomerId());
    order.setVendorId(sub.getVendorId());
    order.setSlotId(slotId);
    order.setServiceDate(serviceDate);
    order.setSubscriptionId(sub.getId());
    order.setIdempotencyKey("ord:" + deterministicKey);
    order.setSubtotalPaise(0);
    order.setDiscountPaise(0);
    order.setTaxPaise(0);
    order.setDeliveryFeePaise(0);
    order.setPlatformFeePaise(0);
    order.setPackagingFeePaise(0);
    order.setItemCount(sched.getQuantity());
    order.setDeliveryAddressSnapshot("{}");
    order.recomputeTotalOrThrow();
    order.transitionTo(OrderStateMachine.State.PLACED);
    OrderEntity saved;
    try {
      saved = orders.saveAndFlush(order);
    } catch (DataIntegrityViolationException dup) {
      // Order idempotencyKey UNIQUE collapsed; link existing order if present.
      var ordDup = orders.findByIdempotencyKey("ord:" + deterministicKey);
      if (ordDup.isPresent()) {
        dayLog.setOrderId(ordDup.get().getId());
        dayLog.setStatus(SubscriptionDailyOrder.Status.GENERATED);
        dayLog.setGeneratedAt(Instant.now());
        dailyOrders.save(dayLog);
        return deterministicKey;
      }
      throw new BusinessException(ErrorCodes.IDEMPOTENCY_CONFLICT, "Duplicate subscription order");
    }

    // 3. Link + mark GENERATED, still in the same transaction.
    dayLog.setOrderId(saved.getId());
    dayLog.setStatus(SubscriptionDailyOrder.Status.GENERATED);
    dayLog.setGeneratedAt(Instant.now());
    dailyOrders.save(dayLog);

    outbox.save(DomainEvent.of("DailyMealOrderGenerated", "Subscription", subscriptionId,
        "{\"date\":\"" + date + "\",\"key\":\"" + deterministicKey
        + "\",\"order\":\"" + saved.getId() + "\"}"));
    return deterministicKey;
  }
}
