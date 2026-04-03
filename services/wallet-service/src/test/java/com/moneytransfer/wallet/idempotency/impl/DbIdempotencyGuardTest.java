package com.moneytransfer.wallet.idempotency.impl;

import com.moneytransfer.wallet.model.ProcessedEvent;
import com.moneytransfer.wallet.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DbIdempotencyGuard Unit Tests")
class DbIdempotencyGuardTest {
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @InjectMocks
    private DbIdempotencyGuard dbIdempotencyGuard;

    @Test
    @DisplayName("Should return false on first processing (insert succeeds)")
    void isProcessed_FirstTime() {
        // Given
        String eventId = "event-123";
        // Must return false for first time
        when(processedEventRepository.existsById(eventId))
              .thenReturn(false);

        // Stub saveAndFlush, not save
        when(processedEventRepository.save(any(ProcessedEvent.class)))
              .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        boolean result = dbIdempotencyGuard.isProcessed(eventId);

        // Then
        assertThat(result).isFalse();
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Should return true on duplicate (PK constraint violation)")
    void isProcessed_Duplicate() {
        // Given
        String eventId = "event-123";

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        // When
        boolean result = dbIdempotencyGuard.isProcessed(eventId);

        // Then
        assertThat(result).isTrue();

        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Should handle different event IDs independently")
    void isProcessed_DifferentEventIds() {
        // Given
        String eventId1 = "event-1";
        String eventId2 = "event-2";

        // Stub the initial checks for both IDs to return false
        when(processedEventRepository.existsById(eventId1))
              .thenReturn(false);

        when(processedEventRepository.existsById(eventId2))
              .thenReturn(false);

        when(processedEventRepository.save(any(ProcessedEvent.class)))
              .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        boolean result1 = dbIdempotencyGuard.isProcessed(eventId1);
        boolean result2 = dbIdempotencyGuard.isProcessed(eventId2);

        // Then
        assertThat(result1).isFalse();
        assertThat(result2).isFalse();

        // Verify save was called exactly twice
        verify(processedEventRepository, times(2)).save(any(ProcessedEvent.class));
    }
}