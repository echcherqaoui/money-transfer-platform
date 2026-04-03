package com.moneytransfer.transaction.service.impl;

import com.google.protobuf.Message;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.transaction.model.OutboxEvent;
import com.moneytransfer.transaction.model.Transaction;
import com.moneytransfer.transaction.properties.TransactionProperties;
import com.moneytransfer.transaction.repository.OutboxEventRepository;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionOutboxServiceImpl Unit Tests")
class TransactionOutboxServiceImplTest {
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ISignatureService signatureService;
    @Mock
    private KafkaProtobufSerializer<Message> serializer;
    @Mock
    private TransactionProperties transactionProperties;
    private TransactionOutboxServiceImpl outboxService;
    private final String transferInitiatedTopic = "test.mtp.transaction.transfer.initiated.v1";

    @BeforeEach
    void setUp() {
        when(transactionProperties.getTimeoutMinutes()).thenReturn(2L);

        outboxService = new TransactionOutboxServiceImpl(
              outboxEventRepository,
              signatureService,
              serializer,
              transferInitiatedTopic,
              transactionProperties
        );
    }

    @Nested
    @DisplayName("publish transfer initiated method")
    class PublishTransferInitiated {

        @Test
        @DisplayName("Should publish MoneyTransferInitiated event to outbox")
        void publishTransferInitiated_Success() {
            // Given
            UUID transactionId = UUID.randomUUID();
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            BigDecimal amount = new BigDecimal("100.0000");

            Transaction transaction = new Transaction()
                  .setId(transactionId)
                  .setSenderId(senderId)
                  .setReceiverId(receiverId)
                  .setAmount(amount);

            String expectedSignature = "test-signature";
            byte[] serializedBytes = new byte[]{1, 2, 3, 4};

            when(signatureService.sign(any(), any(), any()))
                  .thenReturn(expectedSignature);

            when(serializer.serialize(eq(transferInitiatedTopic), any(MoneyTransferInitiated.class)))
                  .thenReturn(serializedBytes);

            when(outboxEventRepository.save(any(OutboxEvent.class)))
                  .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            outboxService.publishTransferInitiated(transaction);

            // Then
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertThat(saved.getAggregateType()).isEqualTo("Transaction");
            assertThat(saved.getAggregateId()).isEqualTo(transactionId);
            assertThat(saved.getPayload()).isEqualTo(serializedBytes);
        }

        @Test
        @DisplayName("Should convert amount to minor units correctly")
        void publishTransferInitiated_AmountConversion() {
            // Given
            Transaction transaction = new Transaction()
                  .setId(UUID.randomUUID())
                  .setSenderId(UUID.randomUUID())
                  .setReceiverId(UUID.randomUUID())
                  .setAmount(new BigDecimal("123.4567"));

            when(signatureService.sign(any(), any(), any())).thenReturn("sig");
            when(serializer.serialize(any(), any())).thenReturn(new byte[]{});

            // When
            outboxService.publishTransferInitiated(transaction);

            // Then
            ArgumentCaptor<MoneyTransferInitiated> protoCaptor =
                  ArgumentCaptor.forClass(MoneyTransferInitiated.class);

            verify(serializer).serialize(eq(transferInitiatedTopic), protoCaptor.capture());

            MoneyTransferInitiated proto = protoCaptor.getValue();
            assertThat(proto.getAmountMinorUnits()).isEqualTo(1_234_567L); // 123.4567 * 10000
        }

        @Test
        @DisplayName("Should include HMAC signature in proto")
        void publishTransferInitiated_IncludesSignature() {
            // Given
            Transaction transaction = new Transaction()
                  .setId(UUID.randomUUID())
                  .setSenderId(UUID.randomUUID())
                  .setReceiverId(UUID.randomUUID())
                  .setAmount(BigDecimal.TEN);

            String expectedSignature = "hmac-signature-xyz";
            when(signatureService.sign(any(), any(), any())).thenReturn(expectedSignature);
            when(serializer.serialize(any(), any())).thenReturn(new byte[]{});

            // When
            outboxService.publishTransferInitiated(transaction);

            // Then
            ArgumentCaptor<MoneyTransferInitiated> protoCaptor =
                  ArgumentCaptor.forClass(MoneyTransferInitiated.class);
            verify(serializer).serialize(eq(transferInitiatedTopic), protoCaptor.capture());

            MoneyTransferInitiated proto = protoCaptor.getValue();
            assertThat(proto.getSignature()).isEqualTo(expectedSignature);
        }

        @Test
        @DisplayName("Should calculate expiresAt with safety buffer")
        void publishTransferInitiated_ExpiresAtCalculation() {
            // Given
            Transaction transaction = new Transaction()
                  .setId(UUID.randomUUID())
                  .setSenderId(UUID.randomUUID())
                  .setReceiverId(UUID.randomUUID())
                  .setAmount(BigDecimal.TEN);

            when(signatureService.sign(any(), any(), any())).thenReturn("sig");
            when(serializer.serialize(any(), any())).thenReturn(new byte[]{});

            Instant before = Instant.now().plusSeconds(2 * 60 - 30 - 5); // timeout - buffer - margin

            // When
            outboxService.publishTransferInitiated(transaction);

            Instant after = Instant.now().plusSeconds(2 * 60 - 30 + 5);

            // Then
            ArgumentCaptor<MoneyTransferInitiated> protoCaptor =
                  ArgumentCaptor.forClass(MoneyTransferInitiated.class);

            verify(serializer).serialize(eq(transferInitiatedTopic), protoCaptor.capture());

            MoneyTransferInitiated proto = protoCaptor.getValue();
            Instant expiresAt = Instant.ofEpochSecond(
                  proto.getExpiresAt().getSeconds(),
                  proto.getExpiresAt().getNanos()
            );

            assertThat(expiresAt).isBetween(before, after);
        }

        @Test
        @DisplayName("Should include all required proto fields")
        void publishTransferInitiated_AllProtoFields() {
            // Given
            UUID transactionId = UUID.randomUUID();
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();

            Transaction transaction = new Transaction()
                  .setId(transactionId)
                  .setSenderId(senderId)
                  .setReceiverId(receiverId)
                  .setAmount(new BigDecimal("50.0000"));

            when(signatureService.sign(any(), any(), any()))
                  .thenReturn("sig");

            when(serializer.serialize(any(), any()))
                  .thenReturn(new byte[]{});

            // When
            outboxService.publishTransferInitiated(transaction);

            // Then
            ArgumentCaptor<MoneyTransferInitiated> protoCaptor =
                  ArgumentCaptor.forClass(MoneyTransferInitiated.class);

            verify(serializer).serialize(eq(transferInitiatedTopic), protoCaptor.capture());

            MoneyTransferInitiated proto = protoCaptor.getValue();
            assertThat(proto.getEventId()).isNotEmpty();
            assertThat(proto.getTransactionId()).isEqualTo(transactionId.toString());
            assertThat(proto.getSenderId()).isEqualTo(senderId.toString());
            assertThat(proto.getReceiverId()).isEqualTo(receiverId.toString());
            assertThat(proto.getAmountMinorUnits()).isEqualTo(500_000L);
            assertThat(proto.hasExpiresAt()).isTrue();
            assertThat(proto.hasOccurredAt()).isTrue();
            assertThat(proto.getSignature()).isNotEmpty();
        }

        @Test
        @DisplayName("Should sign with eventId, transactionId, and epochSeconds")
        void publishTransferInitiated_SignatureParameters() {
            // Given
            UUID transactionId = UUID.randomUUID();
            Transaction transaction = new Transaction()
                  .setId(transactionId)
                  .setSenderId(UUID.randomUUID())
                  .setReceiverId(UUID.randomUUID())
                  .setAmount(BigDecimal.TEN);

            when(signatureService.sign(any(), any(), any()))
                  .thenReturn("sig");

            when(serializer.serialize(any(), any()))
                  .thenReturn(new byte[]{});

            // When
            outboxService.publishTransferInitiated(transaction);

            // Then
            verify(signatureService).sign(
                  any(String.class), // eventId
                  eq(transactionId.toString()),
                  any(String.class) // epochSeconds
            );
        }
    }
}