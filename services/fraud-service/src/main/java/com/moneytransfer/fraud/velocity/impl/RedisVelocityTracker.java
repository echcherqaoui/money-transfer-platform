package com.moneytransfer.fraud.velocity.impl;

import com.moneytransfer.fraud.velocity.IVelocityTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisVelocityTracker implements IVelocityTracker {

    private static final String KEY_PREFIX = "velocity:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> checkAndRecordVelocityScript;

    private String key(String senderId) {
        return KEY_PREFIX + senderId;
    }

    // Records the request and returns true if velocity is exceeded.
    @Override
    public boolean recordAndCheckVelocity(String senderId,
                                          String eventId,
                                          int maxTransactions,
                                          int windowMinutes) {
        long windowDurationMillis = Duration
              .ofMinutes(windowMinutes)
              .toMillis();

        long windowDurationSec = Duration
              .ofMinutes(windowMinutes)
              .toSeconds();

        long windowStart = Instant.now().toEpochMilli() - windowDurationMillis;

        Long result = redisTemplate.execute(
              checkAndRecordVelocityScript,
              List.of(key(senderId)),
              String.valueOf(Instant.now().toEpochMilli()),  // score
              eventId,                                        // member
              String.valueOf(windowStart),                   // windowStart
              String.valueOf(maxTransactions),               // maxTransactions
              String.valueOf(windowDurationSec)              // TTL
        );

        return Long.valueOf(1L).equals(result);
    }
}