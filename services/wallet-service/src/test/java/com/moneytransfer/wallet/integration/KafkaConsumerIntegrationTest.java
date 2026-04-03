package com.moneytransfer.wallet.integration;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.FraudDetected;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.contract.TransferApproved;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.wallet.model.PendingTransfer;
import com.moneytransfer.wallet.model.Wallet;
import com.moneytransfer.wallet.repository.PendingTransferRepository;
import com.moneytransfer.wallet.repository.ProcessedEventRepository;
import com.moneytransfer.wallet.repository.WalletRepository;
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
import java.util.Optional;
import java.util.UUID;

import static com.moneytransfer.wallet.enums.PendingStatus.COMPLETED;
import static com.moneytransfer.wallet.enums.PendingStatus.DISCARDED;
import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;
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
    private WalletRepository walletRepository;
    @Autowired
    private PendingTransferRepository pendingTransferRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Value("${kafka.topics.transaction.transfer-initiated}")
    private String initiatedTopic;
    @Value("${kafka.topics.fraud.transfer-approved}")
    private String approvedTopic;
    @Value("${kafka.topics.fraud.transfer-detected}")
    private String fraudTopic;

    private UUID transactionId;
    private UUID senderId;
    private UUID receiverId;

    @BeforeEach
    void setUp() {
        // Clean up
        processedEventRepository.deleteAll();
        pendingTransferRepository.deleteAll();
        walletRepository.deleteAll();

        transactionId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();

        // Create wallets
        walletRepository.save(
              new Wallet()
                    .setUserId(senderId)
                    .setBalance(new BigDecimal("1000.0000"))
        );

        walletRepository.save(
              new Wallet()
                    .setUserId(receiverId)
                    .setBalance(new BigDecimal("500.0000"))
        );
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
              .setSeconds(instant.getEpochSecond())
              .setNanos(instant.getNano())
              .build();
    }

    @Test
    @DisplayName("Should consume MoneyTransferInitiated and store INITIATED pending transfer")
    void consumeMoneyTransferInitiated_Success() {
        // Given
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(600);

        String signature = signatureService.sign(
              eventId,
              transactionId.toString(),
              String.valueOf(now.getEpochSecond())
        );

        MoneyTransferInitiated event = MoneyTransferInitiated.newBuilder()
              .setEventId(eventId)
              .setTransactionId(transactionId.toString())
              .setSenderId(senderId.toString())
              .setReceiverId(receiverId.toString())
              .setAmountMinorUnits(2_000_000L) // 200.00
              .setExpiresAt(toTimestamp(expiresAt))
              .setSignature(signature)
              .setOccurredAt(toTimestamp(now))
              .build();

        // When
        kafkaTemplate.send(
              new ProducerRecord<>(
                    initiatedTopic,
                    transactionId.toString(),
                    event
              )
        );

        // Then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Optional<PendingTransfer> pendingOpt = pendingTransferRepository.findById(transactionId);
            assertThat(pendingOpt).isPresent();

            PendingTransfer pending = pendingOpt.get();
            assertThat(pending.getStatus()).isEqualTo(INITIATED);

            assertThat(processedEventRepository.findById(eventId)).isPresent();
        });
    }


    @Test
    @DisplayName("Should consume MoneyTransferInitiated and store DISCARDED when expired")
    void consumeMoneyTransferInitiated_Expired() {
        // Given
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.minusSeconds(300); // Already expired

        String signature = signatureService.sign(
              eventId,
              transactionId.toString(),
              String.valueOf(now.getEpochSecond())
        );

        MoneyTransferInitiated event = MoneyTransferInitiated.newBuilder()
              .setEventId(eventId)
              .setTransactionId(transactionId.toString())
              .setSenderId(senderId.toString())
              .setReceiverId(receiverId.toString())
              .setAmountMinorUnits(2_000_000L)
              .setExpiresAt(toTimestamp(expiresAt))
              .setSignature(signature)
              .setOccurredAt(toTimestamp(now))
              .build();

        // When
        kafkaTemplate.send(
              new ProducerRecord<>(
                    initiatedTopic,
                    transactionId.toString(),
                    event
              )
        );

        // Then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            PendingTransfer pending = pendingTransferRepository.findById(transactionId).orElseThrow();
            assertThat(pending.getStatus()).isEqualTo(DISCARDED);
        });
    }

    @Test
    @DisplayName("Should consume TransferApproved and settle transfer")
    void consumeTransferApproved_Success() {
        // Given - create pending transfer first
        pendingTransferRepository.save(
              new PendingTransfer()
                    .setTransactionId(transactionId)
                    .setSenderId(senderId)
                    .setReceiverId(receiverId)
                    .setAmount(new BigDecimal("200.0000"))
                    .setStatus(INITIATED)
        );

        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String signature = signatureService.sign(
              eventId,
              transactionId.toString(),
              String.valueOf(now.getEpochSecond())
        );

        TransferApproved event = TransferApproved.newBuilder()
              .setEventId(eventId)
              .setTransactionId(transactionId.toString())
              .setSignature(signature)
              .setOccurredAt(toTimestamp(now))
              .build();

        // When
        kafkaTemplate.send(
              new ProducerRecord<>(
                    approvedTopic,
                    transactionId.toString(),
                    event
              )
        );

        // Then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Wallet sender = walletRepository.findByUserId(senderId).orElseThrow();
            Wallet receiver = walletRepository.findByUserId(receiverId).orElseThrow();

            assertThat(sender.getBalance()).isEqualByComparingTo("800.0000");
            assertThat(receiver.getBalance()).isEqualByComparingTo("700.0000");

            PendingTransfer pending = pendingTransferRepository.findById(transactionId).orElseThrow();
            assertThat(pending.getStatus()).isEqualTo(COMPLETED);
        });
    }

    @Test
    @DisplayName("Should consume FraudDetected and discard pending transfer")
    void consumeFraudDetected_Success() {
        // Given - create pending transfer first
        pendingTransferRepository.save(
              new PendingTransfer()
                    .setTransactionId(transactionId)
                    .setSenderId(senderId)
                    .setReceiverId(receiverId)
                    .setAmount(new BigDecimal("200.0000"))
                    .setStatus(INITIATED)
        );

        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String signature = signatureService.sign(
              eventId,
              transactionId.toString(),
              String.valueOf(now.getEpochSecond())
        );

        FraudDetected event = FraudDetected.newBuilder()
              .setEventId(eventId)
              .setTransactionId(transactionId.toString())
              .setSenderId(senderId.toString())
              .setReason("velocity_exceeded")
              .setSignature(signature)
              .setOccurredAt(toTimestamp(now))
              .build();

        // When
        kafkaTemplate.send(
              new ProducerRecord<>(
                    fraudTopic,
                    transactionId.toString(),
                    event
              )
        );

        // Then
        await().atMost(10, SECONDS).untilAsserted(() -> {
            PendingTransfer pending = pendingTransferRepository.findById(transactionId).orElseThrow();
            assertThat(pending.getStatus()).isEqualTo(DISCARDED);

            // Verify balances unchanged
            Wallet sender = walletRepository.findByUserId(senderId).orElseThrow();
            assertThat(sender.getBalance()).isEqualByComparingTo("1000.0000");
        });
    }

    @Test
    @DisplayName("Should enforce idempotency - duplicate events ignored")
    void consumeDuplicateEvents_Idempotency() {
        // Given
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(600);

        String signature = signatureService.sign(
              eventId,
              transactionId.toString(),
              String.valueOf(now.getEpochSecond())
        );

        MoneyTransferInitiated event = MoneyTransferInitiated.newBuilder()
              .setEventId(eventId)
              .setTransactionId(transactionId.toString())
              .setSenderId(senderId.toString())
              .setReceiverId(receiverId.toString())
              .setAmountMinorUnits(2_000_000L)
              .setExpiresAt(toTimestamp(expiresAt))
              .setSignature(signature)
              .setOccurredAt(toTimestamp(now))
              .build();

        // When - send same event twice
        kafkaTemplate.send(
              new ProducerRecord<>(
                    initiatedTopic,
                    transactionId.toString(),
                    event
              )
        );

        kafkaTemplate.send(
              new ProducerRecord<>(
                    initiatedTopic,
                    transactionId.toString(),
                    event
              )
        );

        // Then - only one pending transfer created
        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(pendingTransferRepository.count()).isEqualTo(1);
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        });
    }
}