package com.codewild.food.identity.api;

import com.codewild.food.identity.api.AuthDtos.AuthResponse;
import com.codewild.food.identity.api.AuthDtos.OtpRequest;
import com.codewild.food.identity.api.AuthDtos.OtpVerify;
import com.codewild.food.identity.application.AuthService;
import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import com.codewild.food.shared.security.PlatformUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public OTP entry points + authenticated identity lookup.
 *
 * <p>Enumeration resistance: {@code request}/{@code resend} always return the same
 * generic message whether or not the phone exists. Throttling and cooldowns are
 * enforced in {@code AuthService} per {@code OtpSecurityPolicy}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;
  public AuthController(AuthService service) { this.service = service; }

  /** Canonical request path (see openapi + mobile-shared). */
  @PostMapping("/otp/request")
  public ApiResponse<Void> requestOtp(@Valid @RequestBody OtpRequest req, HttpServletRequest http) {
    service.requestOtp(req, clientIp(http));
    return ApiResponse.ok(null, "If the phone number is valid, a code has been sent",
        MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  /** Legacy alias for {@code /otp/request} (kept for deployed clients; same policy). */
  @PostMapping("/otp/send")
  public ApiResponse<Void> sendOtp(@Valid @RequestBody OtpRequest req, HttpServletRequest http) {
    service.requestOtp(req, clientIp(http));
    return ApiResponse.ok(null, "If the phone number is valid, a code has been sent",
        MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  @PostMapping("/otp/resend")
  public ApiResponse<Void> resendOtp(@Valid @RequestBody OtpRequest req, HttpServletRequest http) {
    service.resendOtp(req, clientIp(http));
    return ApiResponse.ok(null, "If the phone number is valid, a code has been sent",
        MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  @PostMapping("/otp/verify")
  public ApiResponse<AuthResponse> verify(@Valid @RequestBody OtpVerify req) {
    return ApiResponse.ok(service.verifyOtp(req), "Authenticated",
        MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }

  /** Authenticated identity: roles + verification status resolved server-side. */
  @GetMapping("/me")
  public ApiResponse<Map<String, Object>> me(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof PlatformUser u) {
      Map<String, Object> data = Map.of(
          "userId", u.getUserId().toString(),
          "roles", u.getRoles().stream().map(Enum::name).sorted().toList(),
          "phoneMasked", u.getPhoneMasked(),
          "vendorStatus", String.valueOf(u.getVendorStatus()),
          "riderStatus", String.valueOf(u.getRiderStatus()));
      return ApiResponse.ok(data, "OK", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
    }
    // Fail closed: no usable principal (filter should have rejected earlier).
    org.springframework.security.access.AccessDeniedException denied =
        new org.springframework.security.access.AccessDeniedException("Unauthenticated");
    throw denied;
  }

  @PostMapping("/refresh")
  @ResponseStatus(HttpStatus.GONE)
  public ApiResponse<Void> refreshGone() {
    // Refresh is a Supabase Auth concern (see auth-refresh.ts); the backend is stateless.
    return ApiResponse.ok(null, "Use Supabase Auth refresh", MDC.get(RequestIdFilter.REQUEST_ID_MDC));
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
