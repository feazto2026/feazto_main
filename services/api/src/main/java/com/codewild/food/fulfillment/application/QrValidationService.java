package com.codewild.food.fulfillment.application;

import java.security.SecureRandom;
import org.springframework.stereotype.Service;

/**
 * Server-side handover-code minting + comparison.
 *
 * <p>Codes are 6-digit CSPRNG values. Only {@code SHA-256} hashes with expiry
 * are persisted (see {@code Delivery.setPickupCode/setDeliveryCode} and
 * {@code DeliveryAssignmentService.isPickupValid/isDeliveryValid} — Supabase
 * 0006 §12: never plaintext). This service keeps a stateless plaintext
 * comparator only as a bridge for pre-hash rows.
 */
@Service
public class QrValidationService {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  public boolean validatePickup(String expected, String provided) {
    if (expected == null || provided == null) {
      return false;
    }
    return expected.trim().equalsIgnoreCase(provided.trim());
  }

  public boolean validateDelivery(String expected, String provided) {
    if (expected == null || provided == null) {
      return false;
    }
    return expected.trim().equalsIgnoreCase(provided.trim());
  }

  /** 6-digit zero-padded CSPRNG code; caller must hash + expire on persist. */
  public String newCode() {
    return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
  }
}
