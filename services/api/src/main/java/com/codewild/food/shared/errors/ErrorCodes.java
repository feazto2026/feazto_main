package com.codewild.food.shared.errors;

public final class ErrorCodes {
  private ErrorCodes() {}
  public static final String VALIDATION = "VALIDATION_ERROR";
  public static final String UNAUTHORIZED = "UNAUTHORIZED";
  public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
  public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
  public static final String TOKEN_INVALID = "TOKEN_INVALID";
  public static final String FORBIDDEN = "FORBIDDEN";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String CONFLICT = "CONFLICT";
  public static final String INVALID_TRANSITION = "INVALID_STATE_TRANSITION";
  public static final String ORDER_SLOT_FULL = "ORDER_SLOT_FULL";
  public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
  public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
  public static final String SERVICEABILITY_UNAVAILABLE = "SERVICEABILITY_UNAVAILABLE";
  public static final String RATE_LIMITED = "RATE_LIMITED";
  public static final String OTP_EXPIRED = "OTP_EXPIRED";
  public static final String OTP_ATTEMPTS_EXCEEDED = "OTP_ATTEMPTS_EXCEEDED";
  public static final String OTP_RESEND_TOO_SOON = "OTP_RESEND_TOO_SOON";
  public static final String OTP_INVALID = "OTP_INVALID";
  public static final String UNKNOWN_USER = "UNKNOWN_USER";
  public static final String AUTH_MISCONFIGURED = "AUTH_MISCONFIGURED";
  public static final String VENDOR_NOT_APPROVED = "VENDOR_NOT_APPROVED";
  public static final String VENDOR_SUSPENDED = "VENDOR_SUSPENDED";
  public static final String RIDER_NOT_APPROVED = "RIDER_NOT_APPROVED";
  public static final String RIDER_SUSPENDED = "RIDER_SUSPENDED";
  public static final String INTERNAL = "INTERNAL_ERROR";
}
