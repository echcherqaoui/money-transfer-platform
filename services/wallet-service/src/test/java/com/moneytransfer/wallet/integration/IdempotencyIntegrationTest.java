package com.moneytransfer.wallet.integration;

import com.moneytransfer.wallet.idempotency.IIdempotencyGuard;
import com.moneytransfer.wallet.model.PendingTransfer;
import com.moneytransfer.wallet.model.ProcessedEvent;
import com.moneytransfer.wallet.repository.PendingTransferRepository;
import com.moneytransfer.wallet.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;
import static java.lang.Boolean.FALSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka
@DisplayName("Idempotency Integration Tests")
class IdempotencyIntegrationTest {
    @Autowired
    private IIdempotencyGuard idempotencyGuard;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private PendingTransferRepository pendingTransferRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
        pendingTransferRepository.deleteAll();
    }

    @Test
    @DisplayName("Should detect duplicate on second call")
    void idempotencyGuard_DetectsDuplicate() {
        // Given
        String eventId = UUID.randomUUID().toString();

        // When
        boolean firstCall = idempotencyGuard.isProcessed(eventId);
        boolean secondCall = idempotencyGuard.isProcessed(eventId);

        // Then
        assertThat(firstCall).isFalse(); // First time - proceed
        assertThat(secondCall).isTrue();  // Duplicate - skip

        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle concurrent duplicate detection correctly")
    void idempotencyGuard_ConcurrentDuplicates() throws InterruptedException {
        // Given
        String eventId = UUID.randomUUID().toString();
        int threadCount = 10;

        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // When - simulate concurrent processing attempts
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Boolean result = transactionTemplate.execute(status ->
                          idempotencyGuard.isProcessed(eventId)
                    );

                    if (FALSE.equals(result))
                        successCount.incrementAndGet();
                    else
                        duplicateCount.incrementAndGet();
                } catch (Exception e) {
                    duplicateCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, SECONDS);
        executor.shutdown();

        // Then - exactly one success, rest are duplicates
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(threadCount - 1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should rollback idempotency record on transaction failure")
    void idempotencyGuard_RollbackOnFailure() {
        // Given
        String eventId = UUID.randomUUID().toString();

        // When - transaction that fails after idempotency check
        try {
            transactionTemplate.execute(status -> {
                boolean isProcessed = idempotencyGuard.isProcessed(eventId);

                if (!isProcessed) {
                    // Simulate business logic
                    PendingTransfer pending = new PendingTransfer()
                          .setTransactionId(UUID.randomUUID())
                          .setSenderId(UUID.randomUUID())
                          .setReceiverId(UUID.randomUUID())
                          .setAmount(new BigDecimal("100.0000"))
                          .setStatus(INITIATED);

                    pendingTransferRepository.save(pending);

                    // Force rollback
                    throw new RuntimeException("Simulated failure");
                }
                return null;
            });
        } catch (RuntimeException e) {
            // Expected
        }

        // Then - both idempotency and business operation rolled back
        assertThat(processedEventRepository.count()).isZero();
        assertThat(pendingTransferRepository.count()).isZero();

        // And - retry should succeed
        boolean retryResult = Boolean.TRUE.equals(transactionTemplate.execute(status ->
              idempotencyGuard.isProcessed(eventId)
        ));

        assertThat(retryResult).isFalse(); // Not duplicate after rollback
    }

    @Test
    @Transactional
    @DisplayName("Should distinguish different event IDs")
    void idempotencyGuard_DifferentEventIds() {
        // Given
        String eventId1 = UUID.randomUUID().toString();
        String eventId2 = UUID.randomUUID().toString();
        String eventId3 = UUID.randomUUID().toString();

        // When
        boolean result1 = idempotencyGuard.isProcessed(eventId1);
        boolean result2 = idempotencyGuard.isProcessed(eventId2);
        boolean result3 = idempotencyGuard.isProcessed(eventId3);

        // Then
        assertThat(result1).isFalse();
        assertThat(result2).isFalse();
        assertThat(result3).isFalse();

        assertThat(processedEventRepository.count()).isEqualTo(3);
    }

    @Test
    @Transactional
    @DisplayName("Should persist idempotency record permanently")
    void idempotencyGuard_PermanentRecord() {
        // Given
        String eventId = UUID.randomUUID().toString();

        // When
        idempotencyGuard.isProcessed(eventId);

        // Then - verify persisted
        ProcessedEvent processed = processedEventRepository.findById(eventId).orElseThrow();
        assertThat(processed.getEventId()).isEqualTo(eventId);
        assertThat(processed.getProcessedAt()).isNotNull();

        // And - still detected as duplicate after flush
        processedEventRepository.flush();

        boolean stillDuplicate = idempotencyGuard.isProcessed(eventId);
        assertThat(stillDuplicate).isTrue();
    }
}