package com.moneytransfer.transaction.scheduler;

import com.moneytransfer.transaction.properties.TransactionProperties;
import com.moneytransfer.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static com.moneytransfer.transaction.enums.TransactionStatus.EXPIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimeoutCompensationJob Unit Tests")
class TimeoutCompensationJobTest {
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionProperties transactionProperties;
    @InjectMocks
    private TimeoutCompensationJob timeoutCompensationJob;

    @BeforeEach
    void setUp() {
        when(transactionProperties.getTimeoutMinutes()).thenReturn(2L);
    }

    @Nested
    @DisplayName("mark stuck transactions failed method")
    class MarkStuckTransactionsFailed {

        @Test
        @DisplayName("Should mark stuck transactions as EXPIRED")
        void markStuckTransactionsFailed_Success() {
            // Given
            when(transactionRepository.markStuckTransactionsAs(eq(EXPIRED), any(OffsetDateTime.class)))
                  .thenReturn(3);

            // When
            timeoutCompensationJob.markStuckTransactionsFailed();

            // Then
            verify(transactionRepository).markStuckTransactionsAs(eq(EXPIRED), any(OffsetDateTime.class));
        }

        @Test
        @DisplayName("Should calculate threshold correctly")
        void markStuckTransactionsFailed_ThresholdCalculation() {
            // Given
            when(transactionRepository.markStuckTransactionsAs(eq(EXPIRED), any(OffsetDateTime.class)))
                  .thenReturn(0);

            OffsetDateTime before = OffsetDateTime.now().minusMinutes(2).minusSeconds(5);

            // When
            timeoutCompensationJob.markStuckTransactionsFailed();

            OffsetDateTime after = OffsetDateTime.now().minusMinutes(2).plusSeconds(5);

            // Then
            ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
            verify(transactionRepository).markStuckTransactionsAs(eq(EXPIRED), captor.capture());

            OffsetDateTime threshold = captor.getValue();
            assertThat(threshold).isBetween(before, after);
        }

        @Test
        @DisplayName("Should use configured timeout minutes")
        void markStuckTransactionsFailed_UsesConfiguredTimeout() {
            // Given
            when(transactionProperties.getTimeoutMinutes()).thenReturn(10L);
            when(transactionRepository.markStuckTransactionsAs(eq(EXPIRED), any(OffsetDateTime.class)))
                  .thenReturn(0);

            OffsetDateTime expectedThreshold = OffsetDateTime.now().minusMinutes(10);

            // When
            timeoutCompensationJob.markStuckTransactionsFailed();

            // Then
            ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
            verify(transactionRepository).markStuckTransactionsAs(eq(EXPIRED), captor.capture());

            assertThat(captor.getValue())
                  .isCloseTo(expectedThreshold, within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Should handle zero updates gracefully")
        void markStuckTransactionsFailed_ZeroUpdates() {
            // Given
            when(transactionRepository.markStuckTransactionsAs(eq(EXPIRED), any(OffsetDateTime.class)))
                  .thenReturn(0);

            // When / Then - should not throw
            timeoutCompensationJob.markStuckTransactionsFailed();

            verify(transactionRepository).markStuckTransactionsAs(eq(EXPIRED), any(OffsetDateTime.class));
        }
    }
}