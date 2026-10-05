package com.codewild.food.commerce.domain;

import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class OrderStateMachine {
  /** Supabase truth: orders.status CHECK (0004) — full lifecycle. */
  public enum State {
    CREATED, PAYMENT_PENDING, PAYMENT_CONFIRMED, PAYMENT_FAILED,
    PLACED, VENDOR_PENDING, VENDOR_ACCEPTED, PREPARING, READY_FOR_PICKUP,
    RIDER_ASSIGNED, RIDER_ACCEPTED, PICKED_UP, OUT_FOR_DELIVERY, ARRIVED,
    DELIVERED, CUSTOMER_CONFIRMED, COMPLETED,
    CANCEL_REQUESTED, CANCELLED, REFUND_PENDING, REFUNDED, FAILED
  }

  private static final Map<State, Set<State>> ALLOWED = new EnumMap<>(State.class);

  static {
    ALLOWED.put(State.CREATED, EnumSet.of(State.PAYMENT_PENDING, State.CANCELLED, State.FAILED));
    ALLOWED.put(State.PAYMENT_PENDING, EnumSet.of(State.PAYMENT_CONFIRMED, State.PAYMENT_FAILED, State.CANCELLED));
    ALLOWED.put(State.PAYMENT_CONFIRMED, EnumSet.of(State.PLACED, State.VENDOR_PENDING, State.CANCELLED));
    ALLOWED.put(State.PAYMENT_FAILED, EnumSet.of(State.PAYMENT_PENDING, State.CANCELLED, State.FAILED));
    ALLOWED.put(State.PLACED, EnumSet.of(State.VENDOR_PENDING, State.VENDOR_ACCEPTED, State.CANCEL_REQUESTED, State.CANCELLED));
    ALLOWED.put(State.VENDOR_PENDING, EnumSet.of(State.VENDOR_ACCEPTED, State.CANCEL_REQUESTED, State.CANCELLED));
    ALLOWED.put(State.VENDOR_ACCEPTED, EnumSet.of(State.PREPARING, State.CANCEL_REQUESTED, State.CANCELLED));
    ALLOWED.put(State.PREPARING, EnumSet.of(State.READY_FOR_PICKUP, State.CANCEL_REQUESTED, State.CANCELLED));
    ALLOWED.put(State.READY_FOR_PICKUP, EnumSet.of(State.RIDER_ASSIGNED, State.CANCEL_REQUESTED, State.CANCELLED));
    ALLOWED.put(State.RIDER_ASSIGNED, EnumSet.of(State.RIDER_ACCEPTED, State.PICKED_UP, State.CANCEL_REQUESTED, State.CANCELLED));
    ALLOWED.put(State.RIDER_ACCEPTED, EnumSet.of(State.PICKED_UP, State.CANCEL_REQUESTED));
    ALLOWED.put(State.PICKED_UP, EnumSet.of(State.OUT_FOR_DELIVERY));
    ALLOWED.put(State.OUT_FOR_DELIVERY, EnumSet.of(State.ARRIVED, State.DELIVERED));
    ALLOWED.put(State.ARRIVED, EnumSet.of(State.DELIVERED));
    ALLOWED.put(State.DELIVERED, EnumSet.of(State.CUSTOMER_CONFIRMED, State.COMPLETED, State.REFUND_PENDING));
    ALLOWED.put(State.CUSTOMER_CONFIRMED, EnumSet.of(State.COMPLETED));
    ALLOWED.put(State.COMPLETED, EnumSet.noneOf(State.class));
    ALLOWED.put(State.CANCEL_REQUESTED, EnumSet.of(State.CANCELLED, State.VENDOR_ACCEPTED, State.PREPARING));
    ALLOWED.put(State.CANCELLED, EnumSet.of(State.REFUND_PENDING, State.REFUNDED));
    ALLOWED.put(State.REFUND_PENDING, EnumSet.of(State.REFUNDED));
    ALLOWED.put(State.REFUNDED, EnumSet.noneOf(State.class));
    ALLOWED.put(State.FAILED, EnumSet.of(State.PAYMENT_PENDING));
  }

  public static void validate(State from, State to) {
    if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
      throw new BusinessException(ErrorCodes.INVALID_TRANSITION,
          "Invalid transition " + from + " -> " + to + " (e.g. COMPLETED->PREPARING is rejected)");
    }
  }

  public static boolean isTerminal(State s) {
    return s == State.COMPLETED || s == State.REFUNDED || s == State.FAILED;
  }
}
