package com.codewild.food.operations.api;

import com.codewild.food.operations.api.OperationsDtos.AdminUserDto;
import com.codewild.food.operations.api.OperationsDtos.LoginRequest;
import com.codewild.food.operations.api.OperationsDtos.LoginResponse;
import com.codewild.food.operations.application.AdminAuthService;
import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import com.codewild.food.shared.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Web authentication endpoints.
 *
 * <p>Contract (see {@code apps/admin-web/src/auth/AuthContext.tsx}):
 * <ul>
 *   <li>{@code POST /api/v1/admin/auth/login {email, password}} is PUBLIC and
 *       returns envelope {@code data {token, admin}} where {@code admin} is
 *       {@code {id, email, name, role, scopes}}.</li>
 *   <li>{@code GET /api/v1/admin/auth/me} is authenticated (admin-family roles
 *       only) and returns envelope {@code data AdminUser}.</li>
 * </ul>
 * Both use the standard {@link ApiResponse} envelope; errors use
 * {@code {success:false, error:{code, message}, requestId}}.
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
  private final AdminAuthService service;
  public AdminAuthController(AdminAuthService service) { this.service = service; }

  /**
   * Public password login. Supabase Auth (GoTrue) is the password truth;
   * throttled per IP + email in {@link AdminAuthService}.
   */
  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(
      @Valid @RequestBody LoginRequest req, HttpServletRequest http) {
    LoginResponse data = service.login(req.email(), req.password(), clientIp(http));
    return ApiResponse.ok(data, "Authenticated", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  /** Authenticated admin profile for Admin Web rehydration. */
  @GetMapping("/me")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPS_ADMIN','FINANCE_ADMIN','SUPPORT_ADMIN')")
  public ApiResponse<AdminUserDto> me() {
    AdminUserDto data = service.me(SecurityUtils.currentUser());
    return ApiResponse.ok(data, "OK", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  private static String clientIp(HttpServletRequest http) {
    if (http == null) {
      return "unknown";
    }
    String xff = http.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      String first = xff.split(",")[0].trim();
      if (!first.isBlank()) {
        return first;
      }
    }
    String remote = http.getRemoteAddr();
    return remote == null || remote.isBlank() ? "unknown" : remote.trim();
  }
}
