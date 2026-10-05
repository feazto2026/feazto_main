package com.codewild.food.fulfillment.api;

public class FulfillmentDtos {
  public record DeliveryResponse(String id, String orderId, String riderId, String status) {}
  public record ConfirmRequest(String code) {}
}
