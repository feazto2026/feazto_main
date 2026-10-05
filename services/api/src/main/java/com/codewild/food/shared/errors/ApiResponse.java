package com.codewild.food.shared.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record ApiResponse<T>(boolean success, T data, String message, String requestId) {
  public static <T> ApiResponse<T> ok(T data, String message, String requestId) {
    return new ApiResponse<>(true, data, message, requestId);
  }
  public static <T> ApiResponse<T> ok(T data, String requestId) {
    return ok(data, "Operation completed", requestId);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ErrorBody(String code, String message, Map<String, Object> details) {}

  public record ErrorResponse(boolean success, ErrorBody error, String requestId) {
    public static ErrorResponse of(String code, String message, Map<String, Object> details, String requestId) {
      return new ErrorResponse(false, new ErrorBody(code, message, details), requestId);
    }
  }
}
