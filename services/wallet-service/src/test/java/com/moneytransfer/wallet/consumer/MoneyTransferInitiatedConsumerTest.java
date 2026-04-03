package com.moneytransfer.wallet.consumer;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.exception.core.EventSecurityException;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.wallet.service.ITransferOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MoneyTransferInitiatedConsumer Unit Tests")
class MoneyTransferInitiatedConsumerTest {
    @Mock
    private ISignatureService signatureService;
    @Mock
    private ITransferOrchestrator transferOrchestrator;
    @Mock
    private Acknowledgment ack;
    @InjectMocks
    private MoneyTransferInitiatedConsumer moneyTransferInitiatedConsumer;

    private MoneyTransferInitiated validEvent;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        Timestamp expiredAt = Timestamp.newBuilder()
              .setSeconds(now.plusSeconds(600).getEpochSecond())
              .build();

        Timestamp occurredAt = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();

        validEvent = MoneyTransferInitiated.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setTransactionId(UUID.randomUUID().toString())
              .setSenderId(UUID.randomUUID().toString())
              .setReceiverId(UUID.randomUUID().toString())
              .setAmountMinorUnits(1_000_000L)
              .setExpiresAt(expiredAt)
              .setSignature("valid-signature")
              .setOccurredAt(occurredAt)
              .build();
    }

    @Test
    @DisplayName("Should consume and process valid event")
    void consume_ValidEvent() {
        // Given
        when(signatureService.verify(any(), any(), any(), any()))
              .thenReturn(true);

        // When
        moneyTransferInitiatedConsumer.consume(validEvent, ack);

        // Then
        verify(signatureService).verify(
              validEvent.getEventId(),
              validEvent.getTransactionId(),
              String.valueOf(validEvent.getOccurredAt().getSeconds()),
              validEvent.getSignature()
        );

        verify(transferOrchestrator).handleInitiated(validEvent);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should throw EventSecurityException on invalid signature")
    void consume_InvalidSignature() {
        // Given
        when(signatureService.verify(any(), any(), any(), any()))
              .thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> moneyTransferInitiatedConsumer.consume(validEvent, ack))
              .isInstanceOf(EventSecurityException.class);

        verify(transferOrchestrator, never()).handleInitiated(any());
        verify(ack, never()).acknowledge();
    }
}