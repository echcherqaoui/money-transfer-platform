package com.moneytransfer.transaction.consumer;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.TransferCompleted;
import com.moneytransfer.exception.core.EventSecurityException;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.transaction.service.ITransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static com.moneytransfer.transaction.enums.TransactionStatus.COMPLETED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferCompletedConsumer Unit Tests")
class TransferCompletedConsumerTest {
    @Mock
    private ITransactionService transactionService;
    @Mock
    private ISignatureService signatureService;
    @Mock
    private Acknowledgment ack;
    @InjectMocks
    private TransferCompletedConsumer transferCompletedConsumer;
    private TransferCompleted validEvent;

    @BeforeEach
    void setUp() {
        validEvent = TransferCompleted.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setTransactionId(UUID.randomUUID().toString())
              .setSenderId(UUID.randomUUID().toString())
              .setReceiverId(UUID.randomUUID().toString())
              .setAmountMinorUnits(1_000_000L)
              .setSenderNewBalanceMinor(9_000_000L)
              .setReceiverNewBalanceMinor(2_000_000L)
              .setSignature("valid-signature")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build())
              .build();
    }

    @Nested
    @DisplayName("consume method")
    class Consume {
        @Test
        @DisplayName("Should consume and process valid event")
        void consume_ValidEvent() {
            // Given
            when(signatureService.verify(any(), any(), any(), any()))
                  .thenReturn(true);

            doNothing().when(transactionService).updateStatus(any(), any());

            // When
            transferCompletedConsumer.consume(validEvent, ack);

            // Then
            verify(signatureService).verify(
                  validEvent.getEventId(),
                  validEvent.getTransactionId(),
                  String.valueOf(validEvent.getOccurredAt().getSeconds()),
                  validEvent.getSignature()
            );

            verify(transactionService).updateStatus(
                  UUID.fromString(validEvent.getTransactionId()),
                  COMPLETED
            );

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should throw EventSecurityException on invalid signature")
        void consume_InvalidSignature() {
            // Given
            when(signatureService.verify(any(), any(), any(), any()))
                  .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> transferCompletedConsumer.consume(validEvent, ack))
                  .isInstanceOf(EventSecurityException.class);

            verify(transactionService, never()).updateStatus(any(), any());
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("Should not acknowledge on signature failure")
        void consume_NoAckOnFailure() {
            // Given
            when(signatureService.verify(any(), any(), any(), any()))
                  .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> transferCompletedConsumer.consume(validEvent, ack))
                  .isInstanceOf(EventSecurityException.class);

            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("Should verify signature before processing")
        void consume_VerifiesSignatureFirst() {
            // Given
            when(signatureService.verify(any(), any(), any(), any()))
                  .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> transferCompletedConsumer.consume(validEvent, ack))
                  .isInstanceOf(EventSecurityException.class);

            verify(signatureService).verify(any(), any(), any(), any());

            verify(transactionService, never()).updateStatus(any(), any());
        }
    }
}