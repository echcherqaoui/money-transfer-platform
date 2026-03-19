package com.moneytransfer.transaction.service;

import com.moneytransfer.security.jwt.JwtUtils;
import com.moneytransfer.transaction.dto.TransferRequest;
import com.moneytransfer.transaction.dto.TransferResponse;
import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.exception.TransactionNotFoundException;
import com.moneytransfer.transaction.mapper.TransactionMapper;
import com.moneytransfer.transaction.model.Transaction;
import com.moneytransfer.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.moneytransfer.transaction.enums.TransactionStatus.PENDING;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionOutboxService outboxService;
    private final TransactionMapper transactionMapper;

    // Writes transaction (PENDING) + outbox_event ATOMICALLY.
    // If either write fails, both are rolled back.
    // Debezium watches the outbox table via WAL and publishes
    @Transactional
    public TransferResponse initiateTransfer(TransferRequest request) {
        UUID senderId = JwtUtils.extractUserId();

        log.info(
              "Initiating transfer — sender: {}, receiver: {}, amount: {} minor units",
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
    public TransferResponse getTransfer(UUID id) {
        return transactionRepository.findById(id)
              .map(TransferResponse::from)
              .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    // ─── Status update — called by Kafka consumers ───────────────
    @Transactional
    public void updateStatus(UUID transactionId,
                             TransactionStatus newStatus) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        log.info("Updating transaction {} status: {} → {}",
                transactionId, transaction.getStatus(), newStatus);

        transaction.setStatus(newStatus);
        transactionRepository.save(transaction);
    }
}