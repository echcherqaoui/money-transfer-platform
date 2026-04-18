package com.moneytransfer.fraud.service.impl;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.FraudDetected;
import com.moneytransfer.contract.TransferApproved;
import com.moneytransfer.fraud.exception.KafkaPublishException;
import com.moneytransfer.security.service.ISignatureService;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.moneytransfer.fraud.exception.enums.FraudErrorCode.KAFKA_PUBLISH_FAILURE;
import static com.moneytransfer.fraud.exception.enums.FraudErrorCode.KAFKA_PUBLISH_INTERRUPTED;
import static com.moneytransfer.fraud.exception.enums.FraudErrorCode.KAFKA_PUBLISH_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaFraudEventProducer Unit Tests")
class KafkaFraudEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ISignatureService signatureService;

    private KafkaFraudEventProducer fraudEventProducer;

    private final String approvedTopic = "dev.mtp.fraud.transfer.approved.v1";
    private final String detectedTopic = "dev.mtp.fraud.transfer.detected.v1";

    @BeforeEach
    void setUp() {
        fraudEventProducer = new KafkaFraudEventProducer(
              kafkaTemplate,
              signatureService,
              approvedTopic,
              detectedTopic
        );

        // Global stubbing to reduce boilerplate across all tests
        lenient().when(signatureService.sign(anyString(), anyString(), anyString()))
              .thenReturn("test-signature");

        lenient().when(kafkaTemplate.send(anyString(), anyString(), any()))
              .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Nested
    @DisplayName("publish fraud detected method")
    class PublishFraudDetected {

        @Test
        @DisplayName("Should publish FraudDetected event successfully")
        void publishFraudDetected_Success() {
            String transactionId = UUID.randomUUID().toString();
            String senderId = UUID.randomUUID().toString();
            String reason = "velocity_exceeded";

            fraudEventProducer.publishFraudDetected(transactionId, senderId, reason);

            ArgumentCaptor<FraudDetected> eventCaptor = ArgumentCaptor.forClass(FraudDetected.class);
            verify(kafkaTemplate).send(eq(detectedTopic), eq(transactionId), eventCaptor.capture());

            FraudDetected captured = eventCaptor.getValue();
            assertThat(captured.getTransactionId()).isEqualTo(transactionId);
            assertThat(captured.getSenderId()).isEqualTo(senderId);
            assertThat(captured.getReason()).isEqualTo(reason);
            assertThat(captured.getSignature()).isEqualTo("test-signature");
            assertThat(captured.hasOccurredAt()).isTrue();
        }

        @Test
        @DisplayName("Should sign with correct parameters")
        void publishFraudDetected_SignatureParameters() {
            String transactionId = UUID.randomUUID().toString();

            fraudEventProducer.publishFraudDetected(transactionId, "sender", "reason");

            verify(signatureService).sign(anyString(), eq(transactionId), anyString());
        }

        @Test
        @DisplayName("Should throw KafkaPublishException on ExecutionException")
        void publishFraudDetected_ExecutionException() {
            CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Kafka error"));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

            assertThatThrownBy(() -> fraudEventProducer.publishFraudDetected("tx1", "s1", "r1"))
                  .isInstanceOf(KafkaPublishException.class)
                  .hasFieldOrPropertyWithValue("errorCode", KAFKA_PUBLISH_FAILURE);
        }

        @Test
        @DisplayName("Should throw KafkaPublishException on InterruptedException")
        void publishFraudDetected_InterruptedException() throws Exception {
            @SuppressWarnings("unchecked")
            CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
            when(future.get()).thenThrow(new InterruptedException("Interrupted"));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

            assertThatThrownBy(() -> fraudEventProducer.publishFraudDetected("tx1", "s1", "r1"))
                  .isInstanceOf(KafkaPublishException.class)
                  .hasFieldOrPropertyWithValue("errorCode", KAFKA_PUBLISH_INTERRUPTED);

            assertThat(Thread.interrupted()).isTrue();
        }

        @Test
        @DisplayName("Should throw KafkaPublishException on TimeoutException")
        void publishFraudDetected_TimeoutException() throws Exception {
            @SuppressWarnings("unchecked")
            CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
            when(future.get()).thenThrow(new TimeoutException("Timeout"));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

            assertThatThrownBy(() -> fraudEventProducer.publishFraudDetected("tx1", "s1", "r1"))
                  .isInstanceOf(KafkaPublishException.class)
                  .hasFieldOrPropertyWithValue("errorCode", KAFKA_PUBLISH_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("publish transfer approved method")
    class PublishTransferApproved {

        @Test
        @DisplayName("Should publish TransferApproved event successfully")
        void publishTransferApproved_Success() {
            String transactionId = UUID.randomUUID().toString();
            Instant instant = Instant.now().plus(Duration.ofMinutes(10));
            Timestamp expiresAt = Timestamp.newBuilder()
                  .setSeconds(instant.getEpochSecond())
                  .build();

            fraudEventProducer.publishTransferApproved(transactionId, expiresAt);

            ArgumentCaptor<TransferApproved> eventCaptor = ArgumentCaptor.forClass(TransferApproved.class);
            verify(kafkaTemplate).send(eq(approvedTopic), eq(transactionId), eventCaptor.capture());

            TransferApproved captured = eventCaptor.getValue();
            assertThat(captured.getTransactionId()).isEqualTo(transactionId);
            assertThat(captured.getSignature()).isEqualTo("test-signature");
            assertThat(captured.hasOccurredAt()).isTrue();
        }

        @Test
        @DisplayName("Should throw KafkaPublishException on failure")
        void publishTransferApproved_Failure() {
            Instant instant = Instant.now().plus(Duration.ofMinutes(10));
            Timestamp expiresAt = Timestamp.newBuilder()
                  .setSeconds(instant.getEpochSecond())
                  .build();


            CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Error"));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

            assertThatThrownBy(() -> fraudEventProducer.publishTransferApproved("tx1", expiresAt))
                  .isInstanceOf(KafkaPublishException.class)
                  .hasFieldOrPropertyWithValue("errorCode", KAFKA_PUBLISH_FAILURE);
        }
    }
}