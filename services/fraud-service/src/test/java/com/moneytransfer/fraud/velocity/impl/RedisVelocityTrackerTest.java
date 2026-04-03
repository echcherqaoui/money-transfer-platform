package com.moneytransfer.fraud.velocity.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisVelocityTracker Unit Tests")
class RedisVelocityTrackerTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private DefaultRedisScript<Long> checkAndRecordVelocityScript;
    private RedisVelocityTracker velocityTracker;

    @BeforeEach
    void setUp() {
        velocityTracker = new RedisVelocityTracker(
              redisTemplate,
              checkAndRecordVelocityScript
        );

        // Default: Script returns 0 (within limit)
        lenient().when(redisTemplate.execute(any(), anyList(), anyString(), anyString(),
                    anyString(), anyString(), anyString()))
              .thenReturn(0L);
    }

    private String getExpectedKey(String senderId) {
        return "velocity:" + senderId;
    }

    @Nested
    @DisplayName("record and check velocity method")
    class RecordAndCheckVelocity {

        @Test
        @DisplayName("Should return false when within limit")
        void recordAndCheckVelocity_WithinLimit() {
            boolean exceeded = velocityTracker.recordAndCheckVelocity(
                  UUID.randomUUID().toString(), UUID.randomUUID().toString(), 5, 10
            );

            assertThat(exceeded).isFalse();
        }

        @Test
        @DisplayName("Should return true when limit exceeded")
        void recordAndCheckVelocity_LimitExceeded() {
            when(redisTemplate.execute(any(), anyList(), anyString(), anyString(),
                  anyString(), anyString(), anyString()))
                  .thenReturn(1L);

            boolean exceeded = velocityTracker.recordAndCheckVelocity(
                  UUID.randomUUID().toString(), UUID.randomUUID().toString(), 5, 10
            );

            assertThat(exceeded).isTrue();
        }

        @Test
        @DisplayName("Should use correct Redis key and pass parameters")
        void recordAndCheckVelocity_CorrectParameters() {
            String senderId = "user-123";
            String eventId = "event-456";
            int maxTransactions = 3;
            int windowMinutes = 5;
            String expectedTtl = "300"; // 5 min * 60

            velocityTracker.recordAndCheckVelocity(senderId, eventId, maxTransactions, windowMinutes);

            verify(redisTemplate).execute(
                  eq(checkAndRecordVelocityScript),
                  eq(List.of(getExpectedKey(senderId))),
                  anyString(),      // score/timestamp
                  eq(eventId),      // member
                  anyString(),      // windowStart
                  eq(String.valueOf(maxTransactions)),
                  eq(expectedTtl)
            );
        }

        @Test
        @DisplayName("Should handle multiple calls for same sender")
        void recordAndCheckVelocity_ConsecutiveCalls() {
            String senderId = "sender-1";

            velocityTracker.recordAndCheckVelocity(senderId, "e1", 5, 10);
            velocityTracker.recordAndCheckVelocity(senderId, "e2", 5, 10);

            verify(redisTemplate, times(2)).execute(
                  any(),
                  eq(List.of(getExpectedKey(senderId))),
                  anyString(), anyString(), anyString(), anyString(), anyString()
            );
        }
    }
}