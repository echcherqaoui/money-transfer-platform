package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.wallet.enums.PendingStatus;
import com.moneytransfer.wallet.exception.WalletException;
import com.moneytransfer.wallet.model.PendingTransfer;
import com.moneytransfer.wallet.repository.PendingTransferRepository;
import com.moneytransfer.wallet.service.IPendingTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;
import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.PENDING_TRANSFER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingTransferService implements IPendingTransferService {
    private final PendingTransferRepository pendingTransferRepository;

    @Transactional
    @Override
    public void store(UUID transactionId,
                      UUID senderId,
                      UUID receiverId,
                      long amountMinorUnits) {

        BigDecimal amount = BigDecimal.valueOf(amountMinorUnits).movePointLeft(4);

        pendingTransferRepository.save(
              new PendingTransfer()
                    .setTransactionId(transactionId)
                    .setSenderId(senderId)
                    .setReceiverId(receiverId)
                    .setAmount(amount)
        );

        log.info(
              "[PENDING] transaction={} sender={} amount={}",
              transactionId,
              senderId,
              amount
        );
    }

    @Override
    public PendingTransfer getPendingTransfer(UUID transactionId) {
        return pendingTransferRepository.findById(transactionId)
              .orElseThrow(() -> new WalletException(PENDING_TRANSFER_NOT_FOUND, transactionId));
    }

    @Override
    public void updateStatus(PendingTransfer pending,
                             PendingStatus status) {
        pendingTransferRepository.save(
              pending.setStatus(status)
        );
    }

    @Override
    public int atomicStatusUpdate(UUID transactionId, PendingStatus newStatus) {
        return pendingTransferRepository.atomicStatusUpdate(
              newStatus,
              transactionId,
              INITIATED
        );
    }
}