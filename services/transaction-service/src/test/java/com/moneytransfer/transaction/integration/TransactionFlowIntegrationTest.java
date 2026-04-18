package com.moneytransfer.transaction.integration;

import com.moneytransfer.security.jwt.JwtUtils;
import com.moneytransfer.transaction.dto.request.TransferRequest;
import com.moneytransfer.transaction.dto.response.TransactionResponse;
import com.moneytransfer.transaction.model.OutboxEvent;
import com.moneytransfer.transaction.model.Transaction;
import com.moneytransfer.transaction.repository.OutboxEventRepository;
import com.moneytransfer.transaction.repository.TransactionRepository;
import com.moneytransfer.transaction.service.ITransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.moneytransfer.transaction.enums.TransactionStatus.COMPLETED;
import static com.moneytransfer.transaction.enums.TransactionStatus.PENDING;
import static java.math.BigDecimal.TEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka
@DisplayName("Transaction Flow Integration Tests")
class TransactionFlowIntegrationTest {
    @Autowired
    private ITransactionService transactionService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Nested
    @DisplayName("initiateTransfer")
    class InitiateTransfer {

        @Test
        @Transactional
        @DisplayName("Should create transaction and outbox event atomically")
        void initiateTransfer_AtomicCreation() {
            // Given
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            BigDecimal amount = new BigDecimal("100.0000");

            TransferRequest request = new TransferRequest(receiverId, amount);

            // When
            TransactionResponse response;
            try (MockedStatic<JwtUtils> mockedJwtUtils = mockStatic(JwtUtils.class)) {
                mockedJwtUtils.when(JwtUtils::extractUserId).thenReturn(senderId);
                response = transactionService.initiateTransfer(request);
            }

            // Then - verify transaction created
            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PENDING);

            Transaction transaction = transactionRepository.findById(response.id()).orElseThrow();
            assertThat(transaction.getSenderId()).isEqualTo(senderId);
            assertThat(transaction.getReceiverId()).isEqualTo(receiverId);
            assertThat(transaction.getAmount()).isEqualByComparingTo(amount);

            // Then - verify outbox event created
            List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
            assertThat(outboxEvents).hasSize(1);

            OutboxEvent outbox = outboxEvents.get(0);
            assertThat(outbox.getAggregateType()).isEqualTo("Transaction");
            assertThat(outbox.getAggregateId()).isEqualTo(response.id());
            assertThat(outbox.getEventType()).isEqualTo("initiated.v1");
            assertThat(outbox.getPayload()).isNotEmpty();
        }

        @Test
        @Transactional
        @DisplayName("Should use senderId from JWT")
        void initiateTransfer_UsesSenderIdFromJwt() {
            // Given
            UUID senderId = UUID.randomUUID();
            TransferRequest request = new TransferRequest(UUID.randomUUID(), TEN);

            // When
            TransactionResponse response;
            try (MockedStatic<JwtUtils> mockedJwtUtils = mockStatic(JwtUtils.class)) {
                mockedJwtUtils.when(JwtUtils::extractUserId).thenReturn(senderId);
                response = transactionService.initiateTransfer(request);
            }

            // Then
            Transaction transaction = transactionRepository.findById(response.id()).orElseThrow();
            assertThat(transaction.getSenderId()).isEqualTo(senderId);
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @Transactional
        @DisplayName("Should update status from PENDING to COMPLETED")
        void updateStatus_PendingToCompleted() {
            UUID receiverId = UUID.randomUUID();
            // Given
            UUID senderId = UUID.randomUUID();
            Transaction transaction = transactionRepository.save(
                  new Transaction()
                        .setSenderId(senderId)
                        .setReceiverId(receiverId)
                        .setAmount(TEN)
                        .setStatus(PENDING)
            );

            // When
            transactionService.updateStatus(transaction.getId(), senderId, receiverId, null, COMPLETED);

            // Then
            Transaction updated = transactionRepository.findById(transaction.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(COMPLETED);
        }

        @Test
        @Transactional
        @DisplayName("Should skip update when already in target status")
        void updateStatus_AlreadyCompleted() {
            UUID sederId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();

            // Given
            Transaction transaction = transactionRepository.save(
                  new Transaction()
                        .setSenderId(sederId)
                        .setReceiverId(receiverId)
                        .setAmount(TEN)
                        .setStatus(COMPLETED)
            );

            // When - no exception should be thrown
            transactionService.updateStatus(transaction.getId(), sederId, receiverId, null, COMPLETED);

            // Then
            Transaction unchanged = transactionRepository.findById(transaction.getId()).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo(COMPLETED);
        }
    }
}