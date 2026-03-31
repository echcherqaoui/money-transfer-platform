package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.wallet.enums.PendingStatus;
import com.moneytransfer.wallet.exception.WalletException;
import com.moneytransfer.wallet.model.PendingTransfer;
import com.moneytransfer.wallet.repository.PendingTransferRepository;
import com.moneytransfer.wallet.service.IPendingTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;
import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.PENDING_TRANSFER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class PendingTransferService implements IPendingTransferService {
    private final PendingTransferRepository pendingTransferRepository;

    @Transactional
    @Override
    public void storeAs(UUID transactionId,
                        UUID senderId,
                        UUID receiverId,
                        BigDecimal amount,
                        PendingStatus status) {

        pendingTransferRepository.save(
              new PendingTransfer()
                    .setTransactionId(transactionId)
                    .setSenderId(senderId)
                    .setReceiverId(receiverId)
                    .setAmount(amount)
                    .setStatus(status)
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