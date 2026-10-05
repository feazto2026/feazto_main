package com.codewild.food.commerce.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class CommerceDtos {
  public record CartLine(@NotBlank String menuItemId, @Min(1) int quantity) {}
  public record CreateOrder(@NotBlank String vendorId, @NotEmpty List<CartLine> lines,
                            String addressId) {}
  public record OrderResponse(String id, String status, String totalAmount) {}
  public record CreatePayment(String orderId) {}
  public record PaymentResponse(String id, String status, String providerReference) {}
  public record RefundRequest(@NotBlank String paymentId) {}
}
