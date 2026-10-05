package com.codewild.food.serviceability.api;

import jakarta.validation.constraints.NotBlank;

public class ServiceabilityDtos {
  public record CheckRequest(@NotBlank String addressId, String vendorId, String slot) {}
  public record CheckResponse(boolean serviceable, String vendorId, String slot,
                              String estimatedDelivery, String deliveryFee, String reason) {}
}
