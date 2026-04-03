package com.moneytransfer.transaction.integration;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.FraudDetected;
import com.moneytransfer.contract.TransferCompleted;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.transaction.model.Transaction;
import com.moneytransfer.transaction.repository.TransactionRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.moneytransfer.transaction.enums.TransactionStatus.COMPLETED;
import static com.moneytransfer.transaction.enums.TransactionStatus.FAILED;
import static com.moneytransfer.transaction.enums.TransactionStatus.PENDING;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka
@DirtiesContext
@DisplayName("Kafka Consumer Integration Tests")
class KafkaConsumerIntegrationTest {
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private ISignatureService signatureService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Value("${kafka.topics.wallet.transfer-completed}")
    private String completedTopic;
    @Value("${kafka.topics.fraud.transfer-detected}")
    private String fraudTopic;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Should consume TransferCompleted and mark transaction COMPLETED")
    void consumeTransferCompleted_Success() {
        // Given
        Transaction transaction = transactionRepository.save(
              new Transaction()
                    .setSenderId(UUID.randomUUID())
                    .setReceiverId(UUID.randomUUID())
                    .setAmount(new BigDecimal("100.0000"))
                    .setStatus(PENDING)
        );

        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String signature = signatureService.sign(
              eventId,
              transaction.getId().toString(),
              String.valueOf(now.getEpochSecond())
        );

        Timestamp occurredAt = Timestamp.newBuilder()
              .setSeconds(now.getEpochSecond())
              .setNanos(now.getNano())
              .build();

        TransferCompleted event = TransferCompleted.newBuilder()
              .setEventId(eventId)
              .setTransactionId(transaction.getId().toString())
              .setSenderId(transaction.getSenderId().toString())
              .setReceiverId(transaction.getReceiverId().toString())
              .setAmountMinorUnits(1_000_000L)
              .setSenderNewBalanceMinor(9_000_000L)
              .setReceiverNewBalanceMinor(2_000_000L)
              .setSignature(signature)
              .setOccurredAt(occurredAt)
              .build();

        // When
        kafkaTemplate.send(
              new ProducerRecord<>(
                    completedTopic,
                    transaction.getId().toString(),
                    event
              )
        );

        // Then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Transaction updated = transactionRepository.findById(transaction.getId()).orElseThrow();

            assertThat(updated.getStatus()).isEqualTo(COMPLETED);
        });
    }

    @Test
    @DisplayName("Should consume FraudDetected and mark transaction FAILED")
    void consumeFraudDetected_Success() {
        // Given
        Transaction transaction = transactionRepository.save(
              new Transaction()
                    .setSenderId(UUID.randomUUID())
                    .setReceiverId(UUID.randomUUID())
                    .setAmount(new BigDecimal("100.0000"))
                    .setStatus(PENDING)
        );

        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String signature = signatureService.sign(
              eventId,
              transaction.getId().toString(),
              String.valueOf(now.getEpochSecond())
        );

        Timestamp occurredAt = Timestamp.newBuilder()
              .setSeconds(now.getEpochSecond())
              .build();

        FraudDetected event = FraudDetected.newBuilder()
              .setEventId(eventId)
              .setTransactionId(transaction.getId().toString())
              .setSenderId(transaction.getSenderId().toString())
              .setReason("velocity_exceeded")
              .setSignature(signature)
              .setOccurredAt(occurredAt)
              .build();

        // When
        kafkaTemplate.send(
              new ProducerRecord<>(
                    fraudTopic,
                    transaction.getId().toString(),
                    event
              )
        );

        // Then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Transaction updated = transactionRepository.findById(transaction.getId()).orElseThrow();

            assertThat(updated.getStatus()).isEqualTo(FAILED);
        });
    }
}