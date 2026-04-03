package com.moneytransfer.transaction.integration;

import com.moneytransfer.transaction.model.Transaction;
import com.moneytransfer.transaction.properties.TransactionProperties;
import com.moneytransfer.transaction.repository.TransactionRepository;
import com.moneytransfer.transaction.scheduler.TimeoutCompensationJob;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.moneytransfer.transaction.enums.TransactionStatus.EXPIRED;
import static com.moneytransfer.transaction.enums.TransactionStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka
@DisplayName("Timeout Compensation Integration Tests")
class TimeoutCompensationIntegrationTest {
    @Autowired
    private TimeoutCompensationJob timeoutCompensationJob;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private TransactionProperties transactionProperties;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcOperations jdbcTemplate;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @Transactional
    @DisplayName("Should mark old PENDING transactions as EXPIRED")
    void markStuckTransactions_Success() {
        // Given
        long timeoutMinutes = transactionProperties.getTimeoutMinutes();

        Transaction oldTransaction = transactionRepository.save(
              new Transaction()
                    .setSenderId(UUID.randomUUID())
                    .setReceiverId(UUID.randomUUID())
                    .setAmount(new BigDecimal("100.0000"))
                    .setStatus(PENDING)
        );

        // Force old created_at via native query (bypass @CreatedDate)
        transactionRepository.flush();
        OffsetDateTime oldTime = OffsetDateTime.now().minusMinutes(timeoutMinutes + 1);

        // Simulate old transaction - Implementation of the comment:
        jdbcTemplate.update("UPDATE transactions SET created_at = ? WHERE id = ?", oldTime, oldTransaction.getId());

        // When
        timeoutCompensationJob.markStuckTransactionsFailed();

        // Then
        // 1. Refresh context to see DB changes
        entityManager.clear();

        Transaction updated = transactionRepository.findById(oldTransaction.getId()).orElseThrow();

        // 2. State Assertions
        assertThat(updated.getStatus()).isEqualTo(EXPIRED);

        assertThat(updated.getUpdatedAt()).isAfter(oldTime);
    }

    @Test
    @Transactional
    @DisplayName("Should not mark recent PENDING transactions")
    void markStuckTransactions_SkipRecent() {
        // Given
        Transaction recentTransaction = transactionRepository.save(
              new Transaction()
                    .setSenderId(UUID.randomUUID())
                    .setReceiverId(UUID.randomUUID())
                    .setAmount(new BigDecimal("100.0000"))
                    .setStatus(PENDING)
        );

        // When
        timeoutCompensationJob.markStuckTransactionsFailed();

        // Then
        Transaction unchanged = transactionRepository.findById(recentTransaction.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(PENDING);
    }
}