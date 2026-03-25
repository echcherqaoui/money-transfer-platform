package com.moneytransfer.fraud.idempotency.impl;

import com.moneytransfer.fraud.idempotency.IIdempotencyGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisIdempotencyGuard implements IIdempotencyGuard {
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> setIdempotencyKeyScript;
    private final long ttlSeconds;

    private static final String KEY_PREFIX = "idempotency:fraud:";

    public RedisIdempotencyGuard(StringRedisTemplate redisTemplate,
                            DefaultRedisScript<Long> setIdempotencyKeyScript,
                            @Value("${fraud.idempotency.ttl-seconds:86400}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.setIdempotencyKeyScript = setIdempotencyKeyScript;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Returns true if this eventId is being seen for the first time.
     * Returns false if it was already processed.
     */
    @Override
    public boolean isFirstOccurrence(String eventId) {
        Long result = redisTemplate.execute(
                setIdempotencyKeyScript,
                List.of(KEY_PREFIX + eventId),
                "PENDING",
                String.valueOf(ttlSeconds)
        );

        return Long.valueOf(1L).equals(result);
    }
}