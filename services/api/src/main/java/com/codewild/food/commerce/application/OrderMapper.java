package com.codewild.food.commerce.application;

import com.codewild.food.commerce.api.CommerceDtos.OrderResponse;
import com.codewild.food.commerce.domain.OrderEntity;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {
  public OrderResponse toResponse(OrderEntity e) {
    return new OrderResponse(e.getId() == null ? null : e.getId().toString(),
        e.getStatus().name(), String.valueOf(e.getTotalAmount()));
  }
}
