package com.learnai.service;

import com.learnai.common.ApiException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-user burst protection with Bucket4j (in-memory; use bucket4j-redis when running multiple instances). */
@Service
public class RateLimitService {
  private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();
  @Value("${app.ai.per-minute-limit}") private int perMinute;

  public void check(Long userId) {
    Bucket b = buckets.computeIfAbsent(userId, id -> Bucket.builder()
        .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
        .build());
    if (!b.tryConsume(1)) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many AI requests. Please wait a moment.");
  }
}
