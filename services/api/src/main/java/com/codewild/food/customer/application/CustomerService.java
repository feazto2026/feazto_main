package com.codewild.food.customer.application;

import com.codewild.food.customer.api.CustomerDtos.*;
import com.codewild.food.customer.domain.CustomerProfile;
import com.codewild.food.customer.infrastructure.CustomerProfileRepository;
import com.codewild.food.shared.security.SecurityUtils;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
  private final CustomerProfileRepository profiles;

  public CustomerService(CustomerProfileRepository profiles) { this.profiles = profiles; }

  @Transactional
  public ProfileResponse upsertMyProfile(UpsertProfile req) {
    UUID userId = SecurityUtils.currentUserUuid();
    CustomerProfile p = profiles.findByUserId(userId)
        .orElseGet(() -> new CustomerProfile(userId, req.displayName()));
    p.setDisplayName(req.displayName());
    p.setHometown(req.hometown());
    CustomerProfile saved = profiles.save(p);
    return new ProfileResponse(saved.getIdAsString(), saved.getUserIdAsString(),
        saved.getDisplayName(), saved.getHometown());
  }

  public ProfileResponse myProfile() {
    UUID userId = SecurityUtils.currentUserUuid();
    String userIdStr = userId.toString();
    return profiles.findByUserId(userId)
        .map(p -> new ProfileResponse(p.getIdAsString(), p.getUserIdAsString(),
            p.getDisplayName(), p.getHometown()))
        .orElse(new ProfileResponse(null, userIdStr, null, null));
  }
}
