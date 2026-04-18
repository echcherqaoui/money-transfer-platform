package com.moneytransfer.wallet.service.impl;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.FraudDetected;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.contract.TransferApproved;
import com.moneytransfer.wallet.idempotency.IIdempotencyGuard;
import com.moneytransfer.wallet.service.ISettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.moneytransfer.wallet.enums.PendingStatus.DISCARDED;
import static com.moneytransfer.wallet.enums.PendingStatus.EXPIRED;
import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferOrchestrator Unit Tests")
class TransferOrchestratorTest {
    @Mock
    private IIdempotencyGuard idempotencyGuard;
    @Mock
    private PendingTransferService pendingTransferService;
    @Mock
    private ISettlementService settlementService;
    @InjectMocks
    private TransferOrchestrator transferOrchestrator;

    private String eventId;
    private UUID transactionId;
    private UUID senderId;
    private UUID receiverId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID().toString();
        transactionId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("handleInitiated Method")
    class HandleInitiated {

        @Test
        @DisplayName("Should handle MoneyTransferInitiated - INITIATED status when not expired")
        void handleInitiated_NotExpired() {
            // Given
            Instant future = Instant.now().plusSeconds(300); // 5 min in future

            Timestamp timestamp = Timestamp.newBuilder()
                  .setSeconds(future.getEpochSecond())
                  .setNanos(future.getNano())
                  .build();

            MoneyTransferInitiated event = MoneyTransferInitiated.newBuilder()
                  .setEventId(eventId)
                  .setTransactionId(transactionId.toString())
                  .setSenderId(senderId.toString())
                  .setReceiverId(receiverId.toString())
                  .setAmountMinorUnits(1_000_000L)
                  .setExpiresAt(timestamp)
                  .build();

            when(idempotencyGuard.isProcessed(eventId)).thenReturn(false);

            // When
            transferOrchestrator.handleInitiated(event);

            // Then
            verify(pendingTransferService).storeAs(
                  transactionId,
                  senderId,
                  receiverId,
                  new BigDecimal("100.0000"),
                  INITIATED
            );
        }

        @Test
        @DisplayName("Should handle MoneyTransferInitiated - DISCARDED status when expired")
        void handleInitiated_Expired() {
            // Given
            Instant past = Instant.now().minusSeconds(300); // 5 min ago

            Timestamp timestamp = Timestamp.newBuilder()
                  .setSeconds(past.getEpochSecond())
                  .setNanos(past.getNano())
                  .build();

            MoneyTransferInitiated event = MoneyTransferInitiated.newBuilder()
                  .setEventId(eventId)
                  .setTransactionId(transactionId.toString())
                  .setSenderId(senderId.toString())
                  .setReceiverId(receiverId.toString())
                  .setAmountMinorUnits(1_000_000L)
                  .setExpiresAt(timestamp)
                  .build();

            when(idempotencyGuard.isProcessed(eventId)).thenReturn(false);

            // When
            transferOrchestrator.handleInitiated(event);

            // Then
            verify(pendingTransferService).storeAs(
                  transactionId,
                  senderId,
                  receiverId,
                  new BigDecimal("100.0000"),
                  EXPIRED
            );
        }

        @Test
        @DisplayName("Should skip duplicate MoneyTransferInitiated")
        void handleInitiated_Duplicate() {
            // Given
            MoneyTransferInitiated event = MoneyTransferInitiated.newBuilder()
                  .setEventId(eventId)
                  .setTransactionId(transactionId.toString())
                  .setSenderId(senderId.toString())
                  .setReceiverId(receiverId.toString())
                  .setAmountMinorUnits(1_000_000L)
                  .setExpiresAt(Timestamp.getDefaultInstance())
                  .build();

            when(idempotencyGuard.isProcessed(eventId))
                  .thenReturn(true);

            // When
            transferOrchestrator.handleInitiated(event);

            // Then
            verify(pendingTransferService, never()).storeAs(
                  any(),
                  any(),
                  any(),
                  any(),
                  any()
            );
        }
    }

    @Nested
    @DisplayName("handleApproved Method")
    class HandleApproved {

        @Test
        @DisplayName("Should handle TransferApproved - trigger settlement")
        void handleApproved_Success() {
            // Given
            TransferApproved event = TransferApproved.newBuilder()
                  .setEventId(eventId)
                  .setTransactionId(transactionId.toString())
                  .build();

            when(idempotencyGuard.isProcessed(eventId))
                  .thenReturn(false);

            // When
            transferOrchestrator.handleApproved(event);

            // Then
            verify(settlementService).settle(eq(transactionId), any(Timestamp.class));
        }

        @Test
        @DisplayName("Should skip duplicate TransferApproved")
        void handleApproved_Duplicate() {
            // Given
            TransferApproved event = TransferApproved.newBuilder()
                  .setEventId(eventId)
                  .setTransactionId(transactionId.toString())
                  .build();

            when(idempotencyGuard.isProcessed(eventId))
                  .thenReturn(true);

            // When
            transferOrchestrator.handleApproved(event);

            // Then
            verify(settlementService, never()).settle(any(), any(Timestamp.class));
        }
    }

    @Nested
    @DisplayName("handleFraud Method")
    class HandleFraud {
        @Test
        @DisplayName("Should handle FraudDetected - atomic discard when 1 row updated")
        void handleFraud_Success() {
            // Given
            FraudDetected event = FraudDetected.newBuilder()
                  .setEventId(eventId)
                  .setTransactionId(transactionId.toString())
                  .setSenderId(senderId.toString())
                  .setReason("test_fraud")
                  .build();

            when(idempotencyGuard.isProcessed(eventId)).thenReturn(false);
            when(pendingTransferService.atomicStatusUpdate(transactionId, DISCARDED))
                  .thenReturn(1);

            // When
            transferOrchestrator.handleFraud(event);

            // Then
            verify(pendingTransferService).atomicStatusUpdate(transactionId, DISCARDED);
        }

        @Test
        @DisplayName("Should handle FraudDetected - skip when 0 rows updated (already terminal)")
        void handleFraud_AlreadyTerminal() {
            // Given
            FraudDetected event = FraudDetected.newBuilder()
                  .setEventId(eventId)
                  .setTransactionId(transactionId.toString())
                  .setSenderId(senderId.toString())
                  .setReason("test_fraud")
                  .build();

            when(idempotencyGuard.isProcessed(eventId))
                  .thenReturn(false);

            when(pendingTransferService.atomicStatusUpdate(transactionId, DISCARDED))
                  .thenReturn(0);

            // When
            transferOrchestrator.handleFraud(event);

            // Then
            verify(pendingTransferService).atomicStatusUpdate(transactionId, DISCARDED);
        }

        @Test
        @DisplayName("Should skip duplicate FraudDetected")
        void handleFraud_Duplicate() {
            // Given
            FraudDetected event = FraudDetected.newBuilder()
                  .setEventId(eventId)
                  .setTransactionId(transactionId.toString())
                  .setSenderId(senderId.toString())
                  .setReason("test_fraud")
                  .build();

            when(idempotencyGuard.isProcessed(eventId))
                  .thenReturn(true);

            // When
            transferOrchestrator.handleFraud(event);

            // Then
            verify(pendingTransferService, never()).atomicStatusUpdate(any(), any());
        }
    }
}