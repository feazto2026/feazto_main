package com.codewild.food.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AuthDtos {
  /** E.164 phone, e.g. +919876543210. Validated server-side; generic errors avoid enumeration. */
  public record OtpRequest(
      @NotBlank
      @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phone must be E.164, e.g. +919876543210")
      String phone) {}

  public record OtpVerify(
      @NotBlank
      @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phone must be E.164, e.g. +919876543210")
      String phone,
      @NotBlank
      @Pattern(regexp = "^[0-9]{6}$", message = "otp must be 6 digits")
      String otp) {}

  /**
   * Platform identity confirmed by OTP. No tokens are minted here — Supabase owns
   * sessions (access + refresh JWT); the client presents the Supabase access JWT
   * on subsequent calls and the backend resolves roles server-side per request.
   */
  public record AuthResponse(String userId, String roles, String phoneMasked) {}
}
