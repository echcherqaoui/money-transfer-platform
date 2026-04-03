package com.moneytransfer.transaction.service.impl;

import com.moneytransfer.security.jwt.JwtUtils;
import com.moneytransfer.transaction.dto.TransferRequest;
import com.moneytransfer.transaction.dto.TransferResponse;
import com.moneytransfer.transaction.exception.ConcurrentUpdateException;
import com.moneytransfer.transaction.exception.TransactionNotFoundException;
import com.moneytransfer.transaction.mapper.TransactionMapper;
import com.moneytransfer.transaction.model.Transaction;
import com.moneytransfer.transaction.repository.TransactionRepository;
import com.moneytransfer.transaction.service.ITransactionOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.moneytransfer.transaction.enums.TransactionStatus.COMPLETED;
import static com.moneytransfer.transaction.enums.TransactionStatus.EXPIRED;
import static com.moneytransfer.transaction.enums.TransactionStatus.FAILED;
import static com.moneytransfer.transaction.enums.TransactionStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionServiceImpl Unit Tests")
class TransactionServiceImplTest {
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ITransactionOutboxService outboxService;
    @Mock
    private TransactionMapper transactionMapper;
    @InjectMocks
    private TransactionServiceImpl transactionService;

    private UUID senderId;
    private UUID receiverId;
    private UUID transactionId;
    private BigDecimal amount;
    private TransferRequest request;
    private Transaction mappedTransaction;
    private Transaction savedTransaction;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        amount = new BigDecimal("100.0000");

        request = new TransferRequest(receiverId, amount);

        mappedTransaction = new Transaction()
              .setReceiverId(receiverId)
              .setAmount(amount);

        savedTransaction = new Transaction()
              .setId(transactionId)
              .setSenderId(senderId)
              .setReceiverId(receiverId)
              .setAmount(amount)
              .setStatus(PENDING);
    }

    @Nested
    @DisplayName("initiate transfer method")
    class InitiateTransfer {
        @Test
        @DisplayName("Should initiate transfer successfully - happy path")
        void initiateTransfer_Success() {
            try (MockedStatic<JwtUtils> mockedJwtUtils = mockStatic(JwtUtils.class)) {
                mockedJwtUtils.when(JwtUtils::extractUserId).thenReturn(senderId);

                when(transactionMapper.fromTransferRequest(request))
                      .thenReturn(mappedTransaction);
                when(transactionRepository.save(any(Transaction.class)))
                      .thenReturn(savedTransaction);
                doNothing().when(outboxService).publishTransferInitiated(any(Transaction.class));

                // When
                TransferResponse response = transactionService.initiateTransfer(request);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.senderId()).isEqualTo(senderId);
                assertThat(response.receiverId()).isEqualTo(receiverId);
                assertThat(response.amount()).isEqualByComparingTo(amount);
                assertThat(response.status()).isEqualTo(PENDING);

                ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
                verify(transactionRepository).save(captor.capture());

                Transaction captured = captor.getValue();
                assertThat(captured.getSenderId()).isEqualTo(senderId);
                assertThat(captured.getStatus()).isEqualTo(PENDING);
            }
        }

        @Test
        @DisplayName("Should extract senderId from JWT")
        void initiateTransfer_ExtractsSenderId() {
            try (MockedStatic<JwtUtils> mockedJwtUtils = mockStatic(JwtUtils.class)) {
                mockedJwtUtils.when(JwtUtils::extractUserId)
                      .thenReturn(senderId);

                when(transactionMapper.fromTransferRequest(request))
                      .thenReturn(mappedTransaction);

                when(transactionRepository.save(any(Transaction.class)))
                      .thenReturn(savedTransaction);

                // When
                transactionService.initiateTransfer(request);

                // Then
                mockedJwtUtils.verify(JwtUtils::extractUserId);

                ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
                verify(transactionRepository).save(captor.capture());
                assertThat(captor.getValue().getSenderId()).isEqualTo(senderId);
            }
        }

        @Test
        @DisplayName("Should set status to PENDING")
        void initiateTransfer_SetsPendingStatus() {
            try (MockedStatic<JwtUtils> mockedJwtUtils = mockStatic(JwtUtils.class)) {
                mockedJwtUtils.when(JwtUtils::extractUserId)
                      .thenReturn(senderId);

                when(transactionMapper.fromTransferRequest(request))
                      .thenReturn(mappedTransaction);

                when(transactionRepository.save(any(Transaction.class)))
                      .thenReturn(savedTransaction);

                // When
                transactionService.initiateTransfer(request);

                // Then
                ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
                verify(transactionRepository).save(captor.capture());
                assertThat(captor.getValue().getStatus()).isEqualTo(PENDING);
            }
        }

        @Test
        @DisplayName("Should publish outbox event after saving transaction")
        void initiateTransfer_PublishesOutboxEvent() {
            try (MockedStatic<JwtUtils> mockedJwtUtils = mockStatic(JwtUtils.class)) {
                mockedJwtUtils.when(JwtUtils::extractUserId)
                      .thenReturn(senderId);

                when(transactionMapper.fromTransferRequest(request))
                      .thenReturn(mappedTransaction);

                when(transactionRepository.save(any(Transaction.class)))
                      .thenReturn(savedTransaction);

                // When
                transactionService.initiateTransfer(request);

                // Then
                verify(transactionRepository).save(any(Transaction.class));
                verify(outboxService).publishTransferInitiated(savedTransaction);
            }
        }
    }

    @Nested
    @DisplayName("get transfer method")
    class GetTransfer {
        @Test
        @DisplayName("Should return transfer when found")
        void getTransfer_Found() {
            // Given
            when(transactionRepository.findById(transactionId))
                  .thenReturn(Optional.of(savedTransaction));

            // When
            TransferResponse response = transactionService.getTransfer(transactionId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(transactionId);
            assertThat(response.status()).isEqualTo(PENDING);
        }

        @Test
        @DisplayName("Should throw exception when transaction not found")
        void getTransfer_NotFound() {
            // Given
            when(transactionRepository.findById(transactionId))
                  .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> transactionService.getTransfer(transactionId))
                  .isInstanceOf(TransactionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update status method")
    class UpdateStatus {

        @Test
        @DisplayName("Should update status successfully when transaction is PENDING")
        void updateStatus_Success() {
            // Given
            when(transactionRepository.atomicStatusUpdate(COMPLETED, transactionId, PENDING))
                  .thenReturn(1);

            // When
            transactionService.updateStatus(transactionId, COMPLETED);

            // Then
            verify(transactionRepository).atomicStatusUpdate(COMPLETED, transactionId, PENDING);
        }

        @Test
        @DisplayName("Should skip update when already in target status")
        void updateStatus_AlreadyInTargetStatus() {
            // Given
            savedTransaction.setStatus(COMPLETED);

            when(transactionRepository.atomicStatusUpdate(COMPLETED, transactionId, PENDING))
                  .thenReturn(0);

            when(transactionRepository.findById(transactionId))
                  .thenReturn(Optional.of(savedTransaction));

            // When
            transactionService.updateStatus(transactionId, COMPLETED);

            // Then
            verify(transactionRepository).findById(transactionId);
        }

        @Test
        @DisplayName("Should throw ConcurrentUpdateException on invalid state transition")
        void updateStatus_InvalidStateTransition() {
            // Given
            savedTransaction.setStatus(FAILED);

            when(transactionRepository.atomicStatusUpdate(COMPLETED, transactionId, PENDING))
                  .thenReturn(0);

            when(transactionRepository.findById(transactionId))
                  .thenReturn(Optional.of(savedTransaction));

            // When / Then
            assertThatThrownBy(() -> transactionService.updateStatus(transactionId, COMPLETED))
                  .isInstanceOf(ConcurrentUpdateException.class);
        }

        @Test
        @DisplayName("Should throw TransactionNotFoundException when transaction doesn't exist")
        void updateStatus_TransactionNotFound() {
            // Given
            when(transactionRepository.atomicStatusUpdate(COMPLETED, transactionId, PENDING))
                  .thenReturn(0);

            when(transactionRepository.findById(transactionId))
                  .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> transactionService.updateStatus(transactionId, COMPLETED))
                  .isInstanceOf(TransactionNotFoundException.class);
        }

        @Test
        @DisplayName("Should handle PENDING to FAILED transition")
        void updateStatus_PendingToFailed() {
            // Given
            when(transactionRepository.atomicStatusUpdate(FAILED, transactionId, PENDING))
                  .thenReturn(1);

            // When
            transactionService.updateStatus(transactionId, FAILED);

            // Then
            verify(transactionRepository).atomicStatusUpdate(FAILED, transactionId, PENDING);
        }

        @Test
        @DisplayName("Should handle PENDING to EXPIRED transition")
        void updateStatus_PendingToExpired() {
            // Given
            when(transactionRepository.atomicStatusUpdate(EXPIRED, transactionId, PENDING))
                  .thenReturn(1);

            // When
            transactionService.updateStatus(transactionId, EXPIRED);

            // Then
            verify(transactionRepository).atomicStatusUpdate(EXPIRED, transactionId, PENDING);
        }
    }
}