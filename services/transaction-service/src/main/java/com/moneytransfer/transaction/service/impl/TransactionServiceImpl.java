package com.moneytransfer.transaction.service.impl;

import com.moneytransfer.security.jwt.JwtUtils;
import com.moneytransfer.transaction.dto.request.TransferRequest;
import com.moneytransfer.transaction.dto.response.PaginatedResponse;
import com.moneytransfer.transaction.dto.response.TransactionDetailResponse;
import com.moneytransfer.transaction.dto.response.TransactionResponse;
import com.moneytransfer.transaction.dto.response.TransactionStatsResponse;
import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.exception.ConcurrentUpdateException;
import com.moneytransfer.transaction.exception.InvalidTransactionException;
import com.moneytransfer.transaction.exception.TransactionNotFoundException;
import com.moneytransfer.transaction.mapper.TransactionMapper;
import com.moneytransfer.transaction.model.Transaction;
import com.moneytransfer.transaction.repository.TransactionRepository;
import com.moneytransfer.transaction.service.ISseEmitterService;
import com.moneytransfer.transaction.service.ITransactionOutboxService;
import com.moneytransfer.transaction.service.ITransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static com.moneytransfer.transaction.enums.TransactionStatus.COMPLETED;
import static com.moneytransfer.transaction.enums.TransactionStatus.PENDING;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements ITransactionService {

    private final TransactionRepository transactionRepository;
    private final ITransactionOutboxService outboxService;
    private final TransactionMapper transactionMapper;
    private final ISseEmitterService sseService;

    private void handleInvalidUpdate(UUID transactionId, TransactionStatus newStatus) {
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

    // Writes transaction (PENDING) + outbox_event ATOMICALLY.
    // If either write fails, both are rolled back.
    // Debezium watches the outbox table via WAL and publishes
    @Transactional
    @Override
    public TransactionResponse initiateTransfer(TransferRequest request) {
        UUID senderId = JwtUtils.extractUserId();

        if (senderId.equals(request.receiverId()))
            throw new InvalidTransactionException();

        log.info(
              "Initiating transfer — sender: {}, receiver: {}, amount: {}",
              senderId,
              request.receiverId(),
              request.amount()
        );

        Transaction transaction = transactionMapper.fromTransferRequest(request)
              .setSenderId(senderId)
              .setStatus(PENDING);

        Transaction savedTransaction = transactionRepository.save(transaction);

        // Delegates proto building + serialization + outbox persistence
        // Runs in the same @Transactional
        outboxService.publishTransferInitiated(savedTransaction);

        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    @Override
    public PaginatedResponse<TransactionResponse> getTransfers(int page,
                                                               int size,
                                                               TransactionStatus status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        UUID userId = JwtUtils.extractUserId();
        Page<Transaction> transactionPage;

        if (status != null) {
            transactionPage = status.equals(COMPLETED) ?
                  // Show both sent and received when filtering by COMPLETED
                  transactionRepository.findCompletedByUserInvolvement(userId, pageable) :
                  // For any other status (PENDING, FAILED, etc.), only show sent transactions
                  transactionRepository.findBySenderAndStatus(userId, status, pageable);
        } else
            transactionPage = transactionRepository.findByUserInvolvement(userId, pageable);

        return PaginatedResponse.fromPage(
              transactionPage.map(TransactionResponse::from)
        );
    }

    @Transactional(readOnly = true)
    @Override
    public TransactionDetailResponse getTransfer(UUID id) {
        UUID userId = JwtUtils.extractUserId();

        return transactionRepository.findAccessibleTransaction(id, userId)
              .map(TransactionDetailResponse::from)
              .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    @Override
    public TransactionStatsResponse getStats() {
        // Get transfer statistics for current user
        return transactionRepository.getStatsByUserId(JwtUtils.extractUserId(), COMPLETED);
    }

    // ─── Status update — called by Kafka consumers ───────────────
    @Transactional
    @Override
    public void updateStatus(UUID transactionId,
                             UUID senderId,
                             UUID receiverId,
                             String reason,
                             TransactionStatus newStatus) {
        // Atomic update
        int updatedRows = transactionRepository.atomicStatusUpdate(newStatus, transactionId, reason, PENDING);

        if (updatedRows == 0) {
            // If update failed, check the current state
            handleInvalidUpdate(transactionId, newStatus);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                String id = transactionId.toString();
                // Always notify the sender
                sseService.pushToEmitter(id, senderId, "transfer-sent-update");

                // Notify receiver only if money actually landed in their account
                if (newStatus == TransactionStatus.COMPLETED)
                    sseService.pushToEmitter(id, receiverId, "transfer-received");
            }
        });

        log.info(
              "Transaction {} successfully updated to {}",
              transactionId,
              newStatus
        );
    }
}