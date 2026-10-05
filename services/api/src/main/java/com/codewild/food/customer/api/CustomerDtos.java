package com.codewild.food.customer.api;

import jakarta.validation.constraints.NotBlank;

public class CustomerDtos {
  public record UpsertProfile(@NotBlank String displayName, String hometown) {}
  public record ProfileResponse(String id, String userId, String displayName, String hometown) {}
  public record CreateAddress(String label, @NotBlank String line1, @NotBlank String city,
                              String postalCode, Double lat, Double lng) {}
}
