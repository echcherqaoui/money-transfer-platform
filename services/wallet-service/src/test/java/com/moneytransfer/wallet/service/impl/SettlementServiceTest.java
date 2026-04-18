package com.moneytransfer.wallet.service.impl;

import com.google.protobuf.Timestamp;
import com.moneytransfer.wallet.dto.BalanceUpdateEvent;
import com.moneytransfer.wallet.enums.WalletRejectionCode;
import com.moneytransfer.wallet.model.PendingTransfer;
import com.moneytransfer.wallet.model.Wallet;
import com.moneytransfer.wallet.repository.WalletRepository;
import com.moneytransfer.wallet.service.ISseEmitterService;
import com.moneytransfer.wallet.service.IWalletOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.moneytransfer.wallet.enums.PendingStatus.COMPLETED;
import static com.moneytransfer.wallet.enums.PendingStatus.DISCARDED;
import static com.moneytransfer.wallet.enums.PendingStatus.FAILED;
import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService Unit Tests")
class SettlementServiceTest {
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private PendingTransferService pendingTransferService;
    @Mock
    private IWalletOutboxService walletOutboxService;
    @Mock
    private ISseEmitterService sseEmitterService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private SettlementService settlementService;

    private UUID transactionId;
    private UUID senderId;
    private UUID receiverId;
    private PendingTransfer pendingTransfer;
    private Wallet senderWallet;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();

        pendingTransfer = new PendingTransfer()
              .setTransactionId(transactionId)
              .setSenderId(senderId)
              .setReceiverId(receiverId)
              .setAmount(new BigDecimal("100.0000"))
              .setStatus(INITIATED);

        senderWallet = new Wallet()
              .setId(UUID.randomUUID())
              .setUserId(senderId)
              .setBalance(new BigDecimal("500.0000"));

        receiverWallet = new Wallet()
              .setId(UUID.randomUUID())
              .setUserId(receiverId)
              .setBalance(new BigDecimal("200.0000"));
    }

    private Timestamp getExpiresAt(){
        Instant instant = Instant.now().plus(Duration.ofMinutes(10));
        return Timestamp.newBuilder()
              .setSeconds(instant.getEpochSecond())
              .build();
    }

    @Test
    @DisplayName("Should settle transfer successfully - happy path")
    void settle_Success() {
        // Given
        when(pendingTransferService.getPendingTransfer(transactionId))
              .thenReturn(pendingTransfer);
        when(walletRepository.findAllByUserIdInForUpdate(anyList()))
              .thenReturn(List.of(senderWallet, receiverWallet));

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then
        assertThat(senderWallet.getBalance())
              .isEqualByComparingTo("400.0000");
        assertThat(receiverWallet.getBalance())
              .isEqualByComparingTo("300.0000");

        verify(walletRepository).saveAll(anyList());
        verify(pendingTransferService).updateStatus(pendingTransfer, COMPLETED);
        verify(walletOutboxService).publishTransferCompleted(
              transactionId,
              senderId,
              receiverId,
              1_000_000L,
              4_000_000L,
              3_000_000L
        );
        // Then
        verify(eventPublisher).publishEvent(new BalanceUpdateEvent(senderId, new BigDecimal("400.0000")));
        verify(eventPublisher).publishEvent(new BalanceUpdateEvent(receiverId, new BigDecimal("300.0000")));
    }

    @Test
    @DisplayName("Should skip settlement when status is not INITIATED")
    void settle_SkipWhenNotInitiated() {
        // Given
        pendingTransfer.setStatus(COMPLETED);
        when(pendingTransferService.getPendingTransfer(transactionId))
              .thenReturn(pendingTransfer);

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then
        verify(walletRepository, never()).findAllByUserIdInForUpdate(anyList());
        verify(walletRepository, never()).saveAll(anyList());
        verify(walletOutboxService, never()).publishTransferCompleted(
              any(),
              any(),
              any(),
              anyLong(),
              anyLong(),
              anyLong()
        );
    }

    @Test
    @DisplayName("Should skip settlement when status is DISCARDED")
    void settle_SkipWhenDiscarded() {
        // Given
        pendingTransfer.setStatus(DISCARDED);
        when(pendingTransferService.getPendingTransfer(transactionId))
              .thenReturn(pendingTransfer);

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then
        verify(walletRepository, never()).findAllByUserIdInForUpdate(anyList());
        verifyNoInteractions(walletOutboxService);
    }

    @Test
    @DisplayName("Should handle insufficient funds - mark FAILED and publish event")
    void settle_InsufficientFunds() {
        // Given
        senderWallet.setBalance(new BigDecimal("50.0000")); // Less than required

        when(pendingTransferService.getPendingTransfer(transactionId))
              .thenReturn(pendingTransfer);

        when(walletRepository.findAllByUserIdInForUpdate(anyList()))
              .thenReturn(List.of(senderWallet, receiverWallet));

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then
        verify(pendingTransferService).updateStatus(pendingTransfer, FAILED);

        // Then - verify failure event published using Enum description
        verify(walletOutboxService).publishTransferFailed(
              transactionId,
              senderId,
              WalletRejectionCode.INSUFFICIENT_FUNDS.getDescription()
        );


        verify(walletRepository, never()).saveAll(anyList());
        verify(walletOutboxService, never()).publishTransferCompleted(
              any(),
              any(),
              any(),
              anyLong(),
              anyLong(),
              anyLong()
        );
    }

    @Test
    @DisplayName("Should mark FAILED and publish event when sender wallet is missing")
    void settle_SenderWalletNotFound() {
        // Given
        when(pendingTransferService.getPendingTransfer(transactionId))
              .thenReturn(pendingTransfer);

        // Mocking that only the receiver wallet was found
        when(walletRepository.findAllByUserIdInForUpdate(anyList()))
              .thenReturn(List.of(receiverWallet));

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then
        verify(pendingTransferService).updateStatus(pendingTransfer, FAILED);

        verify(walletOutboxService).publishTransferFailed(
              transactionId,
              senderId,
              WalletRejectionCode.WALLET_NOT_FOUND.getDescription()
        );

        verify(walletRepository, never()).saveAll(anyList());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Should mark FAILED and publish event when receiver wallet is missing")
    void settle_ReceiverWalletNotFound() {
        // Given
        when(pendingTransferService.getPendingTransfer(transactionId))
              .thenReturn(pendingTransfer);

        when(walletRepository.findAllByUserIdInForUpdate(anyList()))
              .thenReturn(List.of(senderWallet));

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then - verify status update to FAILED instead of throwing exception
        verify(pendingTransferService).updateStatus(pendingTransfer, FAILED);

        verify(walletOutboxService).publishTransferFailed(
              transactionId,
              senderId,
              WalletRejectionCode.WALLET_NOT_FOUND.getDescription()
        );

        // Verify no financial changes occurred
        verify(walletRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Should mark FAILED and publish event when both wallets are missing")
    void settle_BothWalletsMissing() {
        // Given
        when(pendingTransferService.getPendingTransfer(transactionId))
              .thenReturn(pendingTransfer);

        when(walletRepository.findAllByUserIdInForUpdate(anyList()))
              .thenReturn(List.of());

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then
        verify(pendingTransferService).updateStatus(pendingTransfer, FAILED);

        verify(walletOutboxService).publishTransferFailed(
              transactionId,
              senderId,
              WalletRejectionCode.WALLET_NOT_FOUND.getDescription()
        );

        verify(walletRepository, never()).saveAll(anyList());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Should acquire pessimistic write locks in consistent order")
    void settle_ConsistentLockOrdering() {
        // Given
        UUID highSender = UUID.fromString("00000000-0000-0000-0000-ffffffffffff");
        UUID lowReceiver = UUID.fromString("00000000-0000-0000-0000-000000000000");

        pendingTransfer.setSenderId(highSender)
              .setReceiverId(lowReceiver);

        Wallet highWallet = new Wallet()
              .setUserId(highSender)
              .setBalance(new BigDecimal("500.0000"));

        Wallet lowWallet = new Wallet()
              .setUserId(lowReceiver)
              .setBalance(new BigDecimal("200.0000"));

        when(pendingTransferService.getPendingTransfer(transactionId))
              .thenReturn(pendingTransfer);

        when(walletRepository.findAllByUserIdInForUpdate(anyList()))
              .thenReturn(List.of(lowWallet, highWallet));

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(walletRepository).findAllByUserIdInForUpdate(captor.capture());

        List<UUID> lockedIds = captor.getValue();
        assertThat(lockedIds).hasSize(2);
        // Explicitly verify the low UUID comes before the high UUID
        assertThat(lockedIds.get(0)).isEqualTo(lowReceiver);
        assertThat(lockedIds.get(1)).isEqualTo(highSender);
        assertThat(lockedIds.get(0)).isLessThan(lockedIds.get(1));
    }

    @Test
    @DisplayName("Should handle exact balance amount (boundary case)")
    void settle_ExactBalance() {
        // Given
        senderWallet.setBalance(new BigDecimal("100.0000")); // Exact amount

        when(pendingTransferService.getPendingTransfer(transactionId))
              .thenReturn(pendingTransfer);

        when(walletRepository.findAllByUserIdInForUpdate(anyList()))
              .thenReturn(List.of(senderWallet, receiverWallet));

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then
        assertThat(senderWallet.getBalance()).isEqualByComparingTo("0.0000");

        verify(walletOutboxService).publishTransferCompleted(
              any(),
              any(),
              any(),
              anyLong(),
              anyLong(),
              anyLong()
        );
    }

    @Test
    @DisplayName("Should handle balance just below required amount")
    void settle_BalanceJustBelowRequired() {
        // Given
        senderWallet.setBalance(new BigDecimal("99.9999")); // Just below

        when(pendingTransferService.getPendingTransfer(transactionId))
              .thenReturn(pendingTransfer);

        when(walletRepository.findAllByUserIdInForUpdate(anyList()))
              .thenReturn(List.of(senderWallet, receiverWallet));

        // When
        settlementService.settle(transactionId, getExpiresAt());

        // Then
        verify(pendingTransferService).updateStatus(pendingTransfer, FAILED);
        verify(walletOutboxService).publishTransferFailed(
              eq(transactionId),
              eq(senderId),
              any()
        );
    }
}