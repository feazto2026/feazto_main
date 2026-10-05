package com.codewild.food.notification.api;

import com.codewild.food.notification.application.NotificationService;
import com.codewild.food.notification.domain.NotificationEntity;
import com.codewild.food.shared.errors.ApiResponse;
import com.codewild.food.shared.observability.RequestIdFilter;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService service;
  public NotificationController(NotificationService service) { this.service = service; }

  @GetMapping("/me")
  public ApiResponse<List<NotificationEntity>> mine() {
    return ApiResponse.ok(service.myNotifications(), MDC.get(RequestIdFilter.REQUEST_ID_MDC));
  }
}
