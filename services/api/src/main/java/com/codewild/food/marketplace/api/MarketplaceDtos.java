package com.codewild.food.marketplace.api;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public class MarketplaceDtos {
  public record RegisterVendor(@NotBlank String kitchenName, @NotBlank String city) {}
  public record VendorResponse(String id, String kitchenName, String city, String status) {}
  public record MenuItemResponse(String id, String vendorId, String name, BigDecimal price) {}
  public record CreateMenuItem(@NotBlank String name, BigDecimal price, int dailyCapacity) {}
}
