package com.moneytransfer.wallet.service.impl;

import com.google.protobuf.Message;
import com.moneytransfer.contract.TransferCompleted;
import com.moneytransfer.contract.TransferFailed;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.wallet.model.OutboxEvent;
import com.moneytransfer.wallet.repository.OutboxEventRepository;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletOutboxService Unit Tests")
class WalletOutboxServiceTest {
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ISignatureService signatureService;
    @Mock
    private KafkaProtobufSerializer<Message> serializer;
    @InjectMocks
    private WalletOutboxService walletOutboxService;

    private final String transferCompletedTopic = "dev.mtp.transaction.transfer.completed.v1";
    private final String transferFailedTopic = "dev.mtp.transaction.transfer.failed.v1";

    @BeforeEach
    void setUp() {
        walletOutboxService = new WalletOutboxService(
              outboxEventRepository,
              signatureService,
              serializer,
              transferCompletedTopic,
              transferFailedTopic
        );
    }

    @Nested
    @DisplayName("Publishing a completed transfer")
    class PublishTransferCompleted {
        @Test
        @DisplayName("Should map financial data correctly to TransferCompleted payload and save to Outbox")
        void publishTransferCompleted_Success() {
            // Given
            UUID transactionId = UUID.randomUUID();
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            long amountMinor = 1_500_000L;

            String signature = "valid-sig";
            byte[] mockPayload = new byte[]{0, 1, 1};

            when(signatureService.sign(any(), eq(transactionId.toString()), any()))
                  .thenReturn(signature);

            when(serializer.serialize(eq(transferCompletedTopic), any(TransferCompleted.class)))
                  .thenReturn(mockPayload);

            // When
            walletOutboxService.publishTransferCompleted(
                  transactionId,
                  senderId,
                  receiverId,
                  amountMinor,
                  400L,
                  300L
            );

            // Then: Verify Mapping Integrity
            ArgumentCaptor<TransferCompleted> eventCaptor = ArgumentCaptor.forClass(TransferCompleted.class);
            verify(serializer).serialize(eq(transferCompletedTopic), eventCaptor.capture());

            TransferCompleted event = eventCaptor.getValue();

            assertThat(event.getTransactionId()).isEqualTo(transactionId.toString());
            assertThat(event.getSenderId()).isEqualTo(senderId.toString());
            assertThat(event.getAmountMinorUnits()).isEqualTo(amountMinor);
            assertThat(event.getSignature()).isEqualTo(signature);

            // Then: Verify Outbox Storage
            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(outboxCaptor.capture());

            OutboxEvent saved = outboxCaptor.getValue();
            assertThat(saved.getPayload()).isEqualTo(mockPayload);
            assertThat(saved.getAggregateId()).isEqualTo(transactionId);
        }


        @Test
        @DisplayName("Should include correct fields in TransferCompleted proto")
        void publishTransferCompleted_CorrectProtoFields() {
            // Given
            UUID transactionId = UUID.randomUUID();
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();

            String signature = "valid-sig";

            when(signatureService.sign(any(), eq(transactionId.toString()), any()))
                  .thenReturn(signature);

            when(serializer.serialize(any(), any()))
                  .thenReturn(new byte[]{});

            // When
            walletOutboxService.publishTransferCompleted(
                  transactionId,
                  senderId,
                  receiverId,
                  100L,
                  200L,
                  300L
            );

            // Then
            ArgumentCaptor<TransferCompleted> protoCaptor = ArgumentCaptor.forClass(TransferCompleted.class);
            verify(serializer).serialize(eq(transferCompletedTopic), protoCaptor.capture());

            TransferCompleted proto = protoCaptor.getValue();
            assertThat(proto.getTransactionId()).isEqualTo(transactionId.toString());
            assertThat(proto.getSenderId()).isEqualTo(senderId.toString());
            assertThat(proto.getReceiverId()).isEqualTo(receiverId.toString());
            assertThat(proto.getAmountMinorUnits()).isEqualTo(100L);
            assertThat(proto.getSenderNewBalanceMinor()).isEqualTo(200L);
            assertThat(proto.getReceiverNewBalanceMinor()).isEqualTo(300L);
            assertThat(proto.getEventId()).isNotEmpty();
            assertThat(proto.getSignature()).isEqualTo(signature);
            assertThat(proto.hasOccurredAt()).isTrue();
        }
    }

    @Nested
    @DisplayName("Publishing a failed transfer")
    class PublishTransferFailed {
        @Test
        @DisplayName("Should map failure reason and signature correctly to TransferFailed payload")
        void publishTransferFailed_Success() {
            // Given
            UUID transactionId = UUID.randomUUID();
            UUID senderId = UUID.randomUUID();
            String reason = "Insufficient funds: available=10.00, required=100.00";

            String signature = "valid-sig";
            byte[] mockPayload = new byte[]{7, 8, 9};

            when(signatureService.sign(any(), eq(transactionId.toString()), any()))
                  .thenReturn(signature);

            when(serializer.serialize(eq(transferFailedTopic), any(TransferFailed.class)))
                  .thenReturn(mockPayload);

            // When
            walletOutboxService.publishTransferFailed(transactionId, senderId, reason);

            // Then: Verify Mapping Integrity (The internal Protobuf/POJO)
            ArgumentCaptor<TransferFailed> eventCaptor = ArgumentCaptor.forClass(TransferFailed.class);
            verify(serializer).serialize(eq(transferFailedTopic), eventCaptor.capture());

            TransferFailed event = eventCaptor.getValue();
            assertThat(event.getTransactionId()).isEqualTo(transactionId.toString());
            assertThat(event.getSenderId()).isEqualTo(senderId.toString());
            assertThat(event.getReason()).isEqualTo(reason);
            assertThat(event.getSignature()).isEqualTo(signature);

            // Then: Verify Outbox Envelope
            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(outboxCaptor.capture());

            OutboxEvent saved = outboxCaptor.getValue();
            assertThat(saved.getAggregateId()).isEqualTo(transactionId);
            assertThat(saved.getPayload()).isEqualTo(mockPayload);
        }

        @Test
        @DisplayName("Should include correct fields in TransferFailed proto")
        void publishTransferFailed_CorrectProtoFields() {
            // Given
            UUID transactionId = UUID.randomUUID();
            UUID senderId = UUID.randomUUID();
            String reason = "Test reason";

            String signature = "valid-sig";

            when(signatureService.sign(any(), eq(transactionId.toString()), any()))
                  .thenReturn(signature);

            when(serializer.serialize(any(), any())).thenReturn(new byte[]{});

            // When
            walletOutboxService.publishTransferFailed(transactionId, senderId, reason);

            // Then
            ArgumentCaptor<TransferFailed> protoCaptor = ArgumentCaptor.forClass(TransferFailed.class);
            verify(serializer).serialize(eq(transferFailedTopic), protoCaptor.capture());

            TransferFailed proto = protoCaptor.getValue();
            assertThat(proto.getTransactionId()).isEqualTo(transactionId.toString());
            assertThat(proto.getSenderId()).isEqualTo(senderId.toString());
            assertThat(proto.getReason()).isEqualTo(reason);
            assertThat(proto.getEventId()).isNotEmpty();
            assertThat(proto.getSignature()).isEqualTo(signature);
            assertThat(proto.hasOccurredAt()).isTrue();
        }
    }
}