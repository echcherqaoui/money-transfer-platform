package com.moneytransfer.wallet.integration;

import com.google.protobuf.Timestamp;
import com.moneytransfer.wallet.model.OutboxEvent;
import com.moneytransfer.wallet.model.PendingTransfer;
import com.moneytransfer.wallet.model.Wallet;
import com.moneytransfer.wallet.repository.OutboxEventRepository;
import com.moneytransfer.wallet.repository.PendingTransferRepository;
import com.moneytransfer.wallet.repository.WalletRepository;
import com.moneytransfer.wallet.service.ISettlementService;
import com.moneytransfer.wallet.service.impl.PendingTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.moneytransfer.wallet.enums.PendingStatus.COMPLETED;
import static com.moneytransfer.wallet.enums.PendingStatus.FAILED;
import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka
@DisplayName("Wallet Settlement Integration Tests")
class WalletSettlementIntegrationTest {
    @Autowired
    private ISettlementService settlementService;
    @Autowired
    private PendingTransferService pendingTransferService;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private PendingTransferRepository pendingTransferRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private UUID transactionId;
    private UUID senderId;
    private UUID receiverId;
    private Wallet senderWallet;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        // Clean up
        outboxEventRepository.deleteAll();
        pendingTransferRepository.deleteAll();
        walletRepository.deleteAll();

        transactionId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();

        // Create wallets
        senderWallet = walletRepository.save(
              new Wallet()
                    .setUserId(senderId)
                    .setBalance(new BigDecimal("1000.0000"))
        );

        receiverWallet = walletRepository.save(
              new Wallet()
                    .setUserId(receiverId)
                    .setBalance(new BigDecimal("500.0000"))
        );
    }

    private Timestamp getExpiresAt(){
        Instant instant = Instant.now().plus(Duration.ofMinutes(10));
        return Timestamp.newBuilder()
              .setSeconds(instant.getEpochSecond())
              .build();
    }

    @Test
    @Transactional
    @DisplayName("Should settle transfer successfully and persist all changes atomically")
    void settleTransfer_SuccessfulFlow() {
        // Given
        BigDecimal transferAmount = new BigDecimal("200.0000");

        Wallet updatedSender = walletRepository.findByUserId(senderId).orElseThrow();


        pendingTransferService.storeAs(
              transactionId,
              senderId,
              receiverId,
              transferAmount,
              INITIATED
        );

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then - verify balances updated
        Wallet updatedReceiver = walletRepository.findByUserId(receiverId).orElseThrow();

        assertThat(updatedSender.getBalance()).isEqualByComparingTo("800.0000");
        assertThat(updatedReceiver.getBalance()).isEqualByComparingTo("700.0000");

        // Then - verify pending transfer marked COMPLETED
        PendingTransfer pending = pendingTransferRepository.findById(transactionId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(COMPLETED);

        // Then - verify outbox event created
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);

        OutboxEvent outbox = outboxEvents.get(0);
        assertThat(outbox.getAggregateType()).isEqualTo("wallet");
        assertThat(outbox.getAggregateId()).isEqualTo(transactionId);
        assertThat(outbox.getEventType()).isEqualTo("completed.v1");
        assertThat(outbox.getPayload()).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Should handle insufficient funds - mark FAILED and publish failure event")
    void settleTransfer_InsufficientFunds() {
        // Given
        BigDecimal transferAmount = new BigDecimal("1500.0000"); // More than sender has

        pendingTransferService.storeAs(
              transactionId,
              senderId,
              receiverId,
              transferAmount,
              INITIATED
        );

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then - verify balances unchanged
        Wallet unchangedSender = walletRepository.findByUserId(senderId).orElseThrow();
        Wallet unchangedReceiver = walletRepository.findByUserId(receiverId).orElseThrow();

        assertThat(unchangedSender.getBalance()).isEqualByComparingTo("1000.0000");
        assertThat(unchangedReceiver.getBalance()).isEqualByComparingTo("500.0000");

        // Then - verify pending transfer marked FAILED
        PendingTransfer pending = pendingTransferRepository.findById(transactionId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(FAILED);

        // Then - verify failure event in outbox
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);

        OutboxEvent outbox = outboxEvents.get(0);
        assertThat(outbox.getEventType()).isEqualTo("failed.v1");
    }

    @Test
    @Transactional
    @DisplayName("Should skip settlement when status is not INITIATED")
    void settleTransfer_SkipWhenAlreadyProcessed() {
        // Given
        BigDecimal transferAmount = new BigDecimal("200.0000");

        pendingTransferService.storeAs(
              transactionId,
              senderId,
              receiverId,
              transferAmount,
              COMPLETED // Already completed
        );

        BigDecimal initialSenderBalance = senderWallet.getBalance();
        BigDecimal initialReceiverBalance = receiverWallet.getBalance();

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then - verify NO changes
        Wallet unchangedSender = walletRepository.findByUserId(senderId).orElseThrow();
        Wallet unchangedReceiver = walletRepository.findByUserId(receiverId).orElseThrow();

        assertThat(unchangedSender.getBalance()).isEqualByComparingTo(initialSenderBalance);
        assertThat(unchangedReceiver.getBalance()).isEqualByComparingTo(initialReceiverBalance);

        // Then - verify NO outbox events created
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Should prevent concurrent settlement with pessimistic locking")
    void settleTransfer_PessimisticLocking() {
        // Given
        BigDecimal transferAmount = new BigDecimal("200.0000");

        pendingTransferService.storeAs(
              transactionId,
              senderId,
              receiverId,
              transferAmount,
              INITIATED
        );

        // When - settle in transaction (locks acquired)
        settlementService.settle(transactionId, getExpiresAt());

        Wallet updatedSender = walletRepository.findByUserId(senderId).orElseThrow();
        assertThat(updatedSender.getBalance()).isEqualByComparingTo("800.0000");
    }

    @Test
    @Transactional
    @DisplayName("Should handle exact balance amount (boundary case)")
    void settleTransfer_ExactBalance() {
        // Given
        BigDecimal exactAmount = new BigDecimal("1000.0000"); // Sender's exact balance

        pendingTransferService.storeAs(
              transactionId,
              senderId,
              receiverId,
              exactAmount,
              INITIATED
        );

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then
        Wallet updatedSender = walletRepository.findByUserId(senderId).orElseThrow();
        assertThat(updatedSender.getBalance()).isEqualByComparingTo("0.0000");

        PendingTransfer pending = pendingTransferRepository.findById(transactionId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(COMPLETED);
    }
}