package com.moneytransfer.transaction.service.impl;

import com.moneytransfer.security.jwt.JwtUtils;
import com.moneytransfer.transaction.dto.TransferRequest;
import com.moneytransfer.transaction.dto.TransferResponse;
import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.exception.ConcurrentUpdateException;
import com.moneytransfer.transaction.exception.TransactionNotFoundException;
import com.moneytransfer.transaction.mapper.TransactionMapper;
import com.moneytransfer.transaction.model.Transaction;
import com.moneytransfer.transaction.repository.TransactionRepository;
import com.moneytransfer.transaction.service.ITransactionOutboxService;
import com.moneytransfer.transaction.service.ITransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.moneytransfer.transaction.enums.TransactionStatus.PENDING;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements ITransactionService {

    private final TransactionRepository transactionRepository;
    private final ITransactionOutboxService outboxService;
    private final TransactionMapper transactionMapper;

    // Writes transaction (PENDING) + outbox_event ATOMICALLY.
    // If either write fails, both are rolled back.
    // Debezium watches the outbox table via WAL and publishes
    @Transactional
    @Override
    public TransferResponse initiateTransfer(TransferRequest request) {
        UUID senderId = JwtUtils.extractUserId();

        log.info(
              "Initiating transfer — sender: {}, receiver: {}, amount: {}",
              senderId,
              request.receiverId(),
              request.amount()
        );

        Transaction transaction = transactionMapper.fromTransferRequest(request)
              .setSenderId(senderId)
              .setStatus(PENDING);

        transactionRepository.save(transaction);

        // Delegates proto building + serialization + outbox persistence
        // Runs in the same @Transactional
        outboxService.publishTransferInitiated(transaction);

        return TransferResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    @Override
    public TransferResponse getTransfer(UUID id) {
        return transactionRepository.findById(id)
              .map(TransferResponse::from)
              .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    // ─── Status update — called by Kafka consumers ───────────────
    @Transactional
    @Override
    public void updateStatus(UUID transactionId, TransactionStatus newStatus) {
        // Atomic update
        int updatedRows = transactionRepository.atomicStatusUpdate(newStatus, transactionId, PENDING);

        if (updatedRows == 0) {
            // If update failed, check the current state
            Transaction current = transactionRepository.findById(transactionId)
                  .orElseThrow(() -> new TransactionNotFoundException(transactionId));

            if (current.getStatus() == newStatus) {
                //  The work is already done.
                log.info(
                      "Transaction {} is already in status {}. Skipping.",
                      transactionId,
                      newStatus
                );
                return;
            }

            // The transaction is in a state that cannot transition to newStatus
            log.error(
                  "Invalid state transition for transaction {}. Current: {}, Target: {}",
                  transactionId,
                  current.getStatus(),
                  newStatus
            );

            throw new ConcurrentUpdateException(transactionId);
        }

        log.info(
              "Transaction {} successfully updated to {}",
              transactionId,
              newStatus
        );
    }
}