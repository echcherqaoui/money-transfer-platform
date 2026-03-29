package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.wallet.exception.WalletException;
import com.moneytransfer.wallet.model.PendingTransfer;
import com.moneytransfer.wallet.model.Wallet;
import com.moneytransfer.wallet.repository.WalletRepository;
import com.moneytransfer.wallet.service.ISettlementService;
import com.moneytransfer.wallet.service.ISseEmitterService;
import com.moneytransfer.wallet.service.IWalletOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.moneytransfer.wallet.enums.PendingStatus.COMPLETED;
import static com.moneytransfer.wallet.enums.PendingStatus.FAILED;
import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;
import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.WALLET_NOT_FOUND;
import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.WALLET_PARTICIPANTS_MISSING;
import static java.math.RoundingMode.HALF_UP;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService implements ISettlementService {

    private final WalletRepository walletRepository;
    private final PendingTransferService pendingTransferService;
    private final IWalletOutboxService walletOutboxService;
    private final ISseEmitterService sseEmitterService;

    // ── Private helpers ───────────────────────────────────────────
    private void handleInsufficientFunds(PendingTransfer pending,
                                         Wallet senderWallet,
                                         BigDecimal required) {
        log.warn(
              "[FAILED] transaction={} reason=insufficient_funds sender={} available={} required={}",
              pending.getTransactionId(),
              pending.getSenderId(),
              senderWallet.getBalance(),
              required
        );

        pendingTransferService.updateStatus(pending, FAILED);

        String reason = String.format(
              "Insufficient funds: available=%s, required=%s",
              senderWallet.getBalance(),
              required
        );

        // Outbox write — same transaction
        walletOutboxService.publishTransferFailed(
              pending.getTransactionId(),
              pending.getSenderId(),
              reason
        );
    }

    private Wallet findWallet(List<Wallet> wallets, UUID userId) {
        return wallets.stream()
              .filter(w -> w.getUserId().equals(userId))
              .findAny()
              .orElseThrow(() -> new WalletException(WALLET_NOT_FOUND, userId));
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.setScale(4, HALF_UP)
              .movePointRight(4)
              .longValueExact();
    }

    /**
     * Settles an approved transfer atomically:
     * - Pessimistic write lock on both wallets (consistent ordering prevents deadlocks)
     * - Debit sender, credit receiver
     * - Write outbox event in the same transaction (Debezium picks it up via WAL)
     * - Push SSE balance updates after commit
     */
    @Transactional
    @Override
    public void settle(UUID transactionId) {
        PendingTransfer pending = pendingTransferService.getPendingTransfer(transactionId);

        if (pending.getStatus() != INITIATED) {
            log.info(
                  "Transfer {} already in state {}, skipping settlement",
                  transactionId,
                  pending.getStatus()
            );
            return;
        }

        UUID senderId = pending.getSenderId();
        UUID receiverId = pending.getReceiverId();

        // Consistent lock ordering — always lock lower UUID first to prevent deadlocks
        List<UUID> userIds = Stream.of(senderId, receiverId).sorted().toList();
        List<Wallet> wallets = walletRepository.findAllByUserIdInForUpdate(userIds);

        if (wallets.size() != 2)
            throw new WalletException(WALLET_PARTICIPANTS_MISSING, userIds);

        Wallet senderWallet = findWallet(wallets, senderId);
        Wallet receiverWallet = findWallet(wallets, receiverId);

        BigDecimal amount = pending.getAmount();

        if (senderWallet.getBalance().compareTo(amount) < 0) {
            handleInsufficientFunds(pending, senderWallet, amount);
            return;
        }

        senderWallet.debit(amount);
        receiverWallet.credit(amount);

        walletRepository.saveAll(wallets);

        pendingTransferService.updateStatus(pending, COMPLETED);

        // Outbox write — same transaction, Debezium publishes via WAL
        walletOutboxService.publishTransferCompleted(
              transactionId,
              senderId,
              receiverId,
              toMinorUnits(amount),
              toMinorUnits(senderWallet.getBalance()),
              toMinorUnits(receiverWallet.getBalance())
        );

        // SSE push — best effort, outside transaction concern
        sseEmitterService.pushBalanceUpdate(senderId, senderWallet.getBalance());
        sseEmitterService.pushBalanceUpdate(receiverId, receiverWallet.getBalance());

        log.info(
              "[COMPLETED] transaction={} sender={} receiver={} amount={}",
              transactionId,
              senderId,
              receiverId,
              amount
        );
    }
}