package com.codewild.food.fulfillment.domain;

import com.codewild.food.shared.errors.BusinessException;
import com.codewild.food.shared.errors.ErrorCodes;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class DeliveryStateMachine {
  /** Supabase truth: deliveries.status CHECK (0006). */
  public enum State {
    AVAILABLE, ASSIGNMENT_PENDING, ASSIGNED, ACCEPTED, ARRIVED_AT_VENDOR,
    PICKUP_VERIFICATION_PENDING, PICKED_UP, EN_ROUTE, ARRIVED_AT_CUSTOMER,
    DELIVERY_VERIFICATION_PENDING, DELIVERED, CANCELLED, FAILED
  }

  private static final Map<State, Set<State>> ALLOWED = new EnumMap<>(State.class);
  static {
    ALLOWED.put(State.AVAILABLE, EnumSet.of(State.ASSIGNMENT_PENDING, State.ASSIGNED, State.CANCELLED));
    ALLOWED.put(State.ASSIGNMENT_PENDING, EnumSet.of(State.ASSIGNED, State.CANCELLED, State.FAILED));
    ALLOWED.put(State.ASSIGNED, EnumSet.of(State.ACCEPTED, State.ASSIGNMENT_PENDING, State.ASSIGNED, State.CANCELLED));
    ALLOWED.put(State.ACCEPTED, EnumSet.of(State.ARRIVED_AT_VENDOR, State.PICKUP_VERIFICATION_PENDING, State.PICKED_UP, State.CANCELLED));
    ALLOWED.put(State.ARRIVED_AT_VENDOR, EnumSet.of(State.PICKUP_VERIFICATION_PENDING, State.PICKED_UP));
    ALLOWED.put(State.PICKUP_VERIFICATION_PENDING, EnumSet.of(State.PICKED_UP, State.FAILED));
    ALLOWED.put(State.PICKED_UP, EnumSet.of(State.EN_ROUTE));
    ALLOWED.put(State.EN_ROUTE, EnumSet.of(State.ARRIVED_AT_CUSTOMER, State.DELIVERY_VERIFICATION_PENDING, State.DELIVERED, State.FAILED));
    ALLOWED.put(State.ARRIVED_AT_CUSTOMER, EnumSet.of(State.DELIVERY_VERIFICATION_PENDING, State.DELIVERED));
    ALLOWED.put(State.DELIVERY_VERIFICATION_PENDING, EnumSet.of(State.DELIVERED, State.FAILED));
    ALLOWED.put(State.DELIVERED, EnumSet.noneOf(State.class));
    ALLOWED.put(State.FAILED, EnumSet.of(State.ASSIGNMENT_PENDING, State.ASSIGNED));
    ALLOWED.put(State.CANCELLED, EnumSet.noneOf(State.class));
  }

  // Back-compat aliases for pre-Supabase states used by older services.
  public static State compat(String legacy) {
    return switch (legacy) {
      case "CREATED" -> State.AVAILABLE;
      case "OUT_FOR_DELIVERY" -> State.EN_ROUTE;
      default -> State.valueOf(legacy);
    };
  }

  public static void validate(State from, State to) {
    if (!ALLOWED.getOrDefault(from, Set.of()).contains(to))
      throw new BusinessException(ErrorCodes.INVALID_TRANSITION, "Invalid delivery transition " + from + " -> " + to);
  }
}
