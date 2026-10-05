package com.codewild.food.marketplace.application;

import com.codewild.food.marketplace.api.MarketplaceDtos.VendorResponse;
import com.codewild.food.marketplace.domain.VendorProfile;
import org.springframework.stereotype.Component;

@Component
public class VendorMapper {
  public VendorResponse toResponse(VendorProfile v) {
    return new VendorResponse(v.getIdAsString(), v.getKitchenName(), v.getCity(), v.getStatus());
  }
}
