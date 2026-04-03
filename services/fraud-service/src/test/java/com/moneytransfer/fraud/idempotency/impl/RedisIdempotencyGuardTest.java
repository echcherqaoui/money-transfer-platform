package com.moneytransfer.fraud.idempotency.impl;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisIdempotencyGuard Unit Tests")
class RedisIdempotencyGuardTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private DefaultRedisScript<Long> setIdempotencyKeyScript;
    private RedisIdempotencyGuard idempotencyGuard;

    private final long ttlSeconds = 86400L;
    private final String pendingStatus = "PENDING";

    @BeforeEach
    void setUp() {
        idempotencyGuard = new RedisIdempotencyGuard(
              redisTemplate,
              setIdempotencyKeyScript,
              ttlSeconds
        );

        // Default to first occurrence success
        lenient().when(redisTemplate.execute(any(), anyList(), anyString(), anyString()))
              .thenReturn(1L);
    }

    private String getExpectedKey(String eventId) {
        return "idempotency:fraud:" + eventId;
    }

    @Nested
    @DisplayName("isFirstOccurrence")
    class IsFirstOccurrence {

        @Test
        @DisplayName("Should return true on first occurrence")
        void isFirstOccurrence_FirstTime() {
            String eventId = UUID.randomUUID().toString();

            boolean result = idempotencyGuard.isFirstOccurrence(eventId);

            assertThat(result).isTrue();
            verify(redisTemplate).execute(
                  setIdempotencyKeyScript,
                  List.of(getExpectedKey(eventId)),
                  pendingStatus,
                  String.valueOf(ttlSeconds)
            );
        }

        @Test
        @DisplayName("Should return false on duplicate")
        void isFirstOccurrence_Duplicate() {
            String eventId = UUID.randomUUID().toString();
            when(redisTemplate.execute(any(), anyList(), anyString(), anyString()))
                  .thenReturn(0L);

            boolean result = idempotencyGuard.isFirstOccurrence(eventId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should use correct Redis key prefix and parameters")
        void isFirstOccurrence_CorrectParameters() {
            String eventId = "test-event-123";
            String expectedKey = getExpectedKey(eventId);

            idempotencyGuard.isFirstOccurrence(eventId);

            verify(redisTemplate).execute(
                  setIdempotencyKeyScript,
                  List.of(expectedKey),
                  pendingStatus,
                  String.valueOf(ttlSeconds)
            );
        }

        @Test
        @DisplayName("Should handle different event IDs independently")
        void isFirstOccurrence_DifferentEventIds() {
            String eventId1 = "event-1";
            String eventId2 = "event-2";

            boolean result1 = idempotencyGuard.isFirstOccurrence(eventId1);
            boolean result2 = idempotencyGuard.isFirstOccurrence(eventId2);

            assertThat(result1).isTrue();
            assertThat(result2).isTrue();
            verify(redisTemplate).execute(any(), eq(List.of(getExpectedKey(eventId1))), anyString(), anyString());
            verify(redisTemplate).execute(any(), eq(List.of(getExpectedKey(eventId2))), anyString(), anyString());
        }
    }
}