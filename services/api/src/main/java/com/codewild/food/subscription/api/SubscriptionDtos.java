package com.codewild.food.subscription.api;

import jakarta.validation.constraints.NotBlank;

public class SubscriptionDtos {
  public record CreateSubscription(@NotBlank String vendorId, @NotBlank String mealPlanId) {}
  public record SubscriptionResponse(String id, String status, String customerId) {}
}
