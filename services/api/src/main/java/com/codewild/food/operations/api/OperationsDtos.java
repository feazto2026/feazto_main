package com.codewild.food.operations.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class OperationsDtos {
  public record VendorDecision(String vendorId, boolean approve, String reason) {}
  public record MessageResponse(String message) {}

  /**
   * Admin Web password login payload. Password truth lives in Supabase Auth
   * (GoTrue); the backend never stores or verifies admin passwords itself.
   */
  public record LoginRequest(
      @NotBlank(message = "email is required")
      @Email(message = "email must be valid")
      String email,
      @NotBlank(message = "password is required")
      String password) {}

  /**
   * Admin identity served to Admin Web.
   * Mirrors {@code apps/admin-web/src/auth/AuthContext.tsx} {@code AdminUser}:
   * {@code {id, email, name?, role, scopes}}.
   */
  public record AdminUserDto(
      String id,
      String email,
      String name,
      String role,
      List<String> scopes) {}

  /**
   * Login envelope data: the Supabase access JWT (presented as
   * {@code Bearer} on subsequent calls) plus the resolved admin profile.
   */
  public record LoginResponse(String token, AdminUserDto admin) {}
}
