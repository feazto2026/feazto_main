package com.codewild.food.shared.idempotency;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {
  private final StringRedisTemplate redis;

  public IdempotencyService(StringRedisTemplate redis) { this.redis = redis; }

  /** Returns true if this key was already seen (duplicate). First call records and returns false. */
  public boolean isDuplicate(String operation, String key) {
    if (key == null || key.isBlank()) return false;
    String rk = "idempotency:" + operation + ":" + key;
    try {
      Boolean absent = redis.opsForValue().setIfAbsent(rk, "1", Duration.ofHours(24));
      return Boolean.FALSE.equals(absent) ? true : !absent;
    } catch (Exception e) {
      return false; // fail-open: Redis is supporting infra, DB unique constraints are backstop
    }
  }
}
