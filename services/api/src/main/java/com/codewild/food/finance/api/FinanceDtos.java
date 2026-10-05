package com.codewild.food.finance.api;

import java.math.BigDecimal;

public class FinanceDtos {
  public record CreatePayout(String beneficiaryType, String beneficiaryId, BigDecimal amount) {}
  public record PayoutResponse(String id, String status) {}
}
