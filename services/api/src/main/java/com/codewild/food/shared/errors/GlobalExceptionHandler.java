package com.codewild.food.shared.errors;

import com.codewild.food.shared.observability.RequestIdFilter;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private String reqId() {
    String id = MDC.get(RequestIdFilter.REQUEST_ID_MDC);
    return id != null ? id : "n/a";
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse.ErrorResponse> business(BusinessException ex) {
    HttpStatus status = switch (ex.getCode()) {
      case ErrorCodes.NOT_FOUND -> HttpStatus.NOT_FOUND;
      case ErrorCodes.FORBIDDEN -> HttpStatus.FORBIDDEN;
      case ErrorCodes.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
      case ErrorCodes.TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED;
      case ErrorCodes.TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
      case ErrorCodes.UNKNOWN_USER -> HttpStatus.UNAUTHORIZED;
      case ErrorCodes.AUTH_MISCONFIGURED -> HttpStatus.INTERNAL_SERVER_ERROR;
      case ErrorCodes.OTP_EXPIRED -> HttpStatus.GONE;
      case ErrorCodes.OTP_ATTEMPTS_EXCEEDED, ErrorCodes.OTP_RESEND_TOO_SOON,
           ErrorCodes.RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
      case ErrorCodes.OTP_INVALID -> HttpStatus.UNAUTHORIZED;
      case ErrorCodes.VENDOR_NOT_APPROVED, ErrorCodes.RIDER_NOT_APPROVED,
           ErrorCodes.VENDOR_SUSPENDED, ErrorCodes.RIDER_SUSPENDED -> HttpStatus.FORBIDDEN;
      case ErrorCodes.INVALID_TRANSITION, ErrorCodes.VALIDATION,
           ErrorCodes.ORDER_SLOT_FULL, ErrorCodes.SERVICEABILITY_UNAVAILABLE,
           ErrorCodes.PAYMENT_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
      case ErrorCodes.CONFLICT, ErrorCodes.IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
      default -> HttpStatus.BAD_REQUEST;
    };
    return ResponseEntity.status(status)
        .body(ApiResponse.ErrorResponse.of(ex.getCode(), ex.getMessage(), null, reqId()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse.ErrorResponse> validation(MethodArgumentNotValidException ex) {
    String msg = ex.getBindingResult().getFieldErrors().stream()
        .map(f -> f.getField() + ": " + f.getDefaultMessage()).findFirst().orElse("Validation failed");
    return ResponseEntity.badRequest()
        .body(ApiResponse.ErrorResponse.of(ErrorCodes.VALIDATION, msg, null, reqId()));
  }

  @ExceptionHandler({EntityNotFoundException.class})
  public ResponseEntity<ApiResponse.ErrorResponse> notFound(RuntimeException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.ErrorResponse.of(ErrorCodes.NOT_FOUND, ex.getMessage(), null, reqId()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse.ErrorResponse> denied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.ErrorResponse.of(ErrorCodes.FORBIDDEN, "Access denied", null, reqId()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse.ErrorResponse> fallback(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.ErrorResponse.of(ErrorCodes.INTERNAL, "Unexpected error", Map.of(), reqId()));
  }
}
