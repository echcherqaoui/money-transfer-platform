package com.moneytransfer.wallet.consumer;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.TransferApproved;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferApprovedConsumer Unit Tests")
class TransferApprovedConsumerTest {
    @Mock
    private ISignatureService signatureService;
    @Mock
    private ITransferOrchestrator transferOrchestrator;
    @Mock
    private Acknowledgment ack;
    @InjectMocks
    private TransferApprovedConsumer transferApprovedConsumer;
    private TransferApproved validEvent;

    @BeforeEach
    void setUp() {
        validEvent = TransferApproved.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setTransactionId(UUID.randomUUID().toString())
              .setSignature("valid-signature")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build())
              .build();
    }

    @Test
    @DisplayName("Should consume and process valid event")
    void consume_ValidEvent() {
        // Given
        when(signatureService.verify(any(), any(), any(), any()))
              .thenReturn(true);

        // When
        transferApprovedConsumer.consume(validEvent, ack);

        // Then
        verify(signatureService).verify(
              validEvent.getEventId(),
              validEvent.getTransactionId(),
              String.valueOf(validEvent.getOccurredAt().getSeconds()),
              validEvent.getSignature()
        );

        verify(transferOrchestrator).handleApproved(validEvent);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Should throw EventSecurityException on invalid signature")
    void consume_InvalidSignature() {
        // Given
        when(signatureService.verify(any(), any(), any(), any()))
              .thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> transferApprovedConsumer.consume(validEvent, ack))
              .isInstanceOf(EventSecurityException.class);

        verify(transferOrchestrator, never()).handleApproved(any());
        verify(ack, never()).acknowledge();
    }
}