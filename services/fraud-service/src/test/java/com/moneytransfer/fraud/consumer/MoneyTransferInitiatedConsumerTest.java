package com.moneytransfer.fraud.consumer;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.exception.core.EventSecurityException;
import com.moneytransfer.fraud.idempotency.IIdempotencyGuard;
import com.moneytransfer.fraud.service.IFraudDetectionService;
import com.moneytransfer.security.service.ISignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MoneyTransferInitiatedConsumer Unit Tests")
class MoneyTransferInitiatedConsumerTest {
    @Mock
    private IFraudDetectionService fraudDetectionService;
    @Mock
    private ISignatureService signatureService;
    @Mock
    private IIdempotencyGuard idempotencyGuard;
    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private MoneyTransferInitiatedConsumer consumer;

    private MoneyTransferInitiated baseEvent;

    @BeforeEach
    void setUp() {
        baseEvent = createEvent();

        // Default successful behavior to minimize boilerplate in positive tests
        lenient().when(signatureService.verify(anyString(), anyString(), anyString(), anyString()))
              .thenReturn(true);
        lenient().when(idempotencyGuard.isFirstOccurrence(anyString()))
              .thenReturn(true);
    }

    private MoneyTransferInitiated createEvent() {
        Instant now = Instant.now();
        return MoneyTransferInitiated.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setTransactionId(UUID.randomUUID().toString())
              .setSenderId(UUID.randomUUID().toString())
              .setReceiverId(UUID.randomUUID().toString())
              .setAmountMinorUnits(1_000_000L)
              .setSignature("valid-signature")
              .setExpiresAt(Timestamp.newBuilder().setSeconds(now.plusSeconds(600).getEpochSecond()).build())
              .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
              .build();
    }

    @Nested
    @DisplayName("consume")
    class Consume {

        @Test
        @DisplayName("Should consume and process valid event")
        void consume_ValidEvent() {
            consumer.consume(baseEvent, ack);

            verify(signatureService).verify(
                  baseEvent.getEventId(),
                  baseEvent.getTransactionId(),
                  String.valueOf(baseEvent.getOccurredAt().getSeconds()),
                  baseEvent.getSignature()
            );
            verify(idempotencyGuard).isFirstOccurrence(baseEvent.getEventId());
            verify(fraudDetectionService).evaluate(baseEvent);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should throw EventSecurityException on invalid signature")
        void consume_InvalidSignature() {
            when(signatureService.verify(anyString(), anyString(), anyString(), anyString()))
                  .thenReturn(false);

            assertThatThrownBy(() -> consumer.consume(baseEvent, ack))
                  .isInstanceOf(EventSecurityException.class);

            verify(idempotencyGuard, never()).isFirstOccurrence(anyString());
            verify(fraudDetectionService, never()).evaluate(any());
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("Should skip duplicate events but acknowledge")
        void consume_DuplicateEvent() {
            when(idempotencyGuard.isFirstOccurrence(baseEvent.getEventId()))
                  .thenReturn(false);

            consumer.consume(baseEvent, ack);

            verify(fraudDetectionService, never()).evaluate(any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Should verify signature before idempotency check")
        void consume_SignatureBeforeIdempotency() {
            when(signatureService.verify(anyString(), anyString(), anyString(), anyString()))
                  .thenReturn(false);

            assertThatThrownBy(() -> consumer.consume(baseEvent, ack))
                  .isInstanceOf(EventSecurityException.class);

            verify(idempotencyGuard, never()).isFirstOccurrence(anyString());
        }

        @Test
        @DisplayName("Should check idempotency before evaluation")
        void consume_IdempotencyBeforeEvaluation() {
            when(idempotencyGuard.isFirstOccurrence(anyString())).thenReturn(false);

            consumer.consume(baseEvent, ack);

            verify(idempotencyGuard).isFirstOccurrence(anyString());
            verify(fraudDetectionService, never()).evaluate(any());
        }
    }
}