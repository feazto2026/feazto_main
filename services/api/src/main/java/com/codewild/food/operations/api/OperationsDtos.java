package com.codewild.food.operations.api;

public class OperationsDtos {
  public record VendorDecision(String vendorId, boolean approve, String reason) {}
  public record MessageResponse(String message) {}
}
