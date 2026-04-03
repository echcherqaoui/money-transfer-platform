package com.moneytransfer.transaction.consumer;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.FraudDetected;
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

import static com.moneytransfer.transaction.enums.TransactionStatus.FAILED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectedConsumer Unit Tests")
class FraudDetectedConsumerTest {
    @Mock
    private ITransactionService transactionService;
    @Mock
    private ISignatureService signatureService;
    @Mock
    private Acknowledgment ack;
    @InjectMocks
    private FraudDetectedConsumer fraudDetectedConsumer;
    private FraudDetected validEvent;

    @BeforeEach
    void setUp() {
        validEvent = FraudDetected.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setTransactionId(UUID.randomUUID().toString())
              .setSenderId(UUID.randomUUID().toString())
              .setReason("velocity_exceeded")
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
            fraudDetectedConsumer.consume(validEvent, ack);

            // Then
            verify(signatureService).verify(
                  validEvent.getEventId(),
                  validEvent.getTransactionId(),
                  String.valueOf(validEvent.getOccurredAt().getSeconds()),
                  validEvent.getSignature()
            );

            verify(transactionService).updateStatus(
                  UUID.fromString(validEvent.getTransactionId()),
                  FAILED
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
            assertThatThrownBy(() -> fraudDetectedConsumer.consume(validEvent, ack))
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
            assertThatThrownBy(() -> fraudDetectedConsumer.consume(validEvent, ack))
                  .isInstanceOf(EventSecurityException.class);

            verify(ack, never()).acknowledge();
        }
    }
}